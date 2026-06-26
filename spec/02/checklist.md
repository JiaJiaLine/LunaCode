# LunaCode 工具系统 Checklist

> 每一项通过运行代码、测试或观察行为来验证，聚焦系统行为。

## 实现完整性

- [ ] 工具核心接口包含完整能力集：`name()`、`description()`、`inputSchema()`、`execute()`、`isReadOnly()`、`isDestructive()`、`isConcurrencySafe()`、`category()`、`validateInput()`（验证：运行 `mvn test -DskipTests`，编译通过，并检查 `Tool` 接口方法完整）。
- [ ] `ToolResult` 只包含 `content`、`isError`、`metadata` 三类信息（验证：运行 `mvn test -DskipTests`，并检查 `ToolResult` 字段）。
- [ ] 工具普通失败返回 `ToolResult.isError=true`，不会作为程序级 error 抛出（验证：运行工具执行器测试，未知工具、非法参数、文件不存在均返回错误工具结果）。
- [ ] metadata 不会进入发送给模型的消息内容（验证：运行消息格式测试，tool_result 内容只包含 `ToolResult.content` 和 `isError`，不包含 metadata 字段）。
- [ ] 六个内置工具均已注册且默认启用：`ReadFile`、`WriteFile`、`EditFile`、`Bash`、`Glob`、`Grep`（验证：运行 `mvn test -Dtest=ToolRegistryTest`）。
- [ ] 工具注册中心支持按名称启用和禁用工具（验证：运行 `mvn test -Dtest=ToolRegistryTest`，禁用后工具不出现在启用列表中）。
- [ ] `ToolRegistry.toAPIFormat()` 输出 Claude API 要求的 tools 数组（验证：运行 `mvn test -Dtest=ToolRegistryTest`，输出包含 `name`、`description`、`input_schema`）。
- [ ] 每次首次调用 Claude API 前都会读取当前启用工具列表（验证：运行编排器测试，mock registry 记录 `toAPIFormat()` 被调用）。

## 工具行为

- [ ] `ReadFile` 返回带原始行号的文件内容（验证：运行 `mvn test -Dtest=ReadFileToolTest`，结果内容包含形如 `1\t...` 的行）。
- [ ] `ReadFile` 支持 `offset` 和 `limit` 分段读取（验证：运行 `mvn test -Dtest=ReadFileToolTest`，`offset=2, limit=2` 只返回第 2-3 行）。
- [ ] `ReadFile` 的 metadata 包含路径、起止行、总行数、文件大小、修改时间和截断状态（验证：运行 `mvn test -Dtest=ReadFileToolTest`）。
- [ ] `ReadFile` 读取不存在文件时返回 `isError=true`，不会造成程序级 error（验证：运行 `mvn test -Dtest=ReadFileToolTest`）。
- [ ] `WriteFile` 写入前递归创建父目录（验证：运行 `mvn test -Dtest=WriteFileToolTest`，嵌套目录不存在时写入成功）。
- [ ] `WriteFile` 写入目标文本文件并返回写入摘要和 metadata（验证：运行 `mvn test -Dtest=WriteFileToolTest`，文件内容正确，metadata 包含写入字节数或字符数）。
- [ ] `WriteFile` 在 POSIX 文件系统上设置父目录 `0755`、文件 `0644`，非 POSIX 时在 metadata 标记 best effort（验证：运行 `mvn test -Dtest=WriteFileToolTest`，根据当前文件系统断言权限或 `permissionsApplied=false`）。
- [ ] `EditFile` 在原文唯一匹配时修改文件（验证：运行 `mvn test -Dtest=EditFileToolTest`，文件内容完成替换）。
- [ ] `EditFile` 在原文匹配不到时不修改文件，并返回 `isError=true`（验证：运行 `mvn test -Dtest=EditFileToolTest`）。
- [ ] `EditFile` 在原文匹配多次时不修改文件，并返回 `isError=true`（验证：运行 `mvn test -Dtest=EditFileToolTest`）。
- [ ] `Bash` 返回退出码、stdout、stderr、是否超时和执行耗时 metadata（验证：运行 `mvn test -Dtest=BashToolTest`）。
- [ ] `Bash` 命令非零退出时返回 `isError=true`，并保留输出摘要（验证：运行 `mvn test -Dtest=BashToolTest`）。
- [ ] `Bash` 超时时终止命令并返回 `isError=true`，TUI 或编排线程不长时间卡住（验证：运行 `mvn test -Dtest=BashToolTest`）。
- [ ] `Glob` 支持 `**` 递归匹配任意层级子目录（验证：运行 `mvn test -Dtest=GlobToolTest`，`src/**/*.java` 匹配多层 Java 文件）。
- [ ] `Glob` 无匹配时返回空列表而不是错误（验证：运行 `mvn test -Dtest=GlobToolTest`）。
- [ ] `Grep` 返回文件路径、行号、列号和匹配行摘要（验证：运行 `mvn test -Dtest=GrepToolTest`）。
- [ ] `Grep` 跳过明显二进制文件和构建产物目录（验证：运行 `mvn test -Dtest=GrepToolTest`，构造二进制文件和 target 目录样本）。
- [ ] 文件类工具拒绝访问工作区外路径（验证：运行路径解析或各文件工具测试，传入 `..` 或工作区外绝对路径返回 `isError=true`）。
- [ ] 工具输出过长时返回截断后的 content，并在 metadata 标记截断（验证：运行 ReadFile、Bash 或 Grep 的长输出测试）。
- [ ] 工具输出不会泄露配置中的 API Key（验证：运行脱敏器或 Bash 输出测试，假密钥被替换为脱敏文本）。

## Claude 工具流解析

- [ ] Claude `content_block_start` 中 `type=tool_use` 时记录工具调用 id 和 name（验证：运行 `mvn test -Dtest=ClaudeToolUseStreamMapperTest`）。
- [ ] Claude `content_block_delta` 中 `type=input_json_delta` 时追加 `partial_json`（验证：运行 `mvn test -Dtest=ClaudeToolUseStreamMapperTest`）。
- [ ] Claude `content_block_stop` 时解析完整 JSON 并发出 `StreamEvent.ToolUse`（验证：运行 `mvn test -Dtest=ClaudeToolUseStreamMapperTest`）。
- [ ] JSON 参数碎片 `{`、`"path"`、`: "/main.py"}` 能拼成完整输入（验证：运行 `mvn test -Dtest=ClaudeToolUseStreamMapperTest`）。
- [ ] 工具 JSON 参数解析失败时产生流解析 error，而不是执行工具（验证：运行 `mvn test -Dtest=ClaudeToolUseStreamMapperTest`）。
- [ ] 普通文本内容块仍然映射为文本增量，第一章流式文本行为不丢失（验证：运行现有 stream mapper 测试）。

## 消息格式与历史

- [ ] `ApiMessage` 使用 `role + List<ContentBlock>` 表示消息（验证：运行 `mvn test -DskipTests`，消息模型编译通过）。
- [ ] assistant 消息可以同时包含 text 和 tool_use 内容块，且不会被拆成两条消息（验证：运行 `mvn test -Dtest=ToolMessageFormatTest`）。
- [ ] tool_result 放在 user 角色消息中（验证：运行 `mvn test -Dtest=ToolMessageFormatTest`）。
- [ ] 多个 tool_use 保持在同一条 assistant 消息中（验证：运行 `mvn test -Dtest=ToolMessageFormatTest`）。
- [ ] 多个 tool_result 保持在同一条 user 消息中（验证：运行 `mvn test -Dtest=ToolMessageFormatTest`）。
- [ ] tool_use 和 tool_result 通过 id 正确配对（验证：运行 `mvn test -Dtest=ToolMessageFormatTest`）。
- [ ] 普通 user/assistant 交替格式不被工具结果打破（验证：运行 `mvn test -Dtest=ToolMessageFormatTest`，tool_result 作为 user 内容块出现）。
- [ ] 第一章普通对话历史转换仍然可用（验证：运行 `mvn test -Dtest=DefaultConversationManagerTest`）。

## Provider 与编排

- [ ] Claude 请求体在首次请求中包含当前启用工具列表（验证：运行 Provider 请求体测试，`tools` 包含六个工具）。
- [ ] Claude 请求体在最终回复请求中不包含工具列表（验证：运行 `mvn test -Dtest=ToolOrchestratorTest`，第二次请求 tools 为空或缺省）。
- [ ] Claude 请求体正确序列化 assistant 的 text 和 tool_use 内容块（验证：运行 Provider 请求体测试）。
- [ ] Claude 请求体正确序列化 user 的 tool_result 内容块和 `tool_use_id`（验证：运行 Provider 请求体测试）。
- [ ] 模型没有请求工具时，LunaCode 保持普通流式对话行为（验证：运行 `mvn test -Dtest=DefaultChatOrchestratorTest`）。
- [ ] 单个工具调用能完成：模型请求 `ReadFile`，LunaCode 执行工具，回灌 tool_result，再生成最终 assistant 回复（验证：运行 `mvn test -Dtest=ToolOrchestratorTest`）。
- [ ] 多个工具调用能完成：同一 assistant 消息中的 `ReadFile` 和 `Grep` 顺序执行，结果放入同一条 user 消息（验证：运行 `mvn test -Dtest=ToolOrchestratorTest`）。
- [ ] 工具不存在或已禁用时返回 `ToolResult.isError=true`，最终 assistant 可基于错误继续回复（验证：运行 `mvn test -Dtest=ToolOrchestratorTest`）。
- [ ] 工具参数非法时返回 `ToolResult.isError=true`，不会作为程序级 error 终止流程（验证：运行 `mvn test -Dtest=ToolOrchestratorTest`）。
- [ ] 工具结果回灌后的第二次响应如果再次出现工具调用，LunaCode 不执行，并给出本阶段不支持连环工具调用的可理解提示（验证：运行 `mvn test -Dtest=ToolOrchestratorTest`）。
- [ ] Provider 流错误或 JSON 流解析错误会显示为程序级错误状态（验证：运行 stream/orchestrator 错误测试）。

## TUI 与状态

- [ ] 工具执行中状态栏显示 `tool_running` 和当前工具名（验证：使用模拟慢工具启动 LunaCode，观察状态栏）。
- [ ] 工具成功后状态栏显示 `tool_done` 或回到 `idle`，并可见工具摘要（验证：使用模拟工具成功场景观察 TUI）。
- [ ] 工具返回 `isError=true` 时状态栏显示 `tool_error`，但程序继续生成最终回复（验证：使用参数错误工具调用观察 TUI）。
- [ ] TUI 可以展示 metadata 摘要，例如命令耗时、退出码、是否截断（验证：运行 Bash 或长输出工具场景，观察状态栏或工具摘要）。
- [ ] metadata 不作为模型消息显示，也不进入 tool_result content（验证：运行消息格式测试，并观察对话区不显示完整 metadata JSON）。

## 编译与测试

- [ ] 项目编译无错误（验证：运行 `mvn compile`）。
- [ ] 全部单元测试通过（验证：运行 `mvn test`）。
- [ ] 项目可以打包为可运行 jar（验证：运行 `mvn package`，`target` 目录生成 jar）。
- [ ] 现有第一章配置、对话、Provider、流式文本测试仍然通过（验证：运行 `mvn test`，旧测试无回归）。
- [ ] `config.example.yaml` 不包含真实 API Key（验证：人工检查文件，只出现环境变量占位符或示例值）。

## 端到端场景

- [ ] 场景 1：在 tmux 中启动 LunaCode，输入“读取当前项目的 pom.xml 并总结依赖”，模型请求 `ReadFile`，LunaCode 执行工具并回灌文件内容，最终 assistant 用中文总结依赖（验证：观察 TUI 对话区和状态栏）。
- [ ] 场景 2：在 tmux 中启动 LunaCode，输入“从第 2 行开始读取 pom.xml 的 3 行内容”，模型请求 `ReadFile` 且包含 `offset` 和 `limit`，工具结果只包含指定范围和原始行号（验证：观察 tool_result 或最终回复中的行号）。
- [ ] 场景 3：在 tmux 中启动 LunaCode，输入“找出 src 下所有 Java 文件”，模型请求 `Glob`，返回包含多层目录匹配结果（验证：观察最终回复列出匹配文件）。
- [ ] 场景 4：在 tmux 中启动 LunaCode，输入“搜索 ChatProvider 的引用”，模型请求 `Grep`，返回文件路径和行号，最终回复总结匹配位置（验证：观察最终回复包含路径和行号）。
- [ ] 场景 5：在 tmux 中启动 LunaCode，输入“把临时测试文件中的 hello 改成 hello LunaCode”，模型请求 `EditFile`，唯一匹配时文件被修改，最终回复说明修改完成（验证：读取文件内容确认变更）。
- [ ] 场景 6：在 tmux 中启动 LunaCode，输入会触发 `EditFile` 多匹配的请求，工具返回 `isError=true` 且文件未修改，最终回复说明需要更精确原文（验证：观察错误提示并读取文件确认未变更）。
- [ ] 场景 7：在 tmux 中启动 LunaCode，输入“运行一个会失败的命令并解释结果”，模型请求 `Bash`，工具返回非零退出的 `isError=true`，最终回复解释 stdout/stderr 摘要（验证：观察最终回复和状态栏）。
- [ ] 场景 8：在 tmux 中启动 LunaCode，使用模拟 Provider 一次返回 `ReadFile` 和 `Grep` 两个 tool_use，LunaCode 把两个 tool_result 放在同一条 user 消息中回灌（验证：检查测试日志或 TUI 调试输出）。
- [ ] 场景 9：在 tmux 中启动 LunaCode，模拟最终回复再次请求工具，LunaCode 不执行第二次工具调用，并给出不支持连环工具调用的提示（验证：观察最终回复或状态栏）。
