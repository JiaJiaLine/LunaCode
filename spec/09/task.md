# LunaCode 斜杠命令注册与分发 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/command/SlashCommandType.java` | 定义命令执行类型 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandDefinition.java` | 保存命令元数据和处理器 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandHandler.java` | 定义命令处理函数接口 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandContext.java` | 向命令处理器传递调用参数、注册中心和运行时能力 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandInvocation.java` | 保存一次斜杠命令调用的解析结果 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandParseResult.java` | 表达输入是否为斜杠命令 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandName.java` | 表达补全候选名称和归属命令 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandCompletion.java` | 表达补全结果 |
| 新建 | `src/main/java/com/lunacode/command/DispatchResult.java` | 表达命令分发是否消费输入 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandRegistrationException.java` | 表达命令注册冲突或非法定义 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandParser.java` | 解析斜杠输入、归一化命令名和参数 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandRegistry.java` | 管理命令注册、别名索引、冲突检测、帮助和补全顺序 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandCompleter.java` | 根据输入行和光标位置生成命令补全结果 |
| 新建 | `src/main/java/com/lunacode/command/CommandRuntime.java` | 定义命令可调用的运行时能力 |
| 新建 | `src/main/java/com/lunacode/command/CommandRuntimeStatus.java` | 为 `/status` 提供格式化所需状态 |
| 新建 | `src/main/java/com/lunacode/command/CommandUiController.java` | 抽象命令需要的界面控制能力 |
| 新建 | `src/main/java/com/lunacode/command/SlashCommandDispatcher.java` | 执行命令分发、忙碌拦截、未知命令提示和 `/cancel` 优先级 |
| 新建 | `src/main/java/com/lunacode/command/BuiltinSlashCommands.java` | 注册所有内置命令、别名、帮助、状态和提示词处理逻辑 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 启动阶段创建注册中心、分发器、补全器，并注入 orchestrator/TUI |
| 修改 | `src/main/java/com/lunacode/orchestrator/ChatOrchestrator.java` | 暴露命令补全和 UI controller 注入入口 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 接入命令分发，实现 `CommandRuntime`，迁移现有斜杠命令 |
| 修改 | `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java` | 增加 Agent 工作模式字段并保持构造兼容 |
| 修改 | `src/main/java/com/lunacode/permission/PermissionModeSession.java` | 调整 `modeFor(AgentMode)`，让权限模式不再被 `AgentMode.PLAN` 强制覆盖 |
| 修改 | `src/main/java/com/lunacode/tui/InputLineBuffer.java` | 增加命令 token 替换和整行替换能力 |
| 修改 | `src/main/java/com/lunacode/tui/LanternaLunaTui.java` | 处理 Tab 补全、临时候选菜单、清屏和状态栏模式标记 |
| 新建 | `src/test/java/com/lunacode/command/SlashCommandParserTest.java` | 覆盖命令解析行为 |
| 新建 | `src/test/java/com/lunacode/command/SlashCommandRegistryTest.java` | 覆盖注册、冲突、别名和可见命令顺序 |
| 新建 | `src/test/java/com/lunacode/command/SlashCommandCompleterTest.java` | 覆盖单匹配、多匹配、隐藏命令和参数区不补全 |
| 新建 | `src/test/java/com/lunacode/command/SlashCommandDispatcherTest.java` | 覆盖分发、忙碌拦截、未知命令和 `/cancel` 优先级 |
| 新建 | `src/test/java/com/lunacode/command/BuiltinSlashCommandsTest.java` | 覆盖内置命令元数据、别名、帮助、状态和 `/review` prompt |
| 新建 | `src/test/java/com/lunacode/orchestrator/SlashCommandOrchestratorTest.java` | 覆盖 orchestrator 中命令与普通消息的分流 |
| 修改 | `src/test/java/com/lunacode/orchestrator/PermissionCommandTest.java` | 迁移权限命令断言到注册中心后的行为 |
| 修改 | `src/test/java/com/lunacode/tui/InputLineBufferTest.java` | 覆盖新增输入行替换能力 |
| 新建 | `src/test/java/com/lunacode/tui/LanternaLunaTuiCommandCompletionTest.java` | 覆盖 TUI 补全菜单、清除和状态栏模式标记 |

## T1: 建立命令核心类型

**文件：** `src/main/java/com/lunacode/command/SlashCommandType.java`、`SlashCommandDefinition.java`、`SlashCommandHandler.java`、`SlashCommandContext.java`、`SlashCommandInvocation.java`、`SlashCommandParseResult.java`、`SlashCommandName.java`、`SlashCommandCompletion.java`、`DispatchResult.java`、`SlashCommandRegistrationException.java`

**依赖：** 无

**步骤：**
1. 新建 `com.lunacode.command` 包。
2. 按 `plan.md` 定义枚举、record、sealed interface 和函数式接口。
3. 在 `SlashCommandDefinition` 或注册阶段需要的字段上保留完整元数据，不写业务命令逻辑。
4. 确保公开类型命名和 `plan.md` 保持一致。

**验证：** 运行 `mvn -q -DskipTests compile`，期望编译通过。

## T2: 实现命令解析器

**文件：** `src/main/java/com/lunacode/command/SlashCommandParser.java`、`src/test/java/com/lunacode/command/SlashCommandParserTest.java`

**依赖：** T1

**步骤：**
1. 实现空输入、空白输入、非 `/` 输入返回 `NotCommand`。
2. 实现第一个空白前为命令名、后续内容 trim 后为参数。
3. 实现命令名小写归一化，并保留原始命令名。
4. 添加大小写、参数、未知命令形态和普通输入的单元测试。

**验证：** 运行 `mvn -q -Dtest=SlashCommandParserTest test`，期望全部通过。

## T3: 实现命令注册中心

**文件：** `src/main/java/com/lunacode/command/SlashCommandRegistry.java`、`src/test/java/com/lunacode/command/SlashCommandRegistryTest.java`

**依赖：** T1

**步骤：**
1. 使用稳定顺序索引保存主命令和别名。
2. 校验主名称和别名非空、以 `/` 开头、大小写不敏感。
3. 发现任意主名或别名冲突时抛出 `SlashCommandRegistrationException`，错误信息包含冲突名称和相关命令。
4. 实现 `find`、`require`、`visibleCommands`、`visibleNames`。
5. 添加冲突、别名查找、隐藏命令和稳定顺序测试。

**验证：** 运行 `mvn -q -Dtest=SlashCommandRegistryTest test`，期望全部通过。

## T4: 实现命令补全器

**文件：** `src/main/java/com/lunacode/command/SlashCommandCompleter.java`、`src/test/java/com/lunacode/command/SlashCommandCompleterTest.java`

**依赖：** T3

**步骤：**
1. 只在光标位于第一个 token 且 token 以 `/` 开头时尝试补全。
2. 从 `visibleNames` 中匹配非隐藏命令主名和别名。
3. 单个候选返回 `Single`，多个候选返回稳定顺序的 `Multiple`，没有候选返回 `NoMatch`。
4. 添加参数区不补全、隐藏命令不参与、`/pe` 单匹配、`/p` 多匹配测试。

**验证：** 运行 `mvn -q -Dtest=SlashCommandCompleterTest test`，期望全部通过。

## T5: 定义命令运行时并实现分发器

**文件：** `src/main/java/com/lunacode/command/CommandRuntime.java`、`CommandRuntimeStatus.java`、`CommandUiController.java`、`SlashCommandDispatcher.java`、`src/test/java/com/lunacode/command/SlashCommandDispatcherTest.java`

**依赖：** T2、T3

**步骤：**
1. 定义 `CommandRuntime`、`CommandRuntimeStatus` 和 `CommandUiController`。
2. 实现 `SlashCommandDispatcher` 的解析、查找、未知命令提示和 handler 调用。
3. 实现 `/cancel` 优先级：即使运行时忙碌或等待，也能执行 cancel handler。
4. 实现忙碌或等待时拦截非 `/cancel` 命令，并显示中文稍后再试提示。
5. 使用 fake runtime 添加分发测试，覆盖普通输入不消费、未知命令、忙碌拦截和取消优先级。

**验证：** 运行 `mvn -q -Dtest=SlashCommandDispatcherTest test`，期望全部通过。

## T6: 注册内置命令元数据和别名

**文件：** `src/main/java/com/lunacode/command/BuiltinSlashCommands.java`、`src/test/java/com/lunacode/command/BuiltinSlashCommandsTest.java`

**依赖：** T3、T5

**步骤：**
1. 实现 `BuiltinSlashCommands.registerAll(SlashCommandRegistry registry)`。
2. 注册 `/help`、`/compact`、`/clear`、`/plan`、`/do`、`/session`、`/memory`、`/permission`、`/status`、`/review`、`/cancel`。
3. 注册固定别名：`/h`、`/?`、`/cl`、`/cp`、`/pl`、`/d`、`/sess`、`/mem`、`/perm`、`/permissions`、`/st`、`/r`、`/x`。
4. 为每条命令补齐描述、用法、类型、参数提示和隐藏标记。
5. 添加测试确认所有主命令和别名都能被查到，且无冲突。

**验证：** 运行 `mvn -q -Dtest=BuiltinSlashCommandsTest test`，期望内置元数据测试通过。

## T7: 实现帮助、状态和 review 内置命令

**文件：** `src/main/java/com/lunacode/command/BuiltinSlashCommands.java`、`src/test/java/com/lunacode/command/BuiltinSlashCommandsTest.java`

**依赖：** T6

**步骤：**
1. 实现 `/help` 列表输出，包含非隐藏命令的主名称、别名、描述和用法。
2. 实现 `/help <命令>` 详情输出，支持主名称和别名查询。
3. 实现 `/status` 输出 Agent 模式、权限模式、provider、model、token、会话、记忆和运行状态。
4. 实现 `/review` prompt 生成，不带参数时发送默认审查提示词。
5. 实现 `/review <参数>` 追加“额外关注：...”并调用 `runtime.sendUserMessage`。
6. 添加帮助、状态和 review prompt 的 fake runtime 测试。

**验证：** 运行 `mvn -q -Dtest=BuiltinSlashCommandsTest test`，期望帮助、状态和 review 测试通过。

## T8: 实现本地动作和模式权限内置命令

**文件：** `src/main/java/com/lunacode/command/BuiltinSlashCommands.java`、`src/test/java/com/lunacode/command/BuiltinSlashCommandsTest.java`

**依赖：** T6

**步骤：**
1. 实现 `/cancel` 调用 `runtime.cancelCurrentRun`。
2. 实现 `/compact` 调用 `runtime.compactContext`。
3. 实现 `/clear` 调用 `runtime.clearVisibleScreen`。
4. 实现 `/session` 和 `/memory` 转调现有 raw input 处理入口。
5. 实现 `/plan` 调用 `runtime.enterPlanMode`，`/do` 调用 `runtime.enterDefaultMode`。
6. 实现 `/permission` 无参展示当前权限，有参切换合法权限，`bypassPermissions` 走危险确认入口，非法参数显示中文用法。
7. 添加各命令 handler 调用目标和别名行为测试。

**验证：** 运行 `mvn -q -Dtest=BuiltinSlashCommandsTest test`，期望全部内置命令 handler 测试通过。

## T9: 接入应用启动 wiring

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`、`src/main/java/com/lunacode/orchestrator/ChatOrchestrator.java`

**依赖：** T5、T6

**步骤：**
1. 在应用启动阶段创建 `SlashCommandRegistry` 并调用 `BuiltinSlashCommands.registerAll`。
2. 创建 `SlashCommandParser`、`SlashCommandDispatcher` 和 `SlashCommandCompleter`。
3. 扩展 `ChatOrchestrator`，暴露 `completeSlashCommand` 和 `setCommandUiController`。
4. 将 dispatcher 和 completer 注入 `DefaultChatOrchestrator`。
5. 在 TUI 创建后把 TUI 作为 `CommandUiController` 注入 orchestrator。

**验证：** 运行 `mvn -q -DskipTests compile`，期望应用 wiring 编译通过，注册冲突会在启动路径暴露。

## T10: 迁移 orchestrator 的斜杠命令入口

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/orchestrator/SlashCommandOrchestratorTest.java`

**依赖：** T9

**步骤：**
1. 在 `submitUserMessage` 入口优先调用 dispatcher。
2. 当分发结果为 `HANDLED` 时不再把原始命令文本写入 conversation 或发送给 Agent。
3. 当分发结果为 `NOT_COMMAND` 时继续走现有普通消息、用户问题回答和权限确认流程。
4. 移除或旁路原来分散的 `/compact`、`/session`、`/memory`、`/permissions`、`/cancel`、`/plan`、`/do` 分支。
5. 添加测试确认普通输入进入 Agent，已注册命令不进入 Agent，未知命令输出 `/help` 引导。

**验证：** 运行 `mvn -q -Dtest=SlashCommandOrchestratorTest test`，期望分流行为通过。

## T11: 实现 CommandRuntime 在 orchestrator 中的业务桥接

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/orchestrator/SlashCommandOrchestratorTest.java`、`src/test/java/com/lunacode/orchestrator/PermissionCommandTest.java`

**依赖：** T10

**步骤：**
1. 让 `DefaultChatOrchestrator` 实现 `CommandRuntime`。
2. 将 `showInfo`、`showWarning`、`showError` 写入本地可见消息并请求渲染。
3. 将 `cancelCurrentRun`、`compactContext`、`sendUserMessage` 映射到现有能力。
4. 将 `runSessionCommand` 和 `runMemoryCommand` 转调现有 handler，保持原有语义。
5. 将 `requestDangerousPermissionMode` 接入现有二次确认流程。
6. 添加测试确认 `/compact`、`/session`、`/memory`、`/permission` 和 `/cancel` 迁移后行为兼容。

**验证：** 运行 `mvn -q -Dtest=SlashCommandOrchestratorTest,PermissionCommandTest test`，期望命令桥接测试通过。

## T12: 实现 Agent 模式与权限联动

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/main/java/com/lunacode/permission/PermissionModeSession.java`、`src/test/java/com/lunacode/orchestrator/SlashCommandOrchestratorTest.java`、`src/test/java/com/lunacode/permission/PermissionModePolicyTest.java`

**依赖：** T11

**步骤：**
1. 在 orchestrator 中记录当前 Agent 模式、进入 `/plan` 前的权限模式和 Plan 期间权限是否被手动修改。
2. 实现 `/plan` 只切 Agent 模式为 `PLAN`，同步权限到 `plan`，不发送 Agent 消息。
3. 实现 `/do` 切 Agent 模式为 `DEFAULT`，根据是否手动修改权限决定是否恢复旧权限。
4. 在手动执行 `/permission <mode>` 时标记 Plan 期间权限已被修改。
5. 调整 `PermissionModeSession.modeFor(AgentMode)`，返回当前权限模式。
6. 添加 `/plan`、`/do`、Plan 期间手动改权限的测试。

**验证：** 运行 `mvn -q -Dtest=SlashCommandOrchestratorTest,PermissionModePolicyTest test`，期望模式联动测试通过。

## T13: 扩展状态快照和状态栏模式标记

**文件：** `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java`、`src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/main/java/com/lunacode/tui/LanternaLunaTui.java`、`src/test/java/com/lunacode/tui/LanternaLunaTuiStatusContextTest.java`

**依赖：** T12

**步骤：**
1. 在 `StatusSnapshot` 增加 `AgentMode agentMode` 字段。
2. 保留兼容构造器，避免既有测试因参数列表一次性破裂。
3. orchestrator 的 `status` 和 `CommandRuntimeStatus` 都返回当前 Agent 模式。
4. TUI 状态栏展示 `[DEFAULT]` 或 `[PLAN]`，并继续展示现有权限模式和 token 信息。
5. 添加状态栏渲染测试，确认模式切换后输出包含对应标记。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiStatusContextTest,SlashCommandOrchestratorTest test`，期望状态模式测试通过。

## T14: 实现输入行补全基础能力

**文件：** `src/main/java/com/lunacode/tui/InputLineBuffer.java`、`src/test/java/com/lunacode/tui/InputLineBufferTest.java`

**依赖：** T4

**步骤：**
1. 实现 `replaceCommandToken(String replacement)`，只替换第一个 token 并移动光标到 token 末尾。
2. 实现 `replaceAll(String value)`，用于清屏后清空当前输入。
3. 保持现有输入、删除、移动光标行为不回退。
4. 添加命令 token 替换、带参数输入、光标移动和整行替换测试。

**验证：** 运行 `mvn -q -Dtest=InputLineBufferTest test`，期望输入行测试通过。

## T15: 实现 TUI Tab 补全和临时候选菜单

**文件：** `src/main/java/com/lunacode/tui/LanternaLunaTui.java`、`src/test/java/com/lunacode/tui/LanternaLunaTuiCommandCompletionTest.java`

**依赖：** T9、T14

**步骤：**
1. 在 TUI key loop 中识别 Tab 键，调用 `orchestrator.completeSlashCommand`。
2. `Single` 时替换命令 token，`Multiple` 时显示临时候选菜单，`NoMatch` 时保持输入不变。
3. 在继续输入、再次 Tab、回车提交和取消输入时清除旧候选菜单。
4. 确保候选菜单不写入 conversation，也不污染后续输入行。
5. 添加单匹配、多匹配、菜单清理和隐藏命令不显示测试。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiCommandCompletionTest test`，期望 TUI 补全测试通过。

## T16: 实现 `/clear` 的 TUI 可见输出清理

**文件：** `src/main/java/com/lunacode/tui/LanternaLunaTui.java`、`src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/tui/LanternaLunaTuiCommandCompletionTest.java`

**依赖：** T11、T15

**步骤：**
1. 让 `LanternaLunaTui` 实现 `CommandUiController.clearVisibleScreen`。
2. 清空当前输入行和临时候选菜单。
3. 发送终端清屏控制并请求重绘状态栏。
4. 保留已渲染消息集合和 conversation 历史，避免下一次 render 重刷旧消息。
5. 添加测试确认 clear 不删除历史消息状态，只影响可见输出和输入行。

**验证：** 运行 `mvn -q -Dtest=LanternaLunaTuiCommandCompletionTest,SlashCommandOrchestratorTest test`，期望清屏行为测试通过。

## T17: 补齐内置命令兼容回归测试

**文件：** `src/test/java/com/lunacode/orchestrator/SlashCommandOrchestratorTest.java`、`src/test/java/com/lunacode/orchestrator/PermissionCommandTest.java`、既有 session/memory/orchestrator 测试文件

**依赖：** T11、T12、T16

**步骤：**
1. 补齐 `/session current|list|new|resume <id>` 迁移后的兼容测试。
2. 补齐 `/memory`、`/memory list`、`/memory on`、`/memory off`、`/memory delete <id>` 兼容测试。
3. 补齐 `/permissions` 作为 `/permission` 别名的兼容测试。
4. 补齐忙碌或等待状态下非 `/cancel` 命令被拦截、普通输入仍回答问题或权限确认的测试。
5. 确认现有测试中不再依赖旧硬编码命令分支。

**验证：** 运行 `mvn -q -Dtest=DefaultChatOrchestratorTest,DefaultChatOrchestratorCompactTest,DefaultChatOrchestratorMemoryTest,PermissionCommandTest,SlashCommandOrchestratorTest test`，期望相关回归测试通过。

## T18: 运行完整自动化回归

**文件：** 全项目

**依赖：** T1-T17

**步骤：**
1. 运行全量单元测试。
2. 运行 diff 空白检查。
3. 修复失败测试或空白错误。
4. 记录关键命令输出，为后续 checklist 验收提供证据。

**验证：** 运行 `mvn test` 和 `git diff --check`，期望全部通过。

## T19: 按 checklist 执行 tmux 端到端验收

**文件：** `spec/09/checklist.md`、运行中的 LunaCode

**依赖：** T18、已批准的 `checklist.md`

**步骤：**
1. 在 tmux 中启动 LunaCode。
2. 输入真实对话请求，确认普通输入仍进入 Agent。
3. 输入 `/help`、`/status`、`/plan`、`/do`、`/review`、`/clear` 等关键命令。
4. 使用 Tab 验证单匹配补全、多匹配候选菜单和菜单清除。
5. 对照 `checklist.md` 逐项记录通过或失败证据。

**验证：** tmux 端到端场景可观察到 checklist 要求的行为，失败项修复后重新执行对应检查。

## 执行顺序

```text
T1
 -> T2
 -> T3
 -> T4
 -> T5
 -> T6
 -> T7
 -> T8
 -> T9
 -> T10
 -> T11
 -> T12
 -> T13
 -> T14
 -> T15
 -> T16
 -> T17
 -> T18
 -> T19
```
