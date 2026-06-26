# LunaCode 工具系统 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/tool/Tool.java` | 定义统一工具接口 |
| 新建 | `src/main/java/com/lunacode/tool/ToolResult.java` | 定义工具执行结果 |
| 新建 | `src/main/java/com/lunacode/tool/ValidationError.java` | 定义工具入参校验错误 |
| 新建 | `src/main/java/com/lunacode/tool/ToolExecutionContext.java` | 定义工具执行上下文 |
| 新建 | `src/main/java/com/lunacode/tool/ToolUse.java` | 定义 Claude 工具调用请求 |
| 新建 | `src/main/java/com/lunacode/tool/ToolRegistry.java` | 定义工具注册中心接口 |
| 新建 | `src/main/java/com/lunacode/tool/DefaultToolRegistry.java` | 实现注册、启停、查询和 `toAPIFormat()` |
| 新建 | `src/main/java/com/lunacode/tool/ToolExecutor.java` | 定义工具执行器接口 |
| 新建 | `src/main/java/com/lunacode/tool/DefaultToolExecutor.java` | 实现工具查找、校验、执行和错误包装 |
| 新建 | `src/main/java/com/lunacode/tool/WorkspacePathResolver.java` | 限制文件工具只能访问工作区内路径 |
| 新建 | `src/main/java/com/lunacode/tool/SensitiveValueMasker.java` | 对工具输出中的敏感值脱敏 |
| 新建 | `src/main/java/com/lunacode/tool/ReadFileTool.java` | 实现 `ReadFile` |
| 新建 | `src/main/java/com/lunacode/tool/WriteFileTool.java` | 实现 `WriteFile` |
| 新建 | `src/main/java/com/lunacode/tool/EditFileTool.java` | 实现 `EditFile` |
| 新建 | `src/main/java/com/lunacode/tool/BashTool.java` | 实现 `Bash` |
| 新建 | `src/main/java/com/lunacode/tool/GlobTool.java` | 实现 `Glob` |
| 新建 | `src/main/java/com/lunacode/tool/GrepTool.java` | 实现 `Grep` |
| 新建 | `src/main/java/com/lunacode/conversation/ContentBlock.java` | 表达 Claude text、tool_use、tool_result 内容块 |
| 修改 | `src/main/java/com/lunacode/conversation/ApiMessage.java` | 从纯文本消息扩展为 `role + List<ContentBlock>` |
| 修改 | `src/main/java/com/lunacode/conversation/InternalMessage.java` | 保存工具展示信息和 metadata 摘要 |
| 修改 | `src/main/java/com/lunacode/conversation/ConversationManager.java` | 增加混合内容块消息接口 |
| 修改 | `src/main/java/com/lunacode/conversation/DefaultConversationManager.java` | 实现 tool_use/tool_result 历史保存与 API 格式转换 |
| 新建 | `src/main/java/com/lunacode/stream/ToolUseBuffer.java` | 累积 Claude `input_json_delta` |
| 修改 | `src/main/java/com/lunacode/stream/StreamEvent.java` | 增加 `TextDelta` 和 `ToolUse` 事件 |
| 修改 | `src/main/java/com/lunacode/stream/AnthropicStreamMapper.java` | 解析 Claude tool_use 流式事件 |
| 修改 | `src/main/java/com/lunacode/provider/ChatProvider.java` | 接收消息和当前启用工具声明 |
| 修改 | `src/main/java/com/lunacode/provider/AnthropicProvider.java` | 请求体写入 tools 和混合内容块消息 |
| 修改 | `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java` | 增加工具执行状态展示字段 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 串联工具调用、结果回灌和最终回复 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 创建并注入工具注册中心和执行器 |
| 修改 | `src/main/java/com/lunacode/tui/LanternaLunaTui.java` | 展示工具状态和 metadata 摘要 |
| 新建 | `src/test/java/com/lunacode/tool/ToolRegistryTest.java` | 覆盖注册中心和 Claude API 格式 |
| 新建 | `src/test/java/com/lunacode/tool/ReadFileToolTest.java` | 覆盖 ReadFile 行号、分页和错误 |
| 新建 | `src/test/java/com/lunacode/tool/WriteFileToolTest.java` | 覆盖 WriteFile 建目录、写入和权限 |
| 新建 | `src/test/java/com/lunacode/tool/EditFileToolTest.java` | 覆盖唯一匹配、无匹配、多匹配 |
| 新建 | `src/test/java/com/lunacode/tool/BashToolTest.java` | 覆盖命令成功、非零退出和超时 |
| 新建 | `src/test/java/com/lunacode/tool/GlobToolTest.java` | 覆盖 `**` 递归匹配 |
| 新建 | `src/test/java/com/lunacode/tool/GrepToolTest.java` | 覆盖内容搜索结果 |
| 新建 | `src/test/java/com/lunacode/stream/ClaudeToolUseStreamMapperTest.java` | 覆盖 tool_use JSON 碎片拼接 |
| 新建 | `src/test/java/com/lunacode/conversation/ToolMessageFormatTest.java` | 覆盖 text/tool_use/tool_result 消息结构 |
| 新建 | `src/test/java/com/lunacode/orchestrator/ToolOrchestratorTest.java` | 覆盖工具执行、回灌和最终回复流程 |

## T1: 定义工具核心接口

**文件：** `src/main/java/com/lunacode/tool/Tool.java`  
**依赖：** 无  
**步骤：**
1. 新建 `Tool` 接口。
2. 定义 `name()`、`description()`、`inputSchema()`、`execute()`、`isReadOnly()`、`isDestructive()`、`isConcurrencySafe()`、`category()`、`validateInput()`。
3. 使用 Jackson `JsonNode` 表示输入和 Schema。

**验证：** 运行 `mvn test -DskipTests`，期望编译能识别新接口。

## T2: 定义工具结果和校验错误

**文件：** `src/main/java/com/lunacode/tool/ToolResult.java`、`src/main/java/com/lunacode/tool/ValidationError.java`  
**依赖：** T1  
**步骤：**
1. 新建 `ToolResult`，字段为 `content`、`isError`、`metadata`。
2. 新建 `ValidationError`，字段为 `code`、`message`。
3. 为 `ToolResult` 提供成功和失败的静态构造方法。

**验证：** 运行 `mvn test -DskipTests`，期望编译通过。

## T3: 定义工具调用和执行上下文

**文件：** `src/main/java/com/lunacode/tool/ToolUse.java`、`src/main/java/com/lunacode/tool/ToolExecutionContext.java`  
**依赖：** T2  
**步骤：**
1. 新建 `ToolUse`，包含 `id`、`name`、`input`。
2. 新建 `ToolExecutionContext`，包含工作区根路径、命令超时、最大内容长度和脱敏器。
3. 保持字段不可变。

**验证：** 运行 `mvn test -DskipTests`，期望编译通过。

## T4: 实现敏感值脱敏器

**文件：** `src/main/java/com/lunacode/tool/SensitiveValueMasker.java`  
**依赖：** T3  
**步骤：**
1. 新建脱敏器接口或类。
2. 支持注册需要隐藏的字符串。
3. 提供对工具输出文本的脱敏方法。

**验证：** 新增最小单元测试或运行 `mvn test -DskipTests`，期望编译通过。

## T5: 实现工作区路径解析

**文件：** `src/main/java/com/lunacode/tool/WorkspacePathResolver.java`  
**依赖：** T3  
**步骤：**
1. 新建路径解析组件。
2. 将模型输入路径解析为工作区内规范路径。
3. 拒绝 `..` 穿越、绝对路径逃逸和符号链接逃逸。

**验证：** 新增路径解析测试或运行 `mvn test -DskipTests`，期望非法路径被拒绝。

## T6: 定义并实现工具注册中心

**文件：** `src/main/java/com/lunacode/tool/ToolRegistry.java`、`src/main/java/com/lunacode/tool/DefaultToolRegistry.java`  
**依赖：** T1  
**步骤：**
1. 定义注册、启用、禁用、获取单个工具、获取启用工具的方法。
2. 实现重复工具名校验。
3. 实现 `toAPIFormat()`，输出 Claude `tools` 数组。

**验证：** 运行 `mvn test -Dtest=ToolRegistryTest`，期望工具启停和 Claude 格式输出正确。

## T7: 实现工具执行器

**文件：** `src/main/java/com/lunacode/tool/ToolExecutor.java`、`src/main/java/com/lunacode/tool/DefaultToolExecutor.java`  
**依赖：** T2、T3、T6  
**步骤：**
1. 定义 `execute(ToolUse toolUse)`。
2. 实现工具查找和禁用工具错误。
3. 调用 `validateInput()`。
4. 捕获普通异常并包装为 `ToolResult.isError=true`。

**验证：** 运行 `mvn test -Dtest=ToolRegistryTest`，期望未知工具和非法参数返回工具错误而非抛出程序异常。

## T8: 实现 ReadFile 工具 Schema 与校验

**文件：** `src/main/java/com/lunacode/tool/ReadFileTool.java`  
**依赖：** T1、T5  
**步骤：**
1. 新建 `ReadFileTool`，工具名为 `ReadFile`。
2. 实现 `inputSchema()`，包含 `path`、`offset`、`limit`。
3. 实现 `validateInput()`，校验 path 必填、offset 从 1 开始、limit 大于 0。

**验证：** 运行 `mvn test -Dtest=ReadFileToolTest`，期望非法参数返回校验错误。

## T9: 实现 ReadFile 行号与分页读取

**文件：** `src/main/java/com/lunacode/tool/ReadFileTool.java`、`src/test/java/com/lunacode/tool/ReadFileToolTest.java`  
**依赖：** T8  
**步骤：**
1. 读取工作区内文本文件。
2. 根据 `offset` 和 `limit` 截取指定行范围。
3. 在 `content` 中输出原始行号和文本。
4. 在 `metadata` 中写入路径、总行数、起止行、文件大小、修改时间和截断状态。

**验证：** 运行 `mvn test -Dtest=ReadFileToolTest`，期望指定 `offset=2, limit=2` 只返回第 2-3 行且保留原始行号。

## T10: 实现 WriteFile 工具

**文件：** `src/main/java/com/lunacode/tool/WriteFileTool.java`、`src/test/java/com/lunacode/tool/WriteFileToolTest.java`  
**依赖：** T1、T5  
**步骤：**
1. 新建 `WriteFileTool`，工具名为 `WriteFile`。
2. 校验 `path` 和 `content`。
3. 写入前递归创建父目录。
4. 使用临时文件写入后移动到目标路径。
5. POSIX 支持时设置目录 `0755` 和文件 `0644`，非 POSIX 写入 metadata 标记。

**验证：** 运行 `mvn test -Dtest=WriteFileToolTest`，期望嵌套目录被创建、文件内容正确、metadata 包含写入信息。

## T11: 实现 EditFile 工具

**文件：** `src/main/java/com/lunacode/tool/EditFileTool.java`、`src/test/java/com/lunacode/tool/EditFileToolTest.java`  
**依赖：** T5、T10  
**步骤：**
1. 新建 `EditFileTool`，工具名为 `EditFile`。
2. 校验 `path`、`old_text`、`new_text`。
3. 统计 `old_text` 在文件中的出现次数。
4. 出现一次时替换并复用安全写入策略。
5. 出现 0 次或多次时返回 `isError=true`，不修改文件。

**验证：** 运行 `mvn test -Dtest=EditFileToolTest`，期望唯一匹配成功，无匹配和多匹配都不修改文件。

## T12: 实现 Bash 工具

**文件：** `src/main/java/com/lunacode/tool/BashTool.java`、`src/test/java/com/lunacode/tool/BashToolTest.java`  
**依赖：** T4、T7  
**步骤：**
1. 新建 `BashTool`，工具名为 `Bash`。
2. 使用工作区根目录作为命令执行目录。
3. 支持默认超时和 `timeout_seconds`。
4. 收集 stdout、stderr、exit code、耗时。
5. 非零退出和超时返回 `isError=true`。

**验证：** 运行 `mvn test -Dtest=BashToolTest`，期望成功命令、非零退出和超时场景都返回结构化结果。

## T13: 实现 Glob 工具

**文件：** `src/main/java/com/lunacode/tool/GlobTool.java`、`src/test/java/com/lunacode/tool/GlobToolTest.java`  
**依赖：** T5  
**步骤：**
1. 新建 `GlobTool`，工具名为 `Glob`。
2. 校验 `pattern` 和可选 `limit`。
3. 使用 Java NIO 遍历工作区。
4. 支持 `**` 匹配任意层级子目录。
5. 返回排序后的相对路径列表。

**验证：** 运行 `mvn test -Dtest=GlobToolTest`，期望 `src/**/*.java` 能匹配多层目录下的 Java 文件。

## T14: 实现 Grep 工具

**文件：** `src/main/java/com/lunacode/tool/GrepTool.java`、`src/test/java/com/lunacode/tool/GrepToolTest.java`  
**依赖：** T5  
**步骤：**
1. 新建 `GrepTool`，工具名为 `Grep`。
2. 校验 `pattern`、可选 `path` 和 `limit`。
3. 遍历工作区内文本文件。
4. 返回文件路径、行号、列号和匹配行摘要。
5. 跳过明显二进制文件和构建产物目录。

**验证：** 运行 `mvn test -Dtest=GrepToolTest`，期望能搜索到目标文本并返回行号。

## T15: 注册六个内置工具

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`、`src/main/java/com/lunacode/tool/DefaultToolRegistry.java`  
**依赖：** T8、T10、T11、T12、T13、T14  
**步骤：**
1. 在应用启动装配中创建 `DefaultToolRegistry`。
2. 注册 `ReadFile`、`WriteFile`、`EditFile`、`Bash`、`Glob`、`Grep`。
3. 默认启用全部六个工具。

**验证：** 运行 `mvn test -Dtest=ToolRegistryTest`，期望启用工具列表包含六个指定名称。

## T16: 定义 Claude 内容块模型

**文件：** `src/main/java/com/lunacode/conversation/ContentBlock.java`、`src/main/java/com/lunacode/conversation/ApiMessage.java`  
**依赖：** T3  
**步骤：**
1. 新建 `ContentBlock` sealed interface。
2. 定义 `Text`、`ToolUseBlock`、`ToolResultBlock`。
3. 修改 `ApiMessage` 为 `role + List<ContentBlock>`。

**验证：** 运行 `mvn test -DskipTests`，期望消息模型编译通过。

## T17: 更新对话管理接口

**文件：** `src/main/java/com/lunacode/conversation/ConversationManager.java`  
**依赖：** T16  
**步骤：**
1. 增加添加普通 user text 消息的方法。
2. 增加添加 assistant 混合内容块消息的方法。
3. 增加添加 user tool_result 消息的方法。
4. 保留第一章已有接口的兼容入口。

**验证：** 运行 `mvn test -DskipTests`，期望接口引用编译通过。

## T18: 实现 tool_use/tool_result 历史保存

**文件：** `src/main/java/com/lunacode/conversation/DefaultConversationManager.java`、`src/test/java/com/lunacode/conversation/ToolMessageFormatTest.java`  
**依赖：** T17  
**步骤：**
1. 保存 assistant 同一条消息内的 text 和多个 tool_use。
2. 保存 user 同一条消息内的多个 tool_result。
3. 确保 `toAPIFormat()` 不拆散 assistant 的 text 和 tool_use。
4. 确保 tool_result 保持 user 角色。

**验证：** 运行 `mvn test -Dtest=ToolMessageFormatTest`，期望多 tool_use 和多 tool_result 的 id 配对正确。

## T19: 定义工具流式事件

**文件：** `src/main/java/com/lunacode/stream/StreamEvent.java`、`src/main/java/com/lunacode/stream/ToolUseBuffer.java`  
**依赖：** T3  
**步骤：**
1. 将文本增量统一为 `TextDelta`。
2. 增加 `ToolUse(id, name, input)` 事件。
3. 新建 `ToolUseBuffer` 累积 `partial_json`。

**验证：** 运行 `mvn test -DskipTests`，期望流事件模型编译通过。

## T20: 解析 Claude tool_use 流

**文件：** `src/main/java/com/lunacode/stream/AnthropicStreamMapper.java`、`src/test/java/com/lunacode/stream/ClaudeToolUseStreamMapperTest.java`  
**依赖：** T19  
**步骤：**
1. 在 `content_block_start` 且 `type=tool_use` 时初始化 buffer。
2. 在 `content_block_delta` 且 `type=input_json_delta` 时追加 `partial_json`。
3. 在 `content_block_stop` 时解析完整 JSON。
4. 解析成功时发出 `StreamEvent.ToolUse`。
5. 解析失败时发出程序级流解析错误事件。

**验证：** 运行 `mvn test -Dtest=ClaudeToolUseStreamMapperTest`，期望碎片 `{`、`"path"`、`: "/main.py"}` 能拼成完整 JSON。

## T21: 更新 Claude Provider 请求格式

**文件：** `src/main/java/com/lunacode/provider/ChatProvider.java`、`src/main/java/com/lunacode/provider/AnthropicProvider.java`  
**依赖：** T16、T20  
**步骤：**
1. 修改 `ChatProvider.streamChat` 接口，接收 `List<ApiMessage>` 和当前启用工具数组。
2. Anthropic 请求体支持 `tools` 字段。
3. 将 text、tool_use、tool_result 内容块转换为 Claude API 格式。
4. 工具列表为空时不发送 `tools`。

**验证：** 运行 Provider 请求体测试，期望包含工具声明的请求和不包含工具声明的请求都正确。

## T22: 更新编排器依赖注入

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/main/java/com/lunacode/app/LunaCodeApplication.java`  
**依赖：** T7、T15、T21  
**步骤：**
1. 给编排器注入 `ToolRegistry` 和 `ToolExecutor`。
2. 用户请求开始时调用 `ToolRegistry.toAPIFormat()`。
3. 最终回复请求使用空工具列表。

**验证：** 运行 `mvn test -DskipTests`，期望应用装配编译通过。

## T23: 编排普通无工具回复

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`  
**依赖：** T22  
**步骤：**
1. 更新编排器消费 `TextDelta`。
2. 没有 `ToolUse` 时保持第一章流式回复行为。
3. 完成 assistant 消息状态更新。

**验证：** 运行现有 `DefaultChatOrchestratorTest`，期望普通流式回复测试仍通过。

## T24: 编排单个工具调用

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/orchestrator/ToolOrchestratorTest.java`  
**依赖：** T18、T20、T22  
**步骤：**
1. 累积 assistant 文本内容块。
2. 收到 `ToolUse` 后把 text 和 tool_use 放入同一条 assistant 消息。
3. 调用 `ToolExecutor`。
4. 把结果作为 user tool_result 消息加入历史。
5. 第二次调用 Provider 获取最终回复。

**验证：** 运行 `mvn test -Dtest=ToolOrchestratorTest`，期望 ReadFile 工具结果被回灌，最终 assistant 回复基于工具内容生成。

## T25: 编排多个工具调用

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/orchestrator/ToolOrchestratorTest.java`  
**依赖：** T24  
**步骤：**
1. 支持同一 assistant 回复中的多个 `ToolUse`。
2. 按顺序执行多个工具。
3. 把多个 tool_result 放入同一条 user 消息。
4. 确保每个 result 的 `tool_use_id` 与原始 id 配对。

**验证：** 运行 `mvn test -Dtest=ToolOrchestratorTest`，期望 ReadFile 和 Grep 两个工具结果在同一条 user 消息中回灌。

## T26: 禁止工具回灌后的 Agent Loop

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`  
**依赖：** T24  
**步骤：**
1. 第二次 Provider 请求不携带工具列表。
2. 如果最终回复仍出现 `ToolUse`，不执行工具。
3. 在对话中给出本阶段不支持连环工具调用的可理解提示。

**验证：** 运行 `mvn test -Dtest=ToolOrchestratorTest`，期望第二次工具调用不会被执行。

## T27: 扩展状态栏快照

**文件：** `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java`  
**依赖：** T24  
**步骤：**
1. 增加当前工具名字段。
2. 增加工具状态字段。
3. 增加工具 metadata 摘要字段。

**验证：** 运行 `mvn test -DskipTests`，期望状态对象编译通过。

## T28: TUI 展示工具状态

**文件：** `src/main/java/com/lunacode/tui/LanternaLunaTui.java`  
**依赖：** T27  
**步骤：**
1. 状态栏显示 `tool_running`、`tool_done`、`tool_error`。
2. 显示当前工具名。
3. 显示 metadata 摘要，例如耗时、退出码、是否截断。
4. 不把完整 metadata 当作模型消息展示。

**验证：** 运行 TUI 相关测试或手动启动模拟 Provider，期望状态栏能看到工具执行状态。

## T29: 更新现有测试适配新消息模型

**文件：** `src/test/java/com/lunacode/conversation/DefaultConversationManagerTest.java`、`src/test/java/com/lunacode/orchestrator/DefaultChatOrchestratorTest.java`、现有 Provider 测试  
**依赖：** T16、T18、T23  
**步骤：**
1. 将旧的 `role + content` 断言改为内容块断言。
2. 保留普通对话的行为验证。
3. 修正 mock Provider 使用新的 `streamChat` 签名。

**验证：** 运行 `mvn test`，期望旧功能测试继续通过。

## T30: 全量编译和单元测试

**文件：** 全项目  
**依赖：** T1-T29  
**步骤：**
1. 运行 `mvn compile`。
2. 修复编译错误。
3. 运行 `mvn test`。
4. 修复失败测试。

**验证：** `mvn compile` 和 `mvn test` 均成功。

## T31: 打包验证

**文件：** 全项目  
**依赖：** T30  
**步骤：**
1. 运行 `mvn package`。
2. 确认生成可运行 jar。
3. 确认打包后入口仍为 `com.lunacode.app.Main`。

**验证：** `mvn package` 成功，`target` 目录生成 jar。

## T32: 端到端手动验收准备

**文件：** `config.example.yaml`、测试配置或模拟 Provider 配置  
**依赖：** T31  
**步骤：**
1. 准备可触发 `ReadFile` 的模拟或真实 Provider 配置。
2. 准备请求：“读取当前项目的 pom.xml 并总结依赖”。
3. 确认工具声明中包含六个启用工具。

**验证：** 启动前检查配置和工具注册日志或测试输出，期望工具可用。

## 执行顺序

```text
T1 -> T2 -> T3 -> T4 -> T5
             -> T6 -> T7
T7 -> T8 -> T9
   -> T10
   -> T11
   -> T12
   -> T13
   -> T14
T8-T14 -> T15
T16 -> T17 -> T18
T19 -> T20
T18 + T20 -> T21
T15 + T21 -> T22 -> T23 -> T24 -> T25 -> T26
T24 -> T27 -> T28
T16-T28 -> T29 -> T30 -> T31 -> T32
```
