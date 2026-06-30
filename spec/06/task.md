# LunaCode MCP 客户端工具接入 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 修改 | `src/main/java/com/lunacode/config/ProviderConfig.java` | 挂载合并后的 MCP 配置 |
| 修改 | `src/main/java/com/lunacode/config/ConfigLoader.java` | 读取用户级/项目级 `mcp.servers`、合并、展开环境变量 |
| 新建 | `src/main/java/com/lunacode/config/McpConfig.java` | MCP 配置根对象 |
| 新建 | `src/main/java/com/lunacode/config/McpServerConfig.java` | MCP Server 配置抽象 |
| 新建 | `src/main/java/com/lunacode/config/McpTransportKind.java` | MCP transport 类型 |
| 新建 | `src/main/java/com/lunacode/config/McpStdioServerConfig.java` | stdio Server 配置 |
| 新建 | `src/main/java/com/lunacode/config/McpHttpServerConfig.java` | Streamable HTTP Server 配置 |
| 新建 | `src/main/java/com/lunacode/config/EnvironmentValueExpander.java` | 嵌入式 `${VAR}` 展开 |
| 新建 | `src/main/java/com/lunacode/mcp/transport/McpTransport.java` | 统一 MCP transport 接口 |
| 新建 | `src/main/java/com/lunacode/mcp/transport/McpTransportListener.java` | transport 消息监听接口 |
| 新建 | `src/main/java/com/lunacode/mcp/transport/McpTransportFactory.java` | 根据配置创建 transport |
| 新建 | `src/main/java/com/lunacode/mcp/transport/StdioMcpTransport.java` | stdio 子进程 transport |
| 新建 | `src/main/java/com/lunacode/mcp/transport/StreamableHttpMcpTransport.java` | Streamable HTTP transport |
| 新建 | `src/main/java/com/lunacode/mcp/rpc/JsonRpcClient.java` | JSON-RPC 请求/通知/响应配对 |
| 新建 | `src/main/java/com/lunacode/mcp/rpc/JsonRpcError.java` | JSON-RPC 错误结构 |
| 新建 | `src/main/java/com/lunacode/mcp/rpc/PendingJsonRpcRequest.java` | pending 请求状态 |
| 新建 | `src/main/java/com/lunacode/mcp/McpSession.java` | MCP initialize、tools/list、tools/call |
| 新建 | `src/main/java/com/lunacode/mcp/McpInitializeResult.java` | 初始化结果 |
| 新建 | `src/main/java/com/lunacode/mcp/McpToolDefinition.java` | MCP 工具定义 |
| 新建 | `src/main/java/com/lunacode/mcp/McpToolCallResult.java` | MCP 工具调用结果 |
| 新建 | `src/main/java/com/lunacode/mcp/McpServerStatus.java` | Server 状态和失败原因 |
| 新建 | `src/main/java/com/lunacode/mcp/McpDiscoveryResult.java` | 启动发现结果 |
| 新建 | `src/main/java/com/lunacode/mcp/McpClientManager.java` | 多 Server 并发发现和生命周期 |
| 新建 | `src/main/java/com/lunacode/mcp/McpToolNameAllocator.java` | 公开工具名合法化和冲突处理 |
| 新建 | `src/main/java/com/lunacode/mcp/McpContentRenderer.java` | MCP 内容转普通工具结果文本 |
| 修改 | `src/main/java/com/lunacode/tool/Tool.java` | 增加 `shouldDefer()` 默认方法 |
| 修改 | `src/main/java/com/lunacode/tool/ToolRegistry.java` | 增加延迟工具发现接口 |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolRegistry.java` | 管理延迟工具 discovered 状态 |
| 新建 | `src/main/java/com/lunacode/tool/DeferredToolSummary.java` | system-reminder 中的延迟工具摘要 |
| 新建 | `src/main/java/com/lunacode/tool/ToolDefinitionSnapshot.java` | ToolSearch 返回的完整工具定义 |
| 新建 | `src/main/java/com/lunacode/tool/ToolDeclarationSet.java` | 可见工具 schema 与延迟摘要集合 |
| 新建 | `src/main/java/com/lunacode/tool/ToolSearchTool.java` | 延迟工具完整定义检索工具 |
| 新建 | `src/main/java/com/lunacode/tool/McpToolWrapper.java` | MCP 工具适配 LunaCode Tool |
| 修改 | `src/main/java/com/lunacode/prompt/PromptContextBuilder.java` | 使用 ToolDeclarationSet 构建 prompt |
| 修改 | `src/main/java/com/lunacode/prompt/MessageChannelBuilder.java` | 传递延迟工具摘要 |
| 修改 | `src/main/java/com/lunacode/prompt/SystemReminderBuilder.java` | 生成 MCP_HINT reminder |
| 新建 | `src/main/java/com/lunacode/prompt/DeferredToolReminderPolicy.java` | 渲染延迟工具名字列表 |
| 修改 | `src/main/java/com/lunacode/agent/DefaultAgentLoop.java` | 每轮使用 Registry 的可见工具集合 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 启动注册 ToolSearch、发现 MCP Server、关闭 MCP manager |
| 修改 | `config.example.yaml` | 增加 `mcp.servers` 示例 |
| 新建 | `src/test/java/com/lunacode/config/EnvironmentValueExpanderTest.java` | 环境变量展开测试 |
| 修改 | `src/test/java/com/lunacode/config/ConfigLoaderTest.java` | MCP 配置合并和解析测试 |
| 新建 | `src/test/java/com/lunacode/mcp/rpc/JsonRpcClientTest.java` | JSON-RPC 配对测试 |
| 新建 | `src/test/java/com/lunacode/mcp/transport/StdioMcpTransportTest.java` | stdio transport 测试 |
| 新建 | `src/test/java/com/lunacode/mcp/transport/StreamableHttpMcpTransportTest.java` | HTTP transport 测试 |
| 新建 | `src/test/java/com/lunacode/mcp/McpSessionTest.java` | initialize、tools/list、tools/call 测试 |
| 新建 | `src/test/java/com/lunacode/mcp/McpClientManagerTest.java` | 并发发现和失败隔离测试 |
| 新建 | `src/test/java/com/lunacode/mcp/McpToolNameAllocatorTest.java` | 工具名合法化与冲突后缀测试 |
| 新建 | `src/test/java/com/lunacode/mcp/McpContentRendererTest.java` | MCP 内容摘要测试 |
| 修改 | `src/test/java/com/lunacode/tool/ToolRegistryTest.java` | 延迟工具和 discovered 状态测试 |
| 新建 | `src/test/java/com/lunacode/tool/ToolSearchToolTest.java` | ToolSearch 行为测试 |
| 新建 | `src/test/java/com/lunacode/tool/McpToolWrapperTest.java` | wrapper 执行与错误转换测试 |
| 修改 | `src/test/java/com/lunacode/prompt/PromptContextBuilderTest.java` | MCP_HINT reminder 与工具列表测试 |
| 修改 | `src/test/java/com/lunacode/agent/execution/AgentToolRunnerPermissionTest.java` | 权限拒绝不触发远端调用测试 |
| 新建 | `toolTest/mcp-test-server.js` | 端到端测试用 stdio MCP Server |

## T1: 添加 MCP 配置数据结构

**文件：** `src/main/java/com/lunacode/config/McpConfig.java`, `src/main/java/com/lunacode/config/McpServerConfig.java`, `src/main/java/com/lunacode/config/McpTransportKind.java`, `src/main/java/com/lunacode/config/McpStdioServerConfig.java`, `src/main/java/com/lunacode/config/McpHttpServerConfig.java`
**依赖：** 无
**步骤：**
1. 新建 `McpConfig`，包含不可变 `Map<String, McpServerConfig>` 和 `empty()`。
2. 新建 `McpServerConfig` sealed interface，定义 `name()`、`kind()`、`sensitiveValues()`。
3. 新建 stdio、HTTP 两个 record 配置类型。
4. 新建 `McpTransportKind` 枚举，包含 `STDIO` 和 `STREAMABLE_HTTP`。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T2: 实现嵌入式环境变量展开器

**文件：** `src/main/java/com/lunacode/config/EnvironmentValueExpander.java`, `src/test/java/com/lunacode/config/EnvironmentValueExpanderTest.java`
**依赖：** T1
**步骤：**
1. 实现 `${VAR}` 嵌入式展开，支持一个字符串中多个变量。
2. 变量不存在或值为空时返回明确错误。
3. 保持普通字符串原样。
4. 添加测试覆盖 `Bearer ${TOKEN}`、`${HOME}/.cache`、多个变量、缺失变量和空变量。

**验证：** 运行 `mvn -Dtest=EnvironmentValueExpanderTest test`，期望测试通过。

## T3: ProviderConfig 挂载 MCP 配置

**文件：** `src/main/java/com/lunacode/config/ProviderConfig.java`
**依赖：** T1
**步骤：**
1. 给 `ProviderConfig` 增加 `McpConfig mcp` 字段。
2. 兼容现有构造器，未传入时使用 `McpConfig.empty()`。
3. 保持 thinking、agent、permissions、sandbox 默认值逻辑不变。

**验证：** 运行 `mvn -Dtest=ConfigLoaderTest test`，期望现有配置测试仍通过。

## T4: 扩展 ConfigLoader 读取 mcp.servers

**文件：** `src/main/java/com/lunacode/config/ConfigLoader.java`
**依赖：** T1, T2, T3
**步骤：**
1. 在 raw config 中增加 `mcp.servers` 映射。
2. 解析每个 Server 的 `command/args/env` 或 `url/headers`。
3. 校验 `command` 与 `url` 二选一。
4. 对 env、headers、url 执行嵌入式环境变量展开。
5. 单个 Server 配置错误时收集中文 warning 并跳过该 Server。

**验证：** 运行 `mvn -Dtest=ConfigLoaderTest test`，期望现有测试仍通过。

## T5: 实现用户级与项目级 MCP 配置合并

**文件：** `src/main/java/com/lunacode/config/ConfigLoader.java`, `src/test/java/com/lunacode/config/ConfigLoaderTest.java`
**依赖：** T4
**步骤：**
1. 读取用户级 `~/.lunacode/config.yaml`，仅解析其中的 `mcp.servers`。
2. 用户级文件不存在时视为空 MCP 配置。
3. 先放入用户级 Server，再用项目级同名 Server 完整覆盖。
4. 添加测试覆盖不同名合并、同名覆盖、用户级缺少 provider 字段仍可解析 MCP。

**验证：** 运行 `mvn -Dtest=ConfigLoaderTest test`，期望测试通过。

## T6: 添加 MCP transport 接口

**文件：** `src/main/java/com/lunacode/mcp/transport/McpTransport.java`, `src/main/java/com/lunacode/mcp/transport/McpTransportListener.java`
**依赖：** T1
**步骤：**
1. 定义 `start(listener)`、`send(message)`、`closeAsync()`。
2. 定义 listener 的 `onMessage`、`onClosed`、`onDiagnostic`。
3. 接口不暴露 Process、HttpClient 或具体传输细节。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T7: 实现 MCP transport factory

**文件：** `src/main/java/com/lunacode/mcp/transport/McpTransportFactory.java`
**依赖：** T1, T6
**步骤：**
1. 根据 `McpServerConfig.kind()` 分派 stdio 或 HTTP transport。
2. 将工作区根目录传给 stdio transport。
3. 将共享 `HttpClient` 或默认 `HttpClient` 传给 HTTP transport。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T8: 实现 stdio transport 基础收发

**文件：** `src/main/java/com/lunacode/mcp/transport/StdioMcpTransport.java`, `src/test/java/com/lunacode/mcp/transport/StdioMcpTransportTest.java`
**依赖：** T6, T7
**步骤：**
1. 使用 `ProcessBuilder` 按 `command + args` 启动子进程。
2. 设置子进程工作目录为 LunaCode 当前工作区根目录。
3. 将配置 env 合并到进程环境。
4. stdout 按 UTF-8 换行读取 JSON 消息并回调 listener。
5. stderr 按诊断信息回调。
6. `send` 将 JSON 单行写入 stdin。

**验证：** 运行 `mvn -Dtest=StdioMcpTransportTest test`，期望能启动测试子进程并收发一条 JSON 消息。

## T9: 完成 stdio transport 关闭与异常处理

**文件：** `src/main/java/com/lunacode/mcp/transport/StdioMcpTransport.java`, `src/test/java/com/lunacode/mcp/transport/StdioMcpTransportTest.java`
**依赖：** T8
**步骤：**
1. 子进程退出时调用 `onClosed`。
2. stdout 非法 JSON 时调用 `onClosed` 并保存错误原因。
3. `closeAsync` 先关闭 stdin，再等待进程退出。
4. 等待超时后终止仍未退出的进程。
5. 添加测试覆盖进程退出和非法 JSON。

**验证：** 运行 `mvn -Dtest=StdioMcpTransportTest test`，期望测试通过且无残留测试进程。

## T10: 实现 Streamable HTTP transport 基础请求

**文件：** `src/main/java/com/lunacode/mcp/transport/StreamableHttpMcpTransport.java`, `src/test/java/com/lunacode/mcp/transport/StreamableHttpMcpTransportTest.java`
**依赖：** T6, T7
**步骤：**
1. 使用 `HttpClient` 向配置 url 发送 JSON-RPC 请求。
2. 附加配置 headers。
3. 处理普通 JSON 响应并回调 `onMessage`。
4. 非 2xx 响应调用 `onClosed` 或完成发送错误。
5. 添加测试覆盖普通 JSON 响应。

**验证：** 运行 `mvn -Dtest=StreamableHttpMcpTransportTest test`，期望测试通过。

## T11: 增加 Streamable HTTP SSE 处理

**文件：** `src/main/java/com/lunacode/mcp/transport/StreamableHttpMcpTransport.java`, `src/test/java/com/lunacode/mcp/transport/StreamableHttpMcpTransportTest.java`
**依赖：** T10
**步骤：**
1. 识别 `text/event-stream` 响应。
2. 复用或适配现有 SSE parser 读取 data。
3. 将 data 中 JSON 消息回调给 listener。
4. 保存握手返回的会话相关 header，后续请求携带。
5. 添加测试覆盖 SSE 响应和 session header 复用。

**验证：** 运行 `mvn -Dtest=StreamableHttpMcpTransportTest test`，期望测试通过。

## T12: 添加 JSON-RPC 错误和 pending 结构

**文件：** `src/main/java/com/lunacode/mcp/rpc/JsonRpcError.java`, `src/main/java/com/lunacode/mcp/rpc/PendingJsonRpcRequest.java`
**依赖：** T6
**步骤：**
1. 新建 JSON-RPC 错误 record，包含 code、message、data。
2. 新建 pending 请求结构，保存 id、method、future、timeout task。
3. 确保结构不包含 MCP 业务字段。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T13: 实现 JsonRpcClient 请求和通知

**文件：** `src/main/java/com/lunacode/mcp/rpc/JsonRpcClient.java`, `src/test/java/com/lunacode/mcp/rpc/JsonRpcClientTest.java`
**依赖：** T12
**步骤：**
1. 为每个 request 生成唯一字符串 id。
2. 组装 JSON-RPC 2.0 request 消息。
3. 保存 pending 请求并调用 transport.send。
4. notify 不生成 id，不进入 pending 表。
5. 添加测试覆盖 request 和 notify 发送格式。

**验证：** 运行 `mvn -Dtest=JsonRpcClientTest test`，期望测试通过。

## T14: 实现 JsonRpcClient 响应配对

**文件：** `src/main/java/com/lunacode/mcp/rpc/JsonRpcClient.java`, `src/test/java/com/lunacode/mcp/rpc/JsonRpcClientTest.java`
**依赖：** T13
**步骤：**
1. `onMessage` 收到带 result 的响应时按 id 完成对应 future。
2. 收到 error 响应时按 id 失败对应 future。
3. 乱序响应仍完成正确请求。
4. 未知 id、重复 id、缺失 id 不完成其他请求。
5. 添加并发乱序响应测试。

**验证：** 运行 `mvn -Dtest=JsonRpcClientTest test`，期望测试通过。

## T15: 实现 JsonRpcClient 超时与关闭

**文件：** `src/main/java/com/lunacode/mcp/rpc/JsonRpcClient.java`, `src/test/java/com/lunacode/mcp/rpc/JsonRpcClientTest.java`
**依赖：** T14
**步骤：**
1. 为 request 安排超时。
2. 超时后移除 pending 并失败 future。
3. transport 关闭时失败所有 pending 请求。
4. 收到 Server 端请求时返回 Method not found 错误。
5. 添加测试覆盖超时、关闭和不支持请求。

**验证：** 运行 `mvn -Dtest=JsonRpcClientTest test`，期望测试通过。

## T16: 添加 MCP session 基础数据结构

**文件：** `src/main/java/com/lunacode/mcp/McpInitializeResult.java`, `src/main/java/com/lunacode/mcp/McpToolDefinition.java`, `src/main/java/com/lunacode/mcp/McpToolCallResult.java`, `src/main/java/com/lunacode/mcp/McpServerStatus.java`
**依赖：** T13
**步骤：**
1. 新建初始化结果结构。
2. 新建工具定义结构。
3. 新建工具调用结果结构。
4. 新建 Server 状态结构，包含 serverName、state、warning/error summary。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T17: 实现 MCP initialize 流程

**文件：** `src/main/java/com/lunacode/mcp/McpSession.java`, `src/test/java/com/lunacode/mcp/McpSessionTest.java`
**依赖：** T15, T16
**步骤：**
1. 发送 `initialize`，声明协议版本 `2025-06-18`。
2. 校验返回协议版本兼容。
3. 初始化成功后发送 `notifications/initialized`。
4. 版本不兼容时 session 标记 failed。
5. 添加测试覆盖成功和版本不兼容。

**验证：** 运行 `mvn -Dtest=McpSessionTest test`，期望测试通过。

## T18: 实现 tools/list 分页解析

**文件：** `src/main/java/com/lunacode/mcp/McpSession.java`, `src/test/java/com/lunacode/mcp/McpSessionTest.java`
**依赖：** T17
**步骤：**
1. 初始化成功后允许调用 `tools/list`。
2. 解析 tools 数组中的 name、description、inputSchema。
3. 识别 nextCursor 并继续分页拉取。
4. 未声明工具能力或返回错误时标记该 Server 不注册工具。
5. 添加分页和无工具能力测试。

**验证：** 运行 `mvn -Dtest=McpSessionTest test`，期望测试通过。

## T19: 实现 tools/call 流程

**文件：** `src/main/java/com/lunacode/mcp/McpSession.java`, `src/test/java/com/lunacode/mcp/McpSessionTest.java`
**依赖：** T18
**步骤：**
1. 组装 `tools/call` 请求，使用 original tool name。
2. 参数使用 Agent 传入 JSON 对象。
3. 成功响应转换为 `McpToolCallResult`。
4. 协议错误、远端错误、连接关闭转换为失败结果。
5. 添加成功、远端错误、连接断开测试。

**验证：** 运行 `mvn -Dtest=McpSessionTest test`，期望测试通过。

## T20: 实现 MCP 工具名分配器

**文件：** `src/main/java/com/lunacode/mcp/McpToolNameAllocator.java`, `src/test/java/com/lunacode/mcp/McpToolNameAllocatorTest.java`
**依赖：** T16
**步骤：**
1. 生成 `mcp_{serverName}_{toolName}`。
2. 对非法字符做供应商可接受的合法化处理。
3. 与内置工具或已有 MCP 工具冲突时追加稳定短哈希。
4. 保存 publicName 到 originalName 的映射输入。
5. 添加测试覆盖同名冲突、非法字符、稳定哈希。

**验证：** 运行 `mvn -Dtest=McpToolNameAllocatorTest test`，期望测试通过。

## T21: 实现 MCP 内容渲染器

**文件：** `src/main/java/com/lunacode/mcp/McpContentRenderer.java`, `src/test/java/com/lunacode/mcp/McpContentRendererTest.java`
**依赖：** T19
**步骤：**
1. 文本内容直接拼接进入结果正文。
2. 结构化 JSON 内容生成长度受限摘要。
3. 图片、二进制、未知内容只输出类型、数量、名称摘要。
4. 限制远端返回文本长度。
5. 添加测试覆盖文本、JSON、图片、未知类型和长度限制。

**验证：** 运行 `mvn -Dtest=McpContentRendererTest test`，期望测试通过。

## T22: 实现 McpToolWrapper

**文件：** `src/main/java/com/lunacode/tool/McpToolWrapper.java`, `src/test/java/com/lunacode/tool/McpToolWrapperTest.java`
**依赖：** T19, T21
**步骤：**
1. 包装 `McpToolDefinition` 和 `McpSession`。
2. `name()` 返回 publicName，执行时使用 originalName。
3. `isReadOnly()` 返回 false，`isDestructive()` 返回 true。
4. `isConcurrencySafe()` 返回 false，`category()` 返回 `mcp`。
5. `validateInput` 要求输入为 JSON object。
6. `shouldDefer()` 返回 true。
7. 添加测试覆盖属性、参数校验、执行成功、执行失败。

**验证：** 运行 `mvn -Dtest=McpToolWrapperTest test`，期望测试通过。

## T23: 扩展 Tool 接口支持 shouldDefer

**文件：** `src/main/java/com/lunacode/tool/Tool.java`
**依赖：** T22
**步骤：**
1. 给 `Tool` 添加 `default boolean shouldDefer()`。
2. 默认返回 false，保持内置工具行为不变。
3. 确认现有 Tool 实现无需改动即可编译。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T24: 添加延迟工具元数据结构

**文件：** `src/main/java/com/lunacode/tool/DeferredToolSummary.java`, `src/main/java/com/lunacode/tool/ToolDefinitionSnapshot.java`, `src/main/java/com/lunacode/tool/ToolDeclarationSet.java`
**依赖：** T23
**步骤：**
1. 新建 `DeferredToolSummary`。
2. 新建 `ToolDefinitionSnapshot`。
3. 新建 `ToolDeclarationSet`，包含可见工具声明和延迟工具摘要。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T25: 扩展 ToolRegistry 接口

**文件：** `src/main/java/com/lunacode/tool/ToolRegistry.java`
**依赖：** T24
**步骤：**
1. 增加 `getRegistered`。
2. 增加 `discoverDeferredTool`。
3. 增加 `isDeferredDiscovered`。
4. 增加 `deferredToolSummaries`。
5. 增加 `declarationsForModel`。
6. 保留现有 `toAPIFormat` 兼容入口。

**验证：** 运行 `mvn -DskipTests compile`，期望编译提示只剩实现类待适配。

## T26: 实现 DefaultToolRegistry 延迟状态

**文件：** `src/main/java/com/lunacode/tool/DefaultToolRegistry.java`, `src/test/java/com/lunacode/tool/ToolRegistryTest.java`
**依赖：** T25
**步骤：**
1. 增加 discovered set 管理延迟工具。
2. `get` 对未发现延迟工具返回 empty。
3. `getRegistered` 可返回未发现延迟工具。
4. `discoverDeferredTool` 返回完整 snapshot 并标记 discovered。
5. `deferredToolSummaries` 返回未发现延迟工具摘要。
6. 添加测试覆盖注册、未发现不可执行、发现后可执行。

**验证：** 运行 `mvn -Dtest=ToolRegistryTest test`，期望测试通过。

## T27: 实现 declarationsForModel

**文件：** `src/main/java/com/lunacode/tool/DefaultToolRegistry.java`, `src/test/java/com/lunacode/tool/ToolRegistryTest.java`
**依赖：** T26
**步骤：**
1. 构造 visible tools：非延迟工具和已发现延迟工具。
2. Plan mode 下继续隐藏 `AskUserQuestion`。
3. 不把未发现延迟工具完整 schema 放入 visible tools。
4. `toAPIFormat(mode)` 委托 `declarationsForModel(mode).visibleTools()`。
5. 添加测试覆盖普通模式、Plan mode、延迟工具。

**验证：** 运行 `mvn -Dtest=ToolRegistryTest test`，期望测试通过。

## T28: 实现 ToolSearchTool

**文件：** `src/main/java/com/lunacode/tool/ToolSearchTool.java`, `src/test/java/com/lunacode/tool/ToolSearchToolTest.java`
**依赖：** T26
**步骤：**
1. 定义工具名 `ToolSearch`。
2. input schema 要求 `name` 字段。
3. 执行时调用 `ToolRegistry.discoverDeferredTool(name)`。
4. 命中时返回名称、描述和 input schema。
5. 找不到、已禁用或不可公开时返回普通工具错误。
6. 添加测试确认不会触发 MCP session 调用。

**验证：** 运行 `mvn -Dtest=ToolSearchToolTest test`，期望测试通过。

## T29: 接入 MCP_HINT reminder

**文件：** `src/main/java/com/lunacode/prompt/DeferredToolReminderPolicy.java`, `src/main/java/com/lunacode/prompt/SystemReminderBuilder.java`, `src/test/java/com/lunacode/prompt/PromptContextBuilderTest.java`
**依赖：** T24
**步骤：**
1. 新建 `DeferredToolReminderPolicy`。
2. 当存在未发现延迟工具时生成 `SystemReminderKind.MCP_HINT`。
3. reminder 内容列出公开工具名和简短说明。
4. 没有延迟工具时不生成 MCP_HINT。
5. 添加测试覆盖有/无延迟工具。

**验证：** 运行 `mvn -Dtest=PromptContextBuilderTest test`，期望相关测试通过。

## T30: 改造 PromptContextBuilder 使用 ToolDeclarationSet

**文件：** `src/main/java/com/lunacode/prompt/PromptContextBuilder.java`, `src/main/java/com/lunacode/prompt/MessageChannelBuilder.java`, `src/test/java/com/lunacode/prompt/PromptContextBuilderTest.java`
**依赖：** T27, T29
**步骤：**
1. 让 `PromptContextBuilder.build` 接收 `ToolDeclarationSet`。
2. toolDeclarations 使用 `visibleTools`。
3. message reminders 注入 `deferredTools`。
4. 保留测试构造的兼容路径或同步更新测试。
5. 添加测试确认完整 schema 不出现在未发现延迟工具列表中。

**验证：** 运行 `mvn -Dtest=PromptContextBuilderTest test`，期望测试通过。

## T31: Agent Loop 使用 declarationsForModel

**文件：** `src/main/java/com/lunacode/agent/DefaultAgentLoop.java`, `src/test/java/com/lunacode/agent/DefaultAgentLoopTest.java`
**依赖：** T30
**步骤：**
1. 每轮调用 `toolRegistry.declarationsForModel(config.mode())`。
2. 将 `ToolDeclarationSet` 传给 `PromptContextBuilder`。
3. 确认 ToolSearch 标记 discovered 后下一轮会进入 visible tools。
4. 添加或更新测试覆盖延迟工具下一轮可见。

**验证：** 运行 `mvn -Dtest=DefaultAgentLoopTest test`，期望测试通过。

## T32: 实现 McpClientManager 并发发现

**文件：** `src/main/java/com/lunacode/mcp/McpClientManager.java`, `src/main/java/com/lunacode/mcp/McpDiscoveryResult.java`, `src/test/java/com/lunacode/mcp/McpClientManagerTest.java`
**依赖：** T7, T18, T20, T22
**步骤：**
1. 遍历 `McpConfig.servers()` 并发创建 session。
2. 每个 Server 独立 30 秒超时。
3. 成功时转换 tools/list 结果为 wrapper。
4. 单个 Server 失败时记录中文 warning。
5. 添加测试覆盖一个成功一个失败、一个慢超时。

**验证：** 运行 `mvn -Dtest=McpClientManagerTest test`，期望测试通过。

## T33: McpClientManager 关闭所有连接

**文件：** `src/main/java/com/lunacode/mcp/McpClientManager.java`, `src/test/java/com/lunacode/mcp/McpClientManagerTest.java`
**依赖：** T32
**步骤：**
1. 保存成功创建的 session。
2. `closeAsync` 遍历关闭所有 session。
3. 关闭异常汇总为 warning，不阻塞其他关闭。
4. 添加测试覆盖多个 session 都收到 close。

**验证：** 运行 `mvn -Dtest=McpClientManagerTest test`，期望测试通过。

## T34: 应用启动注册 ToolSearch

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`
**依赖：** T28
**步骤：**
1. 在内置工具注册之后注册 `ToolSearchTool`。
2. 给 ToolSearch 注入同一个 `DefaultToolRegistry`。
3. 确保 `ToolSearch` 在普通工具列表中可见。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T35: 应用启动发现并注册 MCP 工具

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`
**依赖：** T32, T34
**步骤：**
1. 根据 `config.mcp()` 创建 `McpClientManager`。
2. 调用 `discoverAll` 并注册返回的 `McpToolWrapper`。
3. 将 MCP 配置中的敏感值加入 `SensitiveValueMasker`。
4. 收集发现 warning 并准备展示。
5. 保持无 MCP 配置时原启动行为不变。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T36: 应用关闭时释放 MCP manager

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`
**依赖：** T33, T35
**步骤：**
1. 在应用生命周期中保存 `McpClientManager`。
2. TUI 退出后或 JVM shutdown hook 中调用 close。
3. 确保 stdio 子进程能被关闭。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T37: 启动 warning 接入可观察状态

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`, `src/main/java/com/lunacode/agent/event/AgentEvent.java`, `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`
**依赖：** T35
**步骤：**
1. 复用或扩展 warning 事件承载 MCP 启动 warning。
2. 在 TUI 启动后能看到 Server 跳过原因。
3. warning 不包含敏感值。

**验证：** 运行 `mvn -DskipTests compile`，期望编译通过。

## T38: 确认权限拒绝不触发 MCP 调用

**文件：** `src/test/java/com/lunacode/agent/execution/AgentToolRunnerPermissionTest.java`
**依赖：** T22, T26
**步骤：**
1. 构造一个已发现的 `McpToolWrapper`。
2. 配置权限引擎返回 deny。
3. 调用 AgentToolRunner 执行该工具。
4. 断言 wrapper 的远端 session 没有收到 `tools/call`。
5. 断言返回结构化权限拒绝结果。

**验证：** 运行 `mvn -Dtest=AgentToolRunnerPermissionTest test`，期望测试通过。

## T39: 确认未发现延迟工具不能直接执行

**文件：** `src/test/java/com/lunacode/tool/ToolRegistryTest.java`, `src/test/java/com/lunacode/agent/execution/AgentToolRunnerTest.java`
**依赖：** T26
**步骤：**
1. 注册一个 `shouldDefer=true` 的测试工具。
2. 在未 discover 前调用 registry.get。
3. 断言返回 empty。
4. 通过 AgentToolRunner 调用该名字，断言得到 tool_not_found 或 deferred_not_discovered。
5. 断言测试工具 execute 未被调用。

**验证：** 运行 `mvn -Dtest=ToolRegistryTest,AgentToolRunnerTest test`，期望测试通过。

## T40: 更新 config.example.yaml

**文件：** `config.example.yaml`
**依赖：** T4
**步骤：**
1. 增加 `mcp.servers` 顶层示例。
2. 添加一个 stdio Server 示例，包含 command、args、env。
3. 添加一个 HTTP Server 示例，包含 url、headers。
4. 注释说明用户级和项目级覆盖规则。

**验证：** 运行 `Get-Content config.example.yaml`，期望示例包含 stdio 和 HTTP 两种配置。

## T41: 添加测试用 stdio MCP Server

**文件：** `toolTest/mcp-test-server.js`
**依赖：** T17, T18, T19
**步骤：**
1. 编写一个最小 JSON-RPC stdio Server。
2. 支持 `initialize`、`notifications/initialized`、`tools/list`、`tools/call`。
3. 暴露一个返回测试数据的工具。
4. 保持输出为 UTF-8 单行 JSON。

**验证：** 运行 `node toolTest/mcp-test-server.js` 后手工输入一条 initialize JSON，期望输出 JSON-RPC 响应。

## T42: 集成测试 stdio MCP 发现

**文件：** `src/test/java/com/lunacode/mcp/McpClientManagerTest.java`
**依赖：** T32, T41
**步骤：**
1. 配置 stdio Server 指向 `toolTest/mcp-test-server.js`。
2. 调用 `discoverAll`。
3. 断言初始化成功。
4. 断言返回一个 `McpToolWrapper`。
5. 断言公开工具名符合 `mcp_{serverName}_{toolName}`。

**验证：** 运行 `mvn -Dtest=McpClientManagerTest test`，期望测试通过。

## T43: 集成测试 HTTP MCP 发现

**文件：** `src/test/java/com/lunacode/mcp/McpClientManagerTest.java`
**依赖：** T11, T32
**步骤：**
1. 使用测试 HTTP Server 模拟 Streamable HTTP。
2. 返回 initialize 和 tools/list 响应。
3. 调用 `discoverAll`。
4. 断言 HTTP Server 工具注册结果与 stdio 路径一致。

**验证：** 运行 `mvn -Dtest=McpClientManagerTest test`，期望测试通过。

## T44: 测试 ToolSearch 下一轮可见性

**文件：** `src/test/java/com/lunacode/prompt/PromptContextBuilderTest.java`, `src/test/java/com/lunacode/agent/DefaultAgentLoopTest.java`
**依赖：** T31
**步骤：**
1. 注册延迟 MCP 工具。
2. 构建第一轮 Prompt，断言工具 schema 不在 visible tools，名字在 MCP_HINT。
3. 调用 ToolSearch 标记 discovered。
4. 构建下一轮 Prompt，断言工具 schema 出现在 visible tools。

**验证：** 运行 `mvn -Dtest=PromptContextBuilderTest,DefaultAgentLoopTest test`，期望测试通过。

## T45: 全量单元测试

**文件：** 所有 Java 源码和测试
**依赖：** T1-T44
**步骤：**
1. 运行完整测试套件。
2. 记录失败项。
3. 修复失败测试对应的实现或测试预期。
4. 重跑直到通过。

**验证：** 运行 `mvn test`，期望全部测试通过。

## T46: 打包编译验证

**文件：** 所有 Java 源码
**依赖：** T45
**步骤：**
1. 运行 Maven 打包。
2. 确认 shade jar 生成。
3. 确认没有编译错误。

**验证：** 运行 `mvn package -DskipTests`，期望构建成功。

## T47: tmux 端到端准备

**文件：** `config.yaml`, `toolTest/mcp-test-server.js`
**依赖：** T46
**步骤：**
1. 准备一个测试配置，声明 stdio MCP Server。
2. 保持 provider 配置可用。
3. 确认 `ToolSearch` 和测试 MCP 工具会进入启动流程。

**验证：** 运行 `Get-Content config.yaml`，确认包含 `mcp.servers` 测试配置或有单独测试配置路径。

## T48: tmux 端到端验证

**文件：** 运行时行为
**依赖：** T47
**步骤：**
1. 在 tmux 中启动 LunaCode。
2. 输入真实请求：“调用配置中的 MCP 工具获取一段测试数据，然后总结结果”。
3. 观察第一轮是否通过 MCP_HINT 看到延迟工具名字。
4. 观察模型是否先调用 ToolSearch。
5. 观察下一轮是否调用 MCP 工具并返回中文总结。

**验证：** tmux 观察到 ToolSearch、MCP 工具调用、中文回复，且无未处理异常。

## 执行顺序

```text
T1
 -> T2 -> T3 -> T4 -> T5
 -> T6 -> T7
 -> T8 -> T9
 -> T10 -> T11
 -> T12 -> T13 -> T14 -> T15
 -> T16 -> T17 -> T18 -> T19
 -> T20 -> T21 -> T22 -> T23 -> T24 -> T25 -> T26 -> T27 -> T28
 -> T29 -> T30 -> T31
 -> T32 -> T33
 -> T34 -> T35 -> T36 -> T37
 -> T38 -> T39 -> T40 -> T41 -> T42 -> T43 -> T44
 -> T45 -> T46 -> T47 -> T48
```
