# LunaCode MCP 客户端工具接入 Plan

## 架构概览

本阶段在现有工具系统旁边新增一条 MCP 接入链路，但最终仍汇入 LunaCode 的 `ToolRegistry`、`ToolExecutor`、`AgentToolRunner` 和权限系统。整体分为八个部分：配置加载、传输抽象、JSON-RPC 客户端、MCP 会话、MCP 工具包装、延迟工具发现、Prompt 注入、应用启动与关闭。

配置加载层负责从用户级 `~/.lunacode/config.yaml` 和项目级当前启动配置读取 `mcp.servers`，只对 MCP Server map 做两层合并。Provider、权限、沙箱等现有必填配置仍按当前项目启动配置校验。合并后的 MCP 配置会完成传输类型判定、嵌入式环境变量展开、敏感值登记和中文警告收集。

传输层先抽象为统一的 `McpTransport`，stdio 和 Streamable HTTP 都实现同一套收发语义。上层 JSON-RPC 客户端只看“发送 JSON 消息、接收 JSON 消息、关闭连接、收到诊断信息”这几个行为，不关心消息来自子进程管道还是 HTTP/SSE。这样同一套 MCP session 可以同时管理本地 stdio Server 和远程 Streamable HTTP Server。

JSON-RPC 客户端负责生成请求 id、维护 pending 请求表、按 id 完成乱序响应、处理超时、忽略通知，并对 Server 发来的不支持请求返回标准 JSON-RPC 错误。它不包含 MCP 业务方法，只提供 `request` 和 `notify` 能力。

MCP 会话层负责协议生命周期：`initialize`、`notifications/initialized`、`tools/list` 分页拉取和 `tools/call`。会话持有一个 transport 和一个 JSON-RPC 客户端，并对外暴露“发现工具”和“调用工具”两个高层行为。会话状态分为 connecting、ready、failed、closed，失败只影响对应 Server。

MCP 工具包装层把 `tools/list` 返回的工具定义转换为 LunaCode 的 `Tool`。公开工具名采用 `mcp_{serverName}_{toolName}`，合法化后冲突则追加稳定短哈希。包装工具默认不可只读、默认有副作用、`shouldDefer()` 固定返回 true；真正执行时再通过会话发送 `tools/call`。由于 AgentToolRunner 会先走权限网关，权限拒绝时 wrapper 不会执行，远端也不会收到调用。

延迟工具发现通过扩展 `ToolRegistry` 和新增 `ToolSearch` 完成。MCP 工具注册进 Registry 后默认处于“延迟且未发现”状态，不进入模型普通工具 schema 列表；Prompt 的 system-reminder 只列出这些工具的公开名和简短说明。模型需要某个工具时先调用 `ToolSearch`，ToolSearch 从本地 Registry 返回完整 schema 并标记该工具已发现。从下一轮开始，Registry 才把该 MCP 工具完整 schema 放进普通工具列表。

Prompt 注入层复用现有 system-reminder 机制和 `MCP_HINT` 类型。每轮 Agent Loop 构建 PromptBundle 时，从 Registry 获取“可见工具 schema”和“延迟工具摘要”两个结果：前者传给 provider 的工具声明，后者渲染成中文 system-reminder。Provider 适配层不感知 MCP，也不感知延迟加载规则。

应用启动层在注册内置工具后注册 `ToolSearch`，再按并发发现流程连接所有 MCP Server。发现成功的远端工具注册为延迟工具；失败的 Server 生成中文警告并继续启动。应用退出时统一关闭 MCP manager，stdio 子进程和 HTTP 会话都从 manager 的生命周期里释放。

## 核心数据结构

### McpConfig

```java
public record McpConfig(Map<String, McpServerConfig> servers) {
    public static McpConfig empty();
}
```

`servers` 使用合并后的 Server 名到配置映射，保持插入顺序：用户级先进入，项目级覆盖同名项并保留最终结果。`empty()` 用于未配置 MCP 时保持启动路径简单。

### McpServerConfig

```java
public sealed interface McpServerConfig permits McpStdioServerConfig, McpHttpServerConfig {
    String name();
    McpTransportKind kind();
    Map<String, String> sensitiveValues();
}

public record McpStdioServerConfig(
        String name,
        String command,
        List<String> args,
        Map<String, String> env
) implements McpServerConfig {}

public record McpHttpServerConfig(
        String name,
        URI url,
        Map<String, String> headers
) implements McpServerConfig {}
```

`sensitiveValues()` 返回展开后的 env/header/url 中需要遮蔽的值，启动时加入 `SensitiveValueMasker`。`McpTransportKind` 只有 `STDIO` 和 `STREAMABLE_HTTP`。

### McpTransport

```java
public interface McpTransport extends AutoCloseable {
    String serverName();

    CompletableFuture<Void> start(McpTransportListener listener);

    CompletableFuture<Void> send(JsonNode message);

    CompletableFuture<Void> closeAsync();

    @Override
    default void close();
}

public interface McpTransportListener {
    void onMessage(JsonNode message);

    void onClosed(Throwable cause);

    void onDiagnostic(String message);
}
```

Transport 只负责消息边界和连接生命周期。stdio 实现使用进程 stdin/stdout/stderr；HTTP 实现使用 `HttpClient`、普通 JSON 响应和 SSE 消息流。上层不直接接触 Process、InputStream、HttpRequest 或 SSE parser。

### JsonRpcClient

```java
public final class JsonRpcClient implements McpTransportListener, AutoCloseable {
    public CompletableFuture<JsonNode> request(String method, ObjectNode params, Duration timeout);

    public CompletableFuture<Void> notify(String method, JsonNode params);

    public void onMessage(JsonNode message);

    public void onClosed(Throwable cause);

    public CompletableFuture<Void> closeAsync();
}
```

内部维护 `ConcurrentHashMap<String, PendingRequest>`。外发请求生成单调递增字符串 id；响应按 id 完成对应 future；未知 id、重复 id、缺失 id 响应只记录诊断，不完成其他请求。收到 Server 请求时，如果本阶段不支持该方法，客户端用相同 id 回写 JSON-RPC `Method not found` 错误。

### McpSession

```java
public final class McpSession implements AutoCloseable {
    public CompletableFuture<McpInitializeResult> initialize(Duration timeout);

    public CompletableFuture<List<McpToolDefinition>> listTools(Duration timeout);

    public CompletableFuture<McpToolCallResult> callTool(String originalToolName, JsonNode arguments, Duration timeout);

    public McpServerStatus status();

    public CompletableFuture<Void> closeAsync();
}
```

`McpSession` 固定声明协议版本 `2025-06-18`。初始化成功后才允许 `listTools` 或 `callTool`。如果 transport 断开，session 状态变为 failed/closed，未完成请求由 JSON-RPC 层失败完成。

### McpToolDefinition

```java
public record McpToolDefinition(
        String serverName,
        String originalName,
        String publicName,
        String description,
        ObjectNode inputSchema
) {}
```

`originalName` 是 MCP Server 返回的工具名，`publicName` 是 LunaCode 对模型公开的名字。`inputSchema` 必须是 object schema；缺失时使用保守空对象 schema，无法作为对象使用时跳过该工具并记录警告。

### McpToolWrapper

```java
public final class McpToolWrapper implements Tool {
    public String name();

    public String description();

    public JsonNode inputSchema();

    public ToolResult execute(ToolExecutionContext context, JsonNode input);

    public boolean isReadOnly();       // false

    public boolean isDestructive();    // true

    public boolean isConcurrencySafe(JsonNode input); // false

    public String category();          // "mcp"

    public ValidationError validateInput(JsonNode input);

    public boolean shouldDefer();      // true
}
```

`execute` 只做参数校验、调用 `McpSession.callTool`、转换返回结果。权限判断不在 wrapper 内重复实现，而是复用 AgentToolRunner 现有执行路径。wrapper 默认有副作用，保证权限模式和规则能先拦住远端调用。

### Tool 延迟可见性

```java
public interface Tool {
    // 现有方法保留
    default boolean shouldDefer() {
        return false;
    }
}

public record DeferredToolSummary(String name, String description, String category) {}

public record ToolDefinitionSnapshot(String name, String description, JsonNode inputSchema) {}

public record ToolDeclarationSet(ArrayNode visibleTools, List<DeferredToolSummary> deferredTools) {}
```

内置工具默认不延迟。MCP wrapper 固定延迟。`ToolDeclarationSet` 是每轮 Prompt 构建的输出：`visibleTools` 给 provider，`deferredTools` 给 system-reminder。

### ToolRegistry 扩展

```java
public interface ToolRegistry {
    void register(Tool tool);

    Optional<Tool> get(String name);

    Optional<Tool> getRegistered(String name);

    Optional<ToolDefinitionSnapshot> discoverDeferredTool(String name);

    boolean isDeferredDiscovered(String name);

    List<DeferredToolSummary> deferredToolSummaries();

    ToolDeclarationSet declarationsForModel(AgentMode mode);
}
```

`get(name)` 只返回可执行工具：内置工具、非延迟工具、已被 ToolSearch 发现的延迟工具。未发现的延迟工具通过 `getRegistered` 和 `discoverDeferredTool` 供 ToolSearch 使用，但不会被普通执行路径直接拿到。

### ToolSearchTool

```java
public final class ToolSearchTool implements Tool {
    public String name();              // "ToolSearch"

    public JsonNode inputSchema();     // { "type": "object", "required": ["name"] }

    public ToolResult execute(ToolExecutionContext context, JsonNode input);

    public boolean isReadOnly();       // true

    public boolean isDestructive();    // false
}
```

ToolSearch 只查本地 Registry 元数据。命中延迟工具时返回完整定义并标记 discovered；找不到时返回普通工具错误；不会启动 Server、不会重连、不会发送 `tools/call`。

### McpClientManager

```java
public final class McpClientManager implements AutoCloseable {
    public McpDiscoveryResult discoverAll(McpConfig config, Duration timeout);

    public Optional<McpSession> session(String serverName);

    public CompletableFuture<Void> closeAsync();
}

public record McpDiscoveryResult(
        List<McpToolWrapper> tools,
        List<McpServerStatus> statuses,
        List<String> warnings
) {}
```

Manager 用固定线程池并发发现所有 Server。每个 Server 独立 30 秒超时，失败只生成该 Server 的 warning。成功工具由应用启动层注册进 ToolRegistry。

## 模块设计

### 配置模块

**职责：** 读取项目配置和用户配置，解析 `mcp.servers`，执行项目级覆盖用户级的合并规则，展开 `${VAR}`，生成结构化 MCP 配置和中文警告。

**对外接口：** `ConfigLoader.load(Path projectConfig)` 返回包含 `McpConfig` 的 `ProviderConfig`；`ProviderConfig` 新增 `mcp()` 字段。`ConfigLoader` 内部增加只读用户级配置路径解析，不要求用户级配置包含 provider 必填字段。

**依赖：** Jackson YAML、环境变量 Map、现有 `SensitiveValueMasker` 在应用启动阶段消费敏感值。

### Transport 模块

**职责：** 提供 `McpTransport` 抽象和两个实现：`StdioMcpTransport`、`StreamableHttpMcpTransport`。Transport 只处理消息传输，不解析 MCP 方法，也不维护 JSON-RPC pending 表。

**对外接口：** `start(listener)`、`send(message)`、`closeAsync()`。

**依赖：** stdio 使用 `ProcessBuilder` 和工作区根目录；HTTP 使用 Java `HttpClient`，复用现有 SSE 解析思路，保存握手产生的会话头并在后续请求携带。

### JSON-RPC 模块

**职责：** 封装 JSON-RPC 2.0 请求、通知、响应、错误响应、pending 请求表、超时和乱序配对。

**对外接口：** `request(method, params, timeout)`、`notify(method, params)`。

**依赖：** `McpTransport` 和 Jackson `ObjectMapper`。不依赖 MCP 工具定义或会话状态。

### MCP Session 模块

**职责：** 实现 MCP 协议生命周期和工具能力：初始化、初始化完成通知、分页列工具、工具调用、结果转换入口、状态管理。

**对外接口：** `initialize`、`listTools`、`callTool`、`status`、`closeAsync`。

**依赖：** `JsonRpcClient`、`McpTransport`、`McpContentRenderer`。

### MCP Discovery 模块

**职责：** 根据合并后的 `McpConfig` 创建 transport、session，并发连接 Server，执行初始化和工具发现，把成功的工具转成 `McpToolWrapper`。

**对外接口：** `McpClientManager.discoverAll`。

**依赖：** Transport factory、MCP session、工具名合法化器、敏感值遮蔽器。

### MCP Tool 模块

**职责：** 包装远端工具定义，转换 Tool 输入校验、执行结果和错误结果，保证 MCP 工具在 LunaCode 中表现为普通工具。

**对外接口：** `McpToolWrapper` 和 `McpContentRenderer`。

**依赖：** `McpSession`、`ToolExecutionContext`、`SensitiveValueMasker`。

### Registry 与 ToolSearch 模块

**职责：** 管理延迟工具状态。Registry 存储所有工具，但只把非延迟或已发现延迟工具暴露给 provider；ToolSearch 负责把单个延迟工具从“仅摘要可见”推进到“完整 schema 可见”。

**对外接口：** `ToolRegistry.declarationsForModel`、`discoverDeferredTool`、`deferredToolSummaries`、`ToolSearchTool`。

**依赖：** 现有 `DefaultToolRegistry`、`ToolDescriptionEnhancer`。

### Prompt 模块

**职责：** 每轮构建 Prompt 时，把可见工具 schema 放入 `PromptBundle.toolDeclarations`，把未发现延迟 MCP 工具摘要渲染为 `MCP_HINT` system-reminder。

**对外接口：** `PromptContextBuilder.build(..., ToolDeclarationSet tools)`，`SystemReminderBuilder.build(..., List<DeferredToolSummary>)`。

**依赖：** `SystemReminderKind.MCP_HINT`、现有 ProviderPromptAdapter。Provider 不需要改 MCP 逻辑，只消费 PromptBundle。

### 权限与信任边界模块

**职责：** 确保 MCP 工具调用仍走现有权限网关。MCP wrapper 的公开工具名就是权限规则中的工具名；默认有副作用，默认触发 ask 或被规则处理。

**对外接口：** 复用 `DefaultToolPermissionGateway`、`PermissionTargetExtractor` 和规则语法。若未发现延迟工具被直接调用，Registry 不返回 executable tool，Agent Loop 得到普通 tool_not_found 或 deferred_not_discovered 错误，不会调用远端。

**依赖：** `AgentToolRunner` 的执行顺序：查工具、硬拦截、权限评估、用户确认、再执行工具。

### 应用集成模块

**职责：** 在 `LunaCodeApplication` 中完成 MCP 初始化：加载配置、注册内置工具、注册 ToolSearch、并发发现 MCP Server、注册 MCP wrapper、记录 startup warnings、构造 ToolExecutor 和 Orchestrator。应用退出时关闭 `McpClientManager`。

**对外接口：** 应用启动路径和关闭 hook。

**依赖：** `DefaultToolRegistry`、`McpClientManager`、`SensitiveValueMasker`、`LanternaLunaTui`。

## 模块交互

### 启动发现流程

```text
LunaCodeApplication
  -> ConfigLoader.load(projectConfig)
  -> register built-in tools
  -> register ToolSearch
  -> McpClientManager.discoverAll(config.mcp(), 30s)
      -> McpTransportFactory.create(serverConfig)
      -> McpTransport.start(listener)
      -> JsonRpcClient.request("initialize")
      -> JsonRpcClient.notify("notifications/initialized")
      -> McpSession.listTools()
      -> McpToolNameAllocator.publicName(serverName, toolName)
      -> McpToolWrapper(...)
  -> ToolRegistry.register(wrapper)
  -> collect startup warnings
  -> start TUI
```

任意 Server 失败时，只产生该 Server 的 `McpServerStatus` 和 warning。其他 Server 的 future 继续完成。`discoverAll` 返回后再进入 TUI。

### 每轮工具列表流程

```text
DefaultAgentLoop
  -> ToolRegistry.declarationsForModel(config.mode())
      -> visibleTools: 内置工具 + ToolSearch + 已发现 MCP 工具
      -> deferredTools: 未发现 MCP 工具摘要
  -> PromptContextBuilder.build(..., ToolDeclarationSet)
  -> MessageChannelBuilder/SystemReminderBuilder 添加 MCP_HINT
  -> ProviderPromptAdapter 发送 visibleTools 和 reminders
```

未发现 MCP 工具完整 schema 不进入 provider 工具声明。模型只能从 reminder 中看到名字和摘要。

### ToolSearch 流程

```text
模型调用 ToolSearch({"name":"mcp_github_search_issues"})
  -> AgentToolRunner 权限检查 ToolSearch
  -> ToolExecutor.execute(ToolSearch)
  -> ToolRegistry.discoverDeferredTool(name)
  -> 返回完整 schema + 标记 discovered
  -> Agent Loop 下一轮
  -> ToolRegistry.declarationsForModel 包含该 MCP 工具完整 schema
```

ToolSearch 不接触 MCP transport，不调用远端 Server，不返回密钥、headers、env 或连接参数。

### MCP 工具调用流程

```text
模型调用 mcp_github_search_issues({...})
  -> AgentToolRunner 通过 ToolRegistry.get(publicName) 获取已发现 wrapper
  -> PermissionGateway.evaluate(toolUse, wrapper, config)
  -> allow: ToolExecutor.execute(wrapper)
  -> ask: 用户确认后 ToolExecutor.execute(wrapper)
  -> deny: 直接返回权限拒绝 ToolResult
  -> wrapper.execute
      -> McpSession.callTool(originalName, args, 30s)
      -> JsonRpcClient.request("tools/call", ...)
      -> McpContentRenderer 转普通 ToolResult
  -> Agent Loop 回灌 tool_result
```

权限 deny 或用户拒绝时，流程不会进入 `wrapper.execute`，因此远端不会收到 `tools/call`。

### 关闭流程

```text
TUI/应用退出
  -> McpClientManager.closeAsync()
      -> session.closeAsync()
      -> transport.closeAsync()
      -> stdio 先正常关闭 stdin/进程
      -> 超时后终止仍未结束子进程
      -> HTTP 释放本地连接资源
```

未完成 JSON-RPC 请求统一失败完成，避免线程或 future 悬挂。

## 文件组织

```text
src/main/java/com/lunacode/
├── app/
│   └── LunaCodeApplication.java              # 启动时接入 MCP manager 和 ToolSearch
├── config/
│   ├── ConfigLoader.java                     # 读取用户级/项目级 mcp.servers 并合并
│   ├── ProviderConfig.java                   # 新增 McpConfig 字段
│   ├── McpConfig.java                        # MCP 配置根对象
│   ├── McpServerConfig.java                  # sealed server config
│   ├── McpStdioServerConfig.java             # stdio server 配置
│   ├── McpHttpServerConfig.java              # HTTP server 配置
│   └── EnvironmentValueExpander.java         # 嵌入式 ${VAR} 展开
├── mcp/
│   ├── McpClientManager.java                 # 多 Server 并发发现与生命周期
│   ├── McpDiscoveryResult.java               # 启动发现结果
│   ├── McpServerStatus.java                  # 单 Server 状态和 warning
│   ├── McpSession.java                       # initialize/listTools/callTool
│   ├── McpToolDefinition.java                # 远端工具定义
│   ├── McpToolNameAllocator.java             # mcp_{server}_{tool} + 短哈希
│   ├── McpContentRenderer.java               # MCP 内容转 ToolResult 文本
│   ├── rpc/
│   │   ├── JsonRpcClient.java
│   │   ├── JsonRpcError.java
│   │   └── PendingJsonRpcRequest.java
│   └── transport/
│       ├── McpTransport.java
│       ├── McpTransportListener.java
│       ├── McpTransportFactory.java
│       ├── StdioMcpTransport.java
│       └── StreamableHttpMcpTransport.java
├── tool/
│   ├── Tool.java                             # 新增 default shouldDefer()
│   ├── ToolRegistry.java                     # 延迟工具和 declarationsForModel
│   ├── DefaultToolRegistry.java              # discovered 状态管理
│   ├── ToolDeclarationSet.java               # 可见 schema + 延迟摘要
│   ├── DeferredToolSummary.java
│   ├── ToolDefinitionSnapshot.java
│   ├── ToolSearchTool.java                   # 延迟工具完整定义检索
│   └── McpToolWrapper.java                   # MCP Tool 适配 Tool
├── prompt/
│   ├── PromptContextBuilder.java             # 接收 ToolDeclarationSet
│   ├── MessageChannelBuilder.java            # 传递延迟工具摘要
│   ├── SystemReminderBuilder.java            # 生成 MCP_HINT
│   └── DeferredToolReminderPolicy.java       # 延迟工具 reminder 文本
└── ...
```

测试文件按包对应放在 `src/test/java/com/lunacode/...`，重点覆盖配置合并、transport、JSON-RPC 配对、MCP session、ToolSearch、权限拒绝不触发远端调用和 prompt 工具可见性。

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| MCP 配置合并 | 只合并 `mcp.servers`，用户级先、项目级覆盖 | 避免改变现有 provider 必填配置语义，同时满足 MCP 两层配置需求 |
| 传输边界 | 抽象 `McpTransport`，stdio/HTTP 只做实现 | 真实产品会同时接本地和远程 Server，上层协议不能被传输细节污染 |
| JSON-RPC 配对 | 独立 `JsonRpcClient` 管 pending 表 | 复用在两种 transport 上，保证异步乱序响应行为一致 |
| Server 发现 | 启动时并发发现，每个 Server 30 秒超时 | 满足故障隔离，避免多个慢 Server 串行拖慢启动 |
| 工具公开名 | `mcp_{serverName}_{toolName}`，冲突追加短哈希 | 符合用户指定格式，同时保证唯一稳定 |
| MCP 工具权限 | wrapper 默认非只读且有副作用 | MCP Server 在信任边界外，不能因远端描述自动放行 |
| 延迟加载 | Registry 保存完整工具，provider 默认只看摘要 | 避免大量 MCP schema 塞进模型工具列表，按需通过 ToolSearch 激活 |
| ToolSearch 行为 | 只查本地 Registry，不访问 MCP Server | 保持信任边界清晰，避免搜索动作产生远端副作用 |
| 未发现工具直接调用 | Registry 不返回 executable tool | 防止模型根据 reminder 名字绕过 ToolSearch 直接调用 |
| 非文本结果 | 文本直出，JSON 摘要，二进制/图片只摘要 | 本阶段不做资源落盘和富媒体展示，降低实现面 |
| Streamable HTTP | Java HttpClient + JSON/SSE 解析 | 复用现有 Java 标准库和 SSE 解析经验，避免新增依赖 |
| 启动警告 | 收集为中文 warning，并保持应用继续启动 | 满足单 Server 失败不影响内置工具和其他 Server |
