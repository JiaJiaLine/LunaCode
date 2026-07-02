# LunaCode 斜杠命令注册与分发 Checklist

> 每一项通过运行代码或观察行为来验证，聚焦系统行为。验收时先记录实际输出或现象，再判断是否通过。

## 实现完整性

- [ ] 命令注册中心能注册内置命令，且主名称和别名冲突会在启动或注册阶段失败并显示清晰错误（验证：运行 `mvn -q -Dtest=SlashCommandRegistryTest,BuiltinSlashCommandsTest test`，观察冲突测试通过且错误信息包含冲突命令名）
- [ ] 命令名和别名大小写不敏感，`/HELP test`、`/Help test`、`/help test` 的命令名匹配一致，参数均为 `test`（验证：运行 `mvn -q -Dtest=SlashCommandParserTest test`，观察大小写和参数解析用例通过）
- [ ] 空输入、空白输入、非 `/` 开头输入不会进入命令处理（验证：运行 `mvn -q -Dtest=SlashCommandParserTest,SlashCommandDispatcherTest test`，观察非命令输入返回普通消息路径）
- [ ] 未知斜杠命令会显示中文未知命令提示，并包含 `/help` 引导（验证：运行 `mvn -q -Dtest=SlashCommandDispatcherTest,SlashCommandOrchestratorTest test`，观察未知命令断言通过）
- [ ] 已注册斜杠命令不会作为普通用户消息写入对话或直接发送给 Agent（验证：运行 `mvn -q -Dtest=SlashCommandOrchestratorTest test`，观察命令分流测试通过）
- [ ] 普通非命令输入仍会进入正常 Agent 对话流程（验证：运行 `mvn -q -Dtest=SlashCommandOrchestratorTest test`，观察普通输入触发 Agent 的测试通过）
- [ ] `/cancel` 和 `/x` 在忙碌、等待用户问题、等待权限确认或等待危险权限确认时仍优先取消当前状态（验证：运行 `mvn -q -Dtest=SlashCommandDispatcherTest,SlashCommandOrchestratorTest test`，观察取消优先级用例通过）
- [ ] 除 `/cancel` 外，其他命令在忙碌或等待状态下不执行业务逻辑，并显示中文稍后再试提示（验证：运行 `mvn -q -Dtest=SlashCommandDispatcherTest,SlashCommandOrchestratorTest test`，观察忙碌拦截用例通过）
- [ ] `/help` 输出所有非隐藏命令的主名、别名、描述和用法，且 `/help review` 能显示 `/review` 详情（验证：运行 `mvn -q -Dtest=BuiltinSlashCommandsTest test`，观察帮助输出断言通过）
- [ ] 隐藏命令不出现在 `/help` 列表和 Tab 补全候选中，但准确输入隐藏命令仍可执行（验证：运行 `mvn -q -Dtest=SlashCommandRegistryTest,SlashCommandCompleterTest test`，观察隐藏命令用例通过）
- [ ] `/clear` 只清空终端可见输出和当前输入行，不删除会话历史或 Agent 上下文（验证：运行 `mvn -q -Dtest=LanternaLunaTuiCommandCompletionTest,SlashCommandOrchestratorTest test`，观察清屏和历史保留断言通过）
- [ ] `/compact` 空闲时触发手动上下文压缩并显示中文结果摘要，忙碌或等待时提示稍后再试（验证：运行 `mvn -q -Dtest=DefaultChatOrchestratorCompactTest,SlashCommandOrchestratorTest test`，观察手动压缩和忙碌拦截用例通过）
- [ ] `/plan` 只进入 `[PLAN]`，权限模式同步切到 `plan`，且不会向 Agent 发送预设消息（验证：运行 `mvn -q -Dtest=SlashCommandOrchestratorTest test`，观察模式、权限和 Agent 消息断言通过）
- [ ] `/do` 只回到 `[DEFAULT]`，并在 Plan 期间未手动改权限时恢复进入 `/plan` 前的权限模式（验证：运行 `mvn -q -Dtest=SlashCommandOrchestratorTest test`，观察权限恢复用例通过）
- [ ] 在 `[PLAN]` 期间执行 `/permission acceptEdits` 后再执行 `/do`，Agent 模式回到 `[DEFAULT]`，权限仍保持 `acceptEdits`（验证：运行 `mvn -q -Dtest=SlashCommandOrchestratorTest test`，观察手动权限保留用例通过）
- [ ] 状态栏在默认模式显示 `[DEFAULT]`，执行 `/plan` 后显示 `[PLAN]`，执行 `/do` 后恢复 `[DEFAULT]`（验证：运行 `mvn -q -Dtest=LanternaLunaTuiStatusContextTest,SlashCommandOrchestratorTest test`，观察状态栏断言通过）
- [ ] `/session current`、`/session list`、`/session new`、`/session resume <id>` 保持迁移前语义，忙碌状态下保护会话切换类操作（验证：运行 `mvn -q -Dtest=DefaultChatOrchestratorTest,SlashCommandOrchestratorTest test`，观察 session 兼容用例通过）
- [ ] `/memory`、`/memory list`、`/memory on`、`/memory off`、`/memory delete <id>` 保持迁移前语义（验证：运行 `mvn -q -Dtest=MemoryCommandHandlerTest,DefaultChatOrchestratorMemoryTest,SlashCommandOrchestratorTest test`，观察 memory 兼容用例通过）
- [ ] `/permission` 无参显示当前权限，`/permission default` 能切换默认权限，`/permissions default` 与 `/permission default` 行为一致（验证：运行 `mvn -q -Dtest=PermissionCommandTest,SlashCommandOrchestratorTest test`，观察权限显示、切换和别名用例通过）
- [ ] `/permission bypassPermissions` 显示明确危险确认，确认后才切换，拒绝后保持原权限模式（验证：运行 `mvn -q -Dtest=PermissionCommandTest,SlashCommandOrchestratorTest test`，观察二次确认用例通过）
- [ ] `/status` 输出包含 Agent 工作模式、权限模式、provider、model、输入 token、输出 token、会话短 ID、记忆状态和当前运行状态（验证：运行 `mvn -q -Dtest=BuiltinSlashCommandsTest,SlashCommandOrchestratorTest test`，观察状态输出字段断言通过）
- [ ] `/review` 发送给 Agent 的用户消息包含“请审查当前 git diff 中的代码变更”，并包含逻辑错误、安全问题、性能问题和代码风格四个关注点（验证：运行 `mvn -q -Dtest=BuiltinSlashCommandsTest,SlashCommandOrchestratorTest test`，观察默认 review prompt 断言通过）
- [ ] `/review 并重点看异常处理` 发送给 Agent 的用户消息额外包含“额外关注：并重点看异常处理”（验证：运行 `mvn -q -Dtest=BuiltinSlashCommandsTest,SlashCommandOrchestratorTest test`，观察带参数 review prompt 断言通过）
- [ ] 所有固定短别名都能执行对应主命令，且帮助和补全能展示这些别名（验证：运行 `mvn -q -Dtest=BuiltinSlashCommandsTest,SlashCommandCompleterTest test`，观察别名覆盖用例通过）
- [ ] 输入 `/pe` 后按 Tab 能单匹配补全为 `/permission` 或唯一匹配项，输入 `/p` 后按 Tab 能显示包含 `/plan`、`/permission` 的候选菜单（验证：运行 `mvn -q -Dtest=SlashCommandCompleterTest,LanternaLunaTuiCommandCompletionTest test`，观察单匹配和多匹配用例通过）
- [ ] 多匹配候选菜单出现后，继续输入、再次按 Tab、回车提交或取消输入时，候选菜单被清除且输入行正确重绘（验证：运行 `mvn -q -Dtest=LanternaLunaTuiCommandCompletionTest test`，观察菜单清除用例通过）

## 集成

- [ ] 应用启动路径创建注册中心、注册内置命令、创建分发器和补全器，并注入 orchestrator 与 TUI（验证：运行 `mvn -q -DskipTests compile`，观察启动 wiring 编译通过；运行 `mvn -q -Dtest=SlashCommandOrchestratorTest test`，观察命令入口可用）
- [ ] 命令实现只通过运行时和界面控制接口访问业务能力，不直接依赖具体 TUI 渲染实现（验证：运行 `mvn -q -Dtest=PackageDependencyTest test` 或检查现有架构测试结果，观察无新增非法包依赖）
- [ ] `DefaultChatOrchestrator` 作为统一输入入口，命令输入走本地分发，非命令输入走原 Agent 流程（验证：运行 `mvn -q -Dtest=SlashCommandOrchestratorTest,DefaultChatOrchestratorTest test`，观察分流和原流程测试通过）
- [ ] `StatusSnapshot`、`/status` 和状态栏使用同一 Agent 模式来源，模式切换后两处展示一致（验证：运行 `mvn -q -Dtest=BuiltinSlashCommandsTest,LanternaLunaTuiStatusContextTest test`，观察状态一致性断言通过）
- [ ] `PermissionModeSession` 在 `[PLAN]` 下使用当前权限模式，Plan 期间手动切权限会影响后续 Agent 运行权限（验证：运行 `mvn -q -Dtest=SlashCommandOrchestratorTest,PermissionModePolicyTest test`，观察权限联动用例通过）
- [ ] `SessionCommandHandler` 和 `MemoryCommandHandler` 由命令注册中心转调后，用户可见行为保持兼容（验证：运行 `mvn -q -Dtest=DefaultChatOrchestratorMemoryTest,MemoryCommandHandlerTest,DefaultChatOrchestratorTest test`，观察既有回归测试通过）
- [ ] TUI 的 Tab 补全只消费命令名候选，不把候选菜单写入 conversation 或普通终端历史输出（验证：运行 `mvn -q -Dtest=LanternaLunaTuiCommandCompletionTest,SlashCommandOrchestratorTest test`，观察补全菜单隔离用例通过）
- [ ] `/review` 生成的 prompt 进入正常 Agent 提交流程，后续工具调用和权限策略与普通用户请求一致（验证：运行 `mvn -q -Dtest=SlashCommandOrchestratorTest test`，观察 review 触发普通 Agent 提交路径）

## 编译与测试

- [ ] 项目主代码编译无错误（验证：运行 `mvn -q -DskipTests compile`，期望退出码为 0）
- [ ] 命令核心单元测试全部通过（验证：运行 `mvn -q -Dtest=SlashCommandParserTest,SlashCommandRegistryTest,SlashCommandCompleterTest,SlashCommandDispatcherTest,BuiltinSlashCommandsTest test`，期望退出码为 0）
- [ ] orchestrator 相关命令回归测试全部通过（验证：运行 `mvn -q -Dtest=SlashCommandOrchestratorTest,PermissionCommandTest,DefaultChatOrchestratorTest,DefaultChatOrchestratorCompactTest,DefaultChatOrchestratorMemoryTest test`，期望退出码为 0）
- [ ] TUI 输入、补全、清屏和状态栏相关测试全部通过（验证：运行 `mvn -q -Dtest=InputLineBufferTest,LanternaLunaTuiCommandCompletionTest,LanternaLunaTuiStatusContextTest test`，期望退出码为 0）
- [ ] 全量测试通过（验证：运行 `mvn test`，期望退出码为 0）
- [ ] diff 空白检查通过（验证：运行 `git diff --check`，期望无输出且退出码为 0）

## 端到端场景

- [ ] 场景 1：在 tmux 中启动 LunaCode，输入一段普通真实对话请求，观察请求进入 Agent 并产生回复（验证：tmux 窗口中可看到普通对话不被命令分发拦截）
- [ ] 场景 2：输入 `/help`，观察输出包含 `/help`、`/compact`、`/clear`、`/plan`、`/do`、`/session`、`/memory`、`/permission`、`/status`、`/review`、`/cancel` 及固定别名（验证：tmux 中帮助列表完整且为中文说明）
- [ ] 场景 3：输入 `/does-not-exist`，观察 LunaCode 显示中文未知命令提示并引导使用 `/help`（验证：tmux 中没有触发 Agent 请求）
- [ ] 场景 4：输入 `/status`，观察输出包含 `[DEFAULT]` 或 `[PLAN]`、权限模式、provider、model、token、session、memory 和运行状态（验证：tmux 中字段完整可读）
- [ ] 场景 5：输入 `/plan`，观察状态栏显示 `[PLAN]` 且权限模式为 `plan`；随后输入 `/do`，观察状态栏恢复 `[DEFAULT]`（验证：tmux 状态栏变化符合预期）
- [ ] 场景 6：输入 `/plan`、再输入 `/permission acceptEdits`、再输入 `/do`，观察 Agent 模式为 `[DEFAULT]` 且权限模式仍为 `acceptEdits`（验证：tmux 状态栏或 `/status` 输出符合预期）
- [ ] 场景 7：输入 `/review 并重点看异常处理`，观察 LunaCode 发送审查当前 git diff 的预设 prompt 给 Agent，且包含“额外关注：并重点看异常处理”（验证：tmux 中 Agent 开始按代码审查请求工作）
- [ ] 场景 8：输入 `/pe` 后按 Tab，观察输入行补全为 `/permission` 或唯一候选；输入 `/p` 后按 Tab，观察出现包含 `/plan`、`/permission` 的候选菜单（验证：tmux 中补全行为可见）
- [ ] 场景 9：出现多匹配候选菜单后继续输入、再次按 Tab、回车或取消，观察旧候选菜单消失，输入行没有重叠或堆积（验证：tmux 中可见区域整洁）
- [ ] 场景 10：输入 `/clear`，观察终端可见输出和当前输入被清空；随后输入普通问题，观察 Agent 仍能基于之前会话上下文回复（验证：tmux 中清屏不等于清上下文）
- [ ] 场景 11：在 Agent 忙碌或等待权限确认时输入 `/status`，观察稍后再试提示；输入 `/cancel` 或 `/x`，观察当前等待或运行被取消（验证：tmux 中非 cancel 命令被保护，cancel 可用）
- [ ] 场景 12：输入 `/permission bypassPermissions`，观察危险确认提示；拒绝时权限不变，确认时才切换到 `bypassPermissions`（验证：tmux 中两条路径都可观察）
