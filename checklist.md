# LunaCode TUI 对话内核 Checklist

> 每一项通过运行代码、测试或观察行为来验证，聚焦系统行为。

## 实现完整性

- [ ] LunaCode 可以通过 Maven 编译打包（验证：运行 `mvn package`，看到构建成功并生成 jar）。
- [ ] 启动入口存在且可执行（验证：运行打包后的入口命令，程序进入配置读取或 TUI 启动流程）。
- [ ] TUI 显示上方对话区域、下方输入框和底部状态栏（验证：启动程序，观察三段式界面清晰可见）。
- [ ] 用户提交输入后，对话区域出现 user 消息（验证：在输入框输入一条消息并回车，观察消息出现在对话区域）。
- [ ] assistant 消息流式开始时显示为 streaming，结束后显示为 complete（验证：使用模拟 Provider 或真实 Provider 流式响应，观察状态变化）。
- [ ] 流式出错时 assistant 消息显示为 error（验证：使用无效 API Key 或模拟错误事件，观察错误状态和错误摘要）。
- [ ] 状态栏显示当前 Provider 和模型（验证：使用 OpenAI 或 Anthropic 配置启动，观察状态栏）。
- [ ] 状态栏能显示 Token 用量或明确的未知状态（验证：使用包含 usage 的模拟事件和不包含 usage 的模拟事件分别运行）。

## 配置

- [ ] YAML 配置支持 `protocol`、`model`、`base_url`、`api_key`（验证：运行 `mvn test -Dtest=ConfigLoaderTest`）。
- [ ] `protocol` 只接受 `openai` 和 `anthropic`（验证：运行配置测试，非法 protocol 被拒绝）。
- [ ] API Key 支持环境变量占位符（验证：运行配置测试，`${ENV_NAME}` 能解析为环境变量值）。
- [ ] 配置错误不会泄露 API Key（验证：用包含假密钥的错误配置启动，错误输出不包含密钥原文）。
- [ ] `config.example.yaml` 不包含真实密钥（验证：人工检查文件，只出现环境变量占位符）。

## 对话管理

- [ ] 添加消息时返回唯一 ID（验证：运行 `mvn test -Dtest=DefaultConversationManagerTest`）。
- [ ] 根据消息 ID 追加流式内容只更新目标消息（验证：运行对话管理器测试）。
- [ ] assistant 消息状态能从 streaming 变为 complete（验证：运行对话管理器测试）。
- [ ] assistant 消息出错时能变为 error 并保存错误摘要（验证：运行对话管理器测试）。
- [ ] `snapshot()` 返回不可变快照，外部修改不会影响内部消息列表（验证：运行对话管理器测试）。
- [ ] 并发添加、追加和读取快照时不出现异常或结构损坏（验证：运行对话管理器并发测试）。
- [ ] `toAPIFormat()` 过滤 system、tool 和 error 消息（验证：运行对话管理器测试）。
- [ ] `toAPIFormat()` 合并相邻同角色消息（验证：运行对话管理器测试）。
- [ ] `toAPIFormat()` 输出首条为 user，且 user/assistant 交替出现（验证：运行对话管理器测试）。
- [ ] API 层消息只包含 role 和 content（验证：运行对话管理器测试或检查 `ApiMessage`）。

## 流式事件

- [ ] SSE 解析器能解析 `event:` 和 `data:` 行（验证：运行 `mvn test -Dtest=SseParserTest`）。
- [ ] SSE 解析器能合并多行 data（验证：运行 `mvn test -Dtest=SseParserTest`）。
- [ ] SSE 解析器能处理 OpenAI `[DONE]`（验证：运行 `mvn test -Dtest=SseParserTest`）。
- [ ] Anthropic `message_start` 映射为 `MessageStart` 并携带 input tokens（验证：运行 `mvn test -Dtest=AnthropicStreamMapperTest`）。
- [ ] Anthropic `content_block_start` 映射为 `ContentBlockStart`（验证：运行 Anthropic mapper 测试）。
- [ ] Anthropic `content_block_delta` 映射为 `ContentDelta`（验证：运行 Anthropic mapper 测试）。
- [ ] Anthropic `content_block_stop` 映射为 `ContentBlockStop`（验证：运行 Anthropic mapper 测试）。
- [ ] Anthropic `message_delta` 映射 output tokens 和 stop reason（验证：运行 Anthropic mapper 测试）。
- [ ] Anthropic `message_stop` 映射为 `MessageStop`（验证：运行 Anthropic mapper 测试）。
- [ ] OpenAI 首个有效 chunk 映射为 `MessageStart`（验证：运行 `mvn test -Dtest=OpenAiStreamMapperTest`）。
- [ ] OpenAI 文本增量映射为 `ContentDelta`（验证：运行 OpenAI mapper 测试）。
- [ ] OpenAI finish reason 和 usage 映射为 `MessageDelta`（验证：运行 OpenAI mapper 测试）。
- [ ] OpenAI `[DONE]` 映射为 `ContentBlockStop` 和 `MessageStop`（验证：运行 OpenAI mapper 测试）。
- [ ] JSON 解析失败会产生 `StreamEvent.Error`（验证：运行 mapper 错误输入测试）。

## Provider 与编排

- [ ] Provider 工厂能根据 `openai` 创建 OpenAI Provider（验证：运行 `mvn test` 或 Provider 工厂测试）。
- [ ] Provider 工厂能根据 `anthropic` 创建 Anthropic Provider（验证：运行 `mvn test` 或 Provider 工厂测试）。
- [ ] 非法 Provider 协议返回可理解错误（验证：运行配置或工厂测试）。
- [ ] Anthropic 请求体包含 `model`、`messages`、`stream: true`（验证：使用模拟 HTTP 客户端或可检查请求构造测试）。
- [ ] Anthropic thinking 启用时请求体包含 thinking 配置（验证：使用模拟 HTTP 客户端或可检查请求构造测试）。
- [ ] OpenAI 请求体包含 `model`、`messages`、`stream: true`（验证：使用模拟 HTTP 客户端或可检查请求构造测试）。
- [ ] Provider 网络或认证错误会映射为 error 状态且不泄露 API Key（验证：使用无效密钥或模拟错误响应启动）。
- [ ] 编排器在已有响应 streaming 时拒绝新输入并给出状态提示（验证：使用慢速模拟 Provider，流式期间再次提交输入）。
- [ ] 编排器在创建 assistant 占位消息前调用 `toAPIFormat()`（验证：使用测试 Provider 记录收到的 messages，不包含空 assistant 消息）。

## 集成

- [ ] TUI 只通过快照渲染消息，不直接修改对话管理器内部状态（验证：代码检查和对话管理器快照测试）。
- [ ] Provider 原始事件不会泄漏到 TUI 层（验证：TUI 依赖只包含内部消息和状态快照，不依赖 Anthropic/OpenAI 事件类）。
- [ ] 新增 Provider 理论上只需实现 `ChatProvider` 和流映射，不需要修改 TUI（验证：代码检查 Provider 抽象边界）。
- [ ] 项目全部单元测试通过（验证：运行 `mvn test`）。
- [ ] 项目无编译错误（验证：运行 `mvn compile`）。
- [ ] 项目能生成可运行包（验证：运行 `mvn package`）。

## 端到端场景

- [ ] 场景 1：使用模拟 OpenAI 流式响应启动 LunaCode，输入“你好”，对话区域逐步显示 assistant 回复，最终状态为 complete（验证：运行模拟配置，观察 TUI）。
- [ ] 场景 2：使用模拟 Anthropic 流式响应启动 LunaCode，事件序列包含 message_start、content_block_delta、message_delta、message_stop，状态栏更新 Token 用量（验证：运行模拟配置，观察 TUI）。
- [ ] 场景 3：使用无效 API Key 启动真实 Provider 请求，assistant 消息变为 error，错误摘要可读且不包含密钥（验证：输入一条消息后观察错误）。
- [ ] 场景 4：模型回复 streaming 期间再次提交用户输入，LunaCode 拒绝第二次提交并在状态栏提示正在响应中（验证：使用慢速模拟流）。
- [ ] 场景 5：连续完成两轮对话，第二轮请求发送给 Provider 的 API 消息包含第一轮 user/assistant 历史，且只包含 role/content（验证：使用测试 Provider 记录请求消息）。