# Agent Hook 自动化 Checklist

> 每一项通过运行代码或观察行为来验证，聚焦系统行为。四份文档全部通过审批前，不进入实现。

## 实现完整性

- [ ] Hook 规则支持 `event`、可选 `if`、`action` 三要素；省略 `if` 时无条件触发（验证：运行 `mvn -Dtest=HookValidatorTest,DefaultHookRuntimeTest test`，观察无条件 Hook 被执行）
- [ ] 三层配置来源会追加合并：项目级 `.lunacode/config.yaml`、用户级 `~/.lunacode/config.yaml`、本地级 `.lunacode/config.local.yaml`（验证：运行 `mvn -Dtest=HookConfigLoaderTest test`，观察三个来源的 Hook 都进入配置）
- [ ] 多个命中的 Hook 按项目级、用户级、本地级顺序执行，同一文件内按声明顺序执行（验证：运行 `mvn -Dtest=HookConfigLoaderTest,DefaultHookRuntimeTest test`，观察执行记录顺序稳定）
- [ ] Hook 配置会集中校验，任一规则非法时启动失败并一次性报告全部错误（验证：运行 `mvn -Dtest=HookValidatorTest,HookApplicationWiringTest test`，观察错误列表包含所有非法规则）
- [ ] 会话级事件 `session_start`、`session_end` 能在会话开始和结束时触发（验证：运行 `mvn -Dtest=HookApplicationWiringTest test`，观察对应事件日志）
- [ ] 轮次级事件 `turn_start`、`turn_end` 能在 Agent 每轮开始和结束时触发（验证：运行 `mvn -Dtest=AgentHookIntegrationTest test`，观察事件顺序包含 turn_start 和 turn_end）
- [ ] 消息级事件 `pre_send`、`post_receive` 能在请求 LLM 前和收到响应后触发（验证：运行 `mvn -Dtest=AgentHookIntegrationTest,HookPromptIntegrationTest test`，观察两个事件都被记录）
- [ ] 工具级事件 `pre_tool_use`、`post_tool_use` 能在工具执行前后触发（验证：运行 `mvn -Dtest=AgentHookIntegrationTest test`，观察工具事件包含工具名和参数）
- [ ] 系统级事件 `startup`、`shutdown`、`error`、`compact`、`permission_request`、`file_change`、`command_execute` 都能在对应节点触发（验证：运行 `mvn -Dtest=HookApplicationWiringTest,ContextHookIntegrationTest,SlashCommandHookTest,AgentHookIntegrationTest test`，观察所有事件都有覆盖）
- [ ] `file_change` 只在 LunaCode 文件工具成功修改文件后触发，外部编辑器修改文件不会触发（验证：端到端场景中先外部修改文件再由 Agent 写文件，观察只有 Agent 写文件产生 file_change 日志）
- [ ] 条件上下文暴露 `eventName`、`toolName`、`toolArgs`、`filePath`、`message`、`error`，并支持 `tool`、`args.<key>` 别名（验证：运行 `mvn -Dtest=HookConditionEvaluatorTest test`，观察字段和别名都能匹配）
- [ ] 条件操作符 `==`、`!=`、`=~`、`~=` 分别符合精确、反向、正则、glob 语义（验证：运行 `mvn -Dtest=HookConditionEvaluatorTest test`，观察四类匹配用例通过）
- [ ] 条件组合 `&&` 要求全部满足，`||` 要求任一满足（验证：运行 `mvn -Dtest=HookConditionParserTest,HookConditionEvaluatorTest test`，观察 AND 和 OR 用例通过）
- [ ] 条件表达式混用 `&&` 和 `||` 时配置校验失败（验证：运行 `mvn -Dtest=HookValidatorTest test`，观察混用表达式被拒绝）
- [ ] 条件解析只接受 `field operator value` 子条件格式，格式错误时给出可诊断错误（验证：运行 `mvn -Dtest=HookConditionParserTest,HookValidatorTest test`，观察错误信息包含表达式位置）
- [ ] command 动作复用 Bash 工具执行环境，遵守工作目录、超时、输出截断、敏感信息脱敏和沙箱策略，且不弹出权限确认（验证：运行 `mvn -Dtest=BashToolTest,CommandHookActionExecutorTest test`，并在端到端场景中观察 Hook 命令执行时没有权限弹窗）
- [ ] command 动作能通过环境变量读取 `EVENT_NAME`、`TOOL_NAME`、`FILE_PATH`、`MESSAGE`、`ERROR`、`ARGS_<KEY>`（验证：运行 `mvn -Dtest=CommandHookActionExecutorTest test`，观察命令输出包含预期环境变量）
- [ ] prompt 动作会作为 system reminder 注入，不改写用户原文或模型回复原文（验证：运行 `mvn -Dtest=HookPromptIntegrationTest,MessageChannelBuilderTest test`，观察用户消息文本保持不变且 system reminder 出现）
- [ ] `pre_send` 触发的 prompt 能影响当前请求，其它事件触发的 prompt 影响下一次请求（验证：运行 `mvn -Dtest=HookPromptIntegrationTest,HookReminderStoreTest test`，观察 reminder 可见轮次正确）
- [ ] HTTP 动作支持 `url`、`method`、`headers`、`body`、`timeout_ms`，并能引用 Hook 上下文变量（验证：运行 `mvn -Dtest=HttpHookActionExecutorTest test`，观察请求字段和变量替换结果正确）
- [ ] sub_agent 动作配置能通过校验，命中后只写未实现日志，不启动真实子 Agent（验证：运行 `mvn -Dtest=HookValidatorTest,DefaultHookRuntimeTest test`，观察日志包含未实现状态且没有子 Agent 运行副作用）
- [ ] command 和 HTTP 动作默认只写日志，不进入对话上下文（验证：运行 `mvn -Dtest=DefaultHookRuntimeTest,HookPromptIntegrationTest test`，观察无 `inject_result` 时没有新增 system reminder）
- [ ] command 和 HTTP 动作配置 `inject_result: true` 后，输出会作为 system reminder 注入模型上下文（验证：运行 `mvn -Dtest=DefaultHookRuntimeTest,HookPromptIntegrationTest test`，观察输出进入 system reminder）
- [ ] `pre_tool_use` 上配置 `reject: true` 会同步执行 Hook、取消原工具调用，并把 Hook 输出作为工具错误反馈给 Agent（验证：运行 `mvn -Dtest=AgentHookIntegrationTest,DefaultHookRuntimeTest test`，观察工具未执行且错误结果包含拒绝原因）
- [ ] `reject: true` 的动作失败或无输出时，原工具调用仍被取消，并返回兜底拒绝原因（验证：运行 `mvn -Dtest=DefaultHookRuntimeTest,AgentHookIntegrationTest test`，观察失败和空输出场景都拒绝）
- [ ] 非 `pre_tool_use` 事件配置 `reject` 会启动校验失败（验证：运行 `mvn -Dtest=HookValidatorTest test`，观察非法 reject 被报告）
- [ ] 拦截类 Hook 不允许异步执行，其它 Hook 可配置后台异步执行（验证：运行 `mvn -Dtest=HookValidatorTest,DefaultHookRuntimeTest test`，观察 reject+async 被拒绝，普通 async 可执行）
- [ ] command 和 HTTP 动作都能应用超时配置（验证：运行 `mvn -Dtest=CommandHookActionExecutorTest,HttpHookActionExecutorTest test`，观察超时场景返回失败结果并写日志）
- [ ] `once` Hook 在同一进程、同一会话中只执行一次，重启或恢复会话后可重新执行（验证：运行 `mvn -Dtest=DefaultHookRuntimeTest test`，并在端到端重启场景中观察 once 可重新执行）
- [ ] 异步 Hook 失败只写 Hook 日志，不影响 Agent 回复、不进入对话、不更新状态栏（验证：运行 `mvn -Dtest=DefaultHookRuntimeTest,AgentHookIntegrationTest test`，并在端到端场景观察对话继续）
- [ ] Hook 日志按会话写入 `.lunacode/tmp/hooks/<sessionId>.log`，包含命中、执行、失败、跳过和拦截原因（验证：运行 Hook 集成测试后查看临时会话日志，观察对应条目存在）

## 集成

- [ ] `HookConfigLoader` 与应用启动流程集成，配置合法时应用能继续启动，配置非法时应用失败退出（验证：运行 `mvn -Dtest=HookApplicationWiringTest test`，观察两类启动结果）
- [ ] `NoOpHookRuntime` 在无 Hook 配置时保持现有 Agent 行为不变（验证：运行 `mvn -Dtest=LunaCodeApplicationTest,DefaultAgentLoopTest,AgentToolRunnerTest test`，观察原有测试通过）
- [ ] `DefaultAgentLoop` 发射事件时不负责条件判断和动作执行，只传递上下文给 HookRuntime（验证：运行 `mvn -Dtest=AgentHookIntegrationTest test`，观察 runtime spy 收到事件）
- [ ] `AgentToolRunner` 在 `pre_tool_use reject` 后跳过权限审批和真实工具执行（验证：运行 `mvn -Dtest=AgentHookIntegrationTest,AgentToolRunnerPermissionTest test`，观察权限网关和工具执行 spy 未被调用）
- [ ] `AgentToolRunner` 在普通工具成功后触发 `post_tool_use`，在文件工具成功后额外触发 `file_change`（验证：运行 `mvn -Dtest=AgentHookIntegrationTest test`，观察事件记录包含对应 filePath）
- [ ] `permission_request` 事件能在权限审批请求发生时触发，且不会改变原有审批结果（验证：运行 `mvn -Dtest=AgentToolRunnerPermissionTest test`，观察审批流程结果与 Hook 前一致）
- [ ] `MessageChannelBuilder` 或 prompt 构建链路能 drain Hook reminder，且 drain 后不会重复注入（验证：运行 `mvn -Dtest=MessageChannelBuilderTest,HookReminderStoreTest test`，观察第二次构建不重复出现）
- [ ] `BashTool` 改用共享命令执行器后，原有 Bash 工具行为保持不变（验证：运行 `mvn -Dtest=BashToolTest test`，观察旧用例全部通过）
- [ ] `DefaultContextManager` 在压缩前触发 `compact`，压缩失败时触发 `error`，Hook 失败不影响压缩流程（验证：运行 `mvn -Dtest=ContextHookIntegrationTest,DefaultContextManagerTest test`，观察压缩结果符合原行为）
- [ ] `SlashCommandDispatcher` 在 Slash Command 执行后触发 `command_execute`，且 Slash Command 不触发普通 LLM `pre_send`（验证：运行 `mvn -Dtest=SlashCommandHookTest test`，观察事件记录）
- [ ] `config.example.yaml` 中的 Hook 示例能被配置加载器解析并通过校验（验证：运行 `mvn -Dtest=HookConfigLoaderTest,HookValidatorTest test`，观察示例片段校验通过）

## 日志与安全

- [ ] Hook 失败不会中断 Agent 主流程，除 `pre_tool_use reject` 的安全拦截语义外（验证：运行 `mvn -Dtest=DefaultHookRuntimeTest,AgentHookIntegrationTest test`，观察失败 Hook 后 Agent 仍继续）
- [ ] 配置错误信息包含来源文件、规则序号和具体原因（验证：运行 `mvn -Dtest=HookValidatorTest test`，观察错误文本可定位到配置来源）
- [ ] Hook 执行顺序不依赖并发调度，异步 Hook 不改变同步 Hook 的顺序（验证：运行 `mvn -Dtest=DefaultHookRuntimeTest test`，观察同步执行顺序稳定）
- [ ] Hook 日志不泄漏已被现有脱敏机制覆盖的敏感值（验证：运行 `mvn -Dtest=CommandHookActionExecutorTest,DefaultHookRuntimeTest test`，观察日志中的敏感样例被脱敏）
- [ ] 默认非 prompt 动作结果不污染对话上下文（验证：运行 `mvn -Dtest=HookPromptIntegrationTest test`，观察默认 command/http 输出不在 prompt 中）
- [ ] Hook 命令不能绕过现有工作区沙箱和命令执行限制（验证：运行 `mvn -Dtest=CommandHookActionExecutorTest,BashToolTest test`，观察越界或受限命令仍被限制）
- [ ] Hook 命令不会触发交互式权限确认（验证：端到端执行 post_tool_use command，观察没有权限审批提示）

## 编译与测试

- [ ] 项目能完整编译（验证：运行 `mvn -DskipTests compile`，期望退出码为 0）
- [ ] Hook 模块单元测试全部通过（验证：运行 `mvn -Dtest=HookConditionParserTest,HookConditionEvaluatorTest,HookConfigLoaderTest,HookValidatorTest,DefaultHookRuntimeTest,CommandHookActionExecutorTest,HttpHookActionExecutorTest,HookReminderStoreTest test`，期望退出码为 0）
- [ ] 生命周期集成测试全部通过（验证：运行 `mvn -Dtest=AgentHookIntegrationTest,HookPromptIntegrationTest,SlashCommandHookTest,ContextHookIntegrationTest,HookApplicationWiringTest test`，期望退出码为 0）
- [ ] 受影响的现有测试仍然通过（验证：运行 `mvn -Dtest=BashToolTest,DefaultAgentLoopTest,AgentToolRunnerTest,AgentToolRunnerPermissionTest,MessageChannelBuilderTest,PromptContextBuilderTest,LunaCodeApplicationTest,SlashCommandDispatcherTest,DefaultContextManagerTest test`，期望退出码为 0）
- [ ] 全量测试通过（验证：运行 `mvn test`，期望退出码为 0）
- [ ] 代码变更没有空白或行尾问题（验证：运行 `git diff --check`，期望无输出）

## 端到端场景

- [ ] 场景 1：三层配置合并与顺序。分别在项目级、用户级、本地级配置 `turn_start` command Hook，命令向同一个日志写入来源名；在 tmux 中启动 LunaCode 并发送一条普通对话，观察日志按项目级、用户级、本地级顺序出现（验证：查看 Hook 日志和命令输出日志）
- [ ] 场景 2：工具前安全拦截。在 `.lunacode/config.local.yaml` 配置 `pre_tool_use`，条件为 `tool == "WriteFile" && args.path ~= "package-lock.json"`，动作输出拒绝原因并设置 `reject: true`；在 tmux 中请求 Agent 修改 `package-lock.json`，观察工具调用被取消，Agent 收到拒绝原因并调整策略（验证：对话和 Hook 日志显示拒绝原因，文件未被直接写入）
- [ ] 场景 3：工具后命令动作读取上下文。配置 `post_tool_use` command Hook，把 `TOOL_NAME`、`FILE_PATH`、`ARGS_PATH` 写入临时日志；请求 Agent 写入一个测试文件，观察命令被执行且日志包含正确工具名和路径（验证：查看临时日志和 `.lunacode/tmp/hooks/<sessionId>.log`）
- [ ] 场景 4：prompt 注入当前请求。配置 `pre_send` prompt Hook，注入一条可观察的 system reminder；在 tmux 中发送普通问题，观察模型回复体现该 reminder，且用户原文没有被改写（验证：对话输出和调试日志）
- [ ] 场景 5：HTTP 动作变量替换。启动本地测试 HTTP 服务，配置 `post_tool_use` HTTP Hook，把 `${toolName}`、`${filePath}` 或等价上下文变量放入请求体；触发工具调用后观察服务收到的请求包含实际上下文值（验证：本地 HTTP 服务日志）
- [ ] 场景 6：异步失败不影响主流程。配置一个异步 command Hook，使其返回失败；发送普通对话后观察 Agent 正常回复，状态栏不出现 Hook 失败提示，Hook 日志记录失败（验证：对话输出、状态栏和 Hook 日志）
- [ ] 场景 7：once 只在同一会话执行一次。配置 `turn_start` command Hook 并设置 `once: true`；同一 tmux 会话连续发送两条消息，观察命令只执行一次；重启 LunaCode 后再次发送消息，观察命令重新执行（验证：命令输出日志计数）
- [ ] 场景 8：file_change 只响应 LunaCode 文件工具。先用外部编辑器修改一个文件，再请求 Agent 用文件工具修改另一个文件；观察只有 Agent 文件工具修改触发 `file_change` Hook（验证：Hook 日志中只出现 Agent 修改的路径）
- [ ] 场景 9：非法配置启动失败。写入一个同时包含未知事件、缺少 action、混用 `&&`/`||`、非 `pre_tool_use` 使用 reject 的配置；启动 LunaCode，观察启动失败并一次性打印所有错误（验证：启动输出包含全部错误）
- [ ] 场景 10：sub_agent 占位。配置合法的 `sub_agent` Hook 并触发它，观察日志写入未实现状态，LunaCode 不启动真实子 Agent，主流程继续（验证：Hook 日志和对话继续）

## 验收标准映射

- [ ] AC1 已覆盖：配置合并与执行顺序（验证：实现完整性第 2-3 项，端到端场景 1）
- [ ] AC2 已覆盖：配置非法启动失败并报告全部错误（验证：实现完整性第 4 项，端到端场景 9）
- [ ] AC3 已覆盖：所有生命周期事件触发（验证：实现完整性第 5-9 项）
- [ ] AC4 已覆盖：无条件和四种匹配操作符（验证：实现完整性第 1、12 项）
- [ ] AC5 已覆盖：AND、OR 和混用失败（验证：实现完整性第 13-14 项）
- [ ] AC6 已覆盖：`pre_tool_use reject` 取消工具调用并反馈原因（验证：实现完整性第 24 项，端到端场景 2）
- [ ] AC7 已覆盖：拒绝动作失败或无输出时仍拒绝（验证：实现完整性第 25 项）
- [ ] AC8 已覆盖：`post_tool_use` command 可读取上下文环境变量（验证：实现完整性第 16 项，端到端场景 3）
- [ ] AC9 已覆盖：命令动作复用 Bash 执行环境且无权限弹窗（验证：实现完整性第 15 项，日志与安全第 7 项）
- [ ] AC10 已覆盖：prompt 注入后续或当前请求（验证：实现完整性第 18-19 项，端到端场景 4）
- [ ] AC11 已覆盖：command/http 默认只记日志，`inject_result` 后注入（验证：实现完整性第 22-23 项）
- [ ] AC12 已覆盖：HTTP 动作和上下文变量（验证：实现完整性第 20 项，端到端场景 5）
- [ ] AC13 已覆盖：once 同会话一次，重启后可重新执行（验证：实现完整性第 29 项，端到端场景 7）
- [ ] AC14 已覆盖：异步不阻塞，失败只写日志（验证：实现完整性第 30 项，端到端场景 6）
- [ ] AC15 已覆盖：`file_change` 只由 LunaCode 文件工具触发（验证：实现完整性第 10 项，端到端场景 8）
- [ ] AC16 已覆盖：sub_agent 校验和未实现日志（验证：实现完整性第 21 项，端到端场景 10）
- [ ] AC17 已覆盖：Hook 日志包含执行、失败、跳过和拦截原因（验证：实现完整性第 31 项）
