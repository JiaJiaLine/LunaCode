# LunaCode MCP 客户端工具接入 Checklist

> 每一项都通过运行代码、观察行为或检查输出验证，聚焦系统行为；本清单通过前不进入实现阶段。

## 配置与启动发现

- [ ] 同时准备用户级 `~/.lunacode/config.yaml` 和项目级当前启动配置时，启动后能观察到 `mcp.servers` 已按用户级先读、项目级覆盖的规则合并；同名 Server 使用项目级声明，不同名 Server 同时存在。（验证：运行配置加载测试，或启动 LunaCode 观察启动 warning/status 摘要）
- [ ] stdio Server 配置包含 `command`、`args`、`env` 时，`${VAR}` 能在字符串中嵌入展开，子进程在当前工作区根目录启动，并完成 initialize 与 tools/list。（验证：运行 stdio discovery 集成测试，观察工具注册结果）
- [ ] Streamable HTTP Server 配置包含 `url`、`headers` 时，`Bearer ${TOKEN}` 这类字符串能正确展开，并通过 Streamable HTTP 完成 initialize 与 tools/list。（验证：运行 HTTP discovery 集成测试，观察工具注册结果）
- [ ] 任一 Server 引用不存在或为空的环境变量时，该 Server 被跳过并产生中文原因；内置工具和其他 Server 仍可用。（验证：运行配置解析/启动发现测试，观察 warning 和工具列表）
- [ ] Server 配置同时声明 `command` 与 `url`，或两者都缺失时，只跳过该 Server，不阻断 LunaCode 启动。（验证：运行配置解析测试，观察中文 warning）
- [ ] 配置示例包含 `mcp.servers` 下的 stdio 示例、HTTP 示例、嵌入式环境变量展开示例，以及用户级/项目级覆盖规则说明。（验证：检查 `config.example.yaml`）

## 传输与 JSON-RPC

- [ ] MCP 上层会话、JSON-RPC 请求配对、工具发现和工具调用只依赖统一 transport 行为，不直接依赖 stdio 或 HTTP 细节。（验证：运行编译和 transport/session 测试，确认 stdio 与 HTTP 共用同一套 session 测试路径）
- [ ] stdio transport 通过 UTF-8 单行 JSON 在 stdin/stdout 收发协议消息，stderr 只作为诊断信息，不参与协议解析。（验证：运行 `StdioMcpTransportTest`）
- [ ] stdio Server 退出、输出非法 JSON、关闭管道或响应超时时，该 Server 被标记断开，未完成请求以结构化错误完成。（验证：运行 stdio transport 异常测试）
- [ ] Streamable HTTP transport 能处理普通 JSON 响应和 SSE 响应，并保存握手产生的会话头用于后续请求。（验证：运行 `StreamableHttpMcpTransportTest`）
- [ ] HTTP 非 2xx、协议错误、非法 JSON、网络异常或超时时，只让对应 Server 或对应工具调用失败，不影响其他 Server。（验证：运行 HTTP transport 和 manager 故障隔离测试）
- [ ] JSON-RPC 客户端为每个请求生成唯一 id，响应乱序返回时仍按 id 完成正确请求。（验证：运行 `JsonRpcClientTest` 的并发乱序响应用例）
- [ ] 未知 id、重复 id、缺失 id 的响应不会误完成其他请求。（验证：运行 `JsonRpcClientTest` 的异常响应用例）
- [ ] Server 发来的通知可被忽略；Server 发来本阶段不支持的请求时，客户端返回明确 JSON-RPC 错误而不是卡死。（验证：运行 `JsonRpcClientTest` 的通知和 unsupported request 用例）

## MCP 会话与工具发现

- [ ] 每个 Server 连接后先发送 `initialize`，声明协议版本 `2025-06-18`，成功后发送 `notifications/initialized`。（验证：运行 `McpSessionTest`）
- [ ] Server 返回不兼容协议版本时，该 Server 初始化失败并被跳过，其他 Server 继续发现。（验证：运行 `McpSessionTest` 和 `McpClientManagerTest`）
- [ ] tools/list 在 Server 声明工具能力后执行，支持分页游标直到工具列表收集完成。（验证：运行 `McpSessionTest` 的分页用例）
- [ ] Server 未声明工具能力、tools/list 失败或单个工具 schema 无法作为对象参数使用时，不注册坏工具，并记录中文 warning；同一 Server 的其他合法工具继续注册。（验证：运行 session/manager 工具解析测试）
- [ ] 多个 Server 启动发现并发执行，每个 Server 独立使用 30 秒超时；慢 Server 超时后不可用，其他已成功 Server 的工具仍注册成功。（验证：运行 `McpClientManagerTest`）
- [ ] 同一 Server 启动发现后连接被缓存复用；重复调用同一 Server 工具不会重复 initialize 或 tools/list。（验证：运行 manager/session 调用计数测试）
- [ ] LunaCode 退出时关闭所有 MCP 连接；stdio 子进程不残留，未完成请求被释放或失败完成。（验证：运行 manager close 测试，并在 tmux E2E 后检查进程状态）

## 工具包装与结果转换

- [ ] 远端工具公开名采用 `mcp_` + serverName + `_` + toolDef.name 的格式；非法字符被合法化处理。（验证：运行 `McpToolNameAllocatorTest`）
- [ ] 两个 Server 暴露同名工具，或远端工具与内置工具同名时，公开工具名稳定唯一，合法化后冲突会追加稳定短哈希后缀，且不会覆盖内置工具。（验证：运行命名冲突测试）
- [ ] LunaCode 保存公开工具名到 MCP 原始工具名的映射；Agent 调用公开名时，发送给 Server 的是原始工具名和 Agent 参数 JSON 对象。（验证：运行 `McpToolWrapperTest` 或 session call 测试）
- [ ] MCP 工具成功返回文本内容时，Agent Loop 收到普通工具结果正文。（验证：运行 wrapper/content renderer 测试）
- [ ] MCP 工具返回结构化内容时，Agent Loop 收到长度受限的可读 JSON 摘要。（验证：运行 `McpContentRendererTest`）
- [ ] MCP 工具返回图片、二进制资源或未知非文本内容时，LunaCode 不落盘、不做富媒体展示，只返回类型、数量、名称等摘要。（验证：运行 `McpContentRendererTest`）
- [ ] MCP 工具返回远端错误、协议错误、超时或连接断开时，Agent Loop 收到普通工具错误结果，包含 Server 名、远端工具名和简短失败原因。（验证：运行 `McpToolWrapperTest` 和 `McpSessionTest`）
- [ ] 一个 Server 运行中断开后，调用该 Server 的工具返回连接不可用错误；其他 MCP Server 工具和内置工具仍可继续调用。（验证：运行 manager 故障隔离测试）

## 权限与信任边界

- [ ] MCP 工具默认按可能有副作用的外部工具处理，不因远端描述或 schema 自动标记为只读或自动放行。（验证：运行 `McpToolWrapperTest` 检查工具属性）
- [ ] MCP 工具调用经过现有权限路径；权限规则能按公开工具名配置 allow、ask、deny。（验证：运行 `AgentToolRunnerPermissionTest`）
- [ ] 命中 deny 或用户拒绝权限确认时，LunaCode 不向 MCP Server 发送 `tools/call`，Agent Loop 收到结构化拒绝结果。（验证：运行权限拒绝不触发远端调用测试）
- [ ] 未被 ToolSearch 发现的延迟 MCP 工具不能被普通执行路径直接拿到并执行。（验证：运行 `ToolRegistryTest` 和 `AgentToolRunnerTest`）
- [ ] 恶意或异常 MCP Server 返回包含提示注入文本的工具描述、schema、错误或工具结果时，LunaCode 仍按不可信输入处理，限制展示长度，不绕过权限检查或工具注册边界。（验证：运行内容限制/权限路径测试）
- [ ] 错误摘要、事件流、工具结果和普通回复中不出现展开后的 API Key、Token、Authorization 头或配置中的敏感环境变量值。（验证：运行敏感值遮蔽测试，并检查启动 warning 与工具错误输出）

## 延迟加载与 Prompt 集成

- [ ] MCP 工具注册后标记为延迟工具，完整 schema 默认不进入模型普通工具列表。（验证：运行 `ToolRegistryTest` 和 `PromptContextBuilderTest`）
- [ ] Agent Loop 每轮生成工具列表时，未发现的延迟 MCP 工具只以公开名字和简短说明出现在 `MCP_HINT` system-reminder 中。（验证：运行 prompt 构建测试，检查 visible tools 与 reminder）
- [ ] `ToolSearch` 是普通可见工具，模型可以调用它按公开工具名查询延迟 MCP 工具完整定义。（验证：运行 `ToolSearchToolTest`）
- [ ] `ToolSearch` 命中延迟 MCP 工具后，返回完整名称、描述和输入 schema，并标记为已发现。（验证：运行 `ToolSearchToolTest`）
- [ ] `ToolSearch` 标记 discovered 后，下一轮 Agent Loop 的普通工具列表包含该 MCP 工具完整 schema。（验证：运行 `DefaultAgentLoopTest` 或 prompt 下一轮可见性测试）
- [ ] `ToolSearch` 查询不存在、已禁用、未注册或不可公开的工具时，返回普通工具错误结果，不触发 MCP Server 调用，也不让 Agent Loop 崩溃。（验证：运行 `ToolSearchToolTest`）
- [ ] `ToolSearch` 只读取本地 Registry 元数据，不启动 Server、不重连 Server、不发送 `tools/call`，也不返回 headers、env 或展开后的密钥值。（验证：运行 ToolSearch 隔离测试）

## 编译与自动化测试

- [ ] 项目能在 MCP 改动后完整编译通过。（验证：运行 `mvn -DskipTests compile`）
- [ ] 配置、环境变量展开、MCP 命名、transport、JSON-RPC、session、manager、wrapper、registry、ToolSearch、prompt 和权限相关单元测试全部通过。（验证：运行 `mvn test`）
- [ ] 打包构建成功并生成可运行产物。（验证：运行 `mvn package -DskipTests`）
- [ ] 自动化测试覆盖配置合并、嵌入式环境变量展开、公开工具命名与冲突后缀、初始化握手、协议版本不兼容、列工具分页、坏工具 schema 跳过、工具调用、JSON-RPC id 配对、单 Server 失败隔离和敏感值遮蔽。（验证：检查测试报告或测试用例名称与断言）

## 端到端场景

- [ ] 使用同一套 MCP 会话和 JSON-RPC 逻辑分别接入一个 stdio Server 和一个 Streamable HTTP Server，两者都能完成初始化、列工具、ToolSearch 延迟发现、权限审批和工具调用；除连接方式不同外，Agent 可观察行为一致。（验证：运行 stdio/HTTP 集成测试）
- [ ] 在 tmux 中启动 LunaCode，输入“调用配置中的 MCP 工具获取一段测试数据，然后总结结果”，能观察到 MCP 工具名先出现在 `MCP_HINT`，随后模型调用 `ToolSearch` 获取完整定义。（验证：tmux 观察 Agent Loop 事件）
- [ ] 同一 tmux 场景中，`ToolSearch` 之后下一轮模型调用 MCP 工具，LunaCode 发送 `tools/call`，收到测试数据并继续中文总结回复。（验证：tmux 观察工具调用事件和最终中文回复）
- [ ] 同一 tmux 场景中，权限拒绝路径可观察：为 MCP 公开工具名配置 deny 后再次请求调用，LunaCode 不发送 `tools/call`，并返回结构化拒绝结果。（验证：tmux 或权限集成测试观察）
- [ ] 退出 tmux 中的 LunaCode 后，测试 stdio MCP Server 子进程没有残留。（验证：检查系统进程列表）
