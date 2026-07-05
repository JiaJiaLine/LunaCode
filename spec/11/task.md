# Agent Hook 自动化 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/hook/HookEventName.java` | 定义 Hook 生命周期事件和 snake_case 互转 |
| 新建 | `src/main/java/com/lunacode/hook/HookSourceLevel.java` | 定义项目级、用户级、本地级来源顺序 |
| 新建 | `src/main/java/com/lunacode/hook/HookSource.java` | 记录 Hook 来源文件和级别 |
| 新建 | `src/main/java/com/lunacode/hook/HookConfig.java` | 保存已合并并校验的 Hook 列表 |
| 新建 | `src/main/java/com/lunacode/hook/RawHookDefinition.java` | 保存 YAML 读取后的原始 Hook 字段 |
| 新建 | `src/main/java/com/lunacode/hook/HookDefinition.java` | 保存运行时使用的规范化 Hook 定义 |
| 新建 | `src/main/java/com/lunacode/hook/HookContext.java` | 暴露条件表达式可访问的上下文字段 |
| 新建 | `src/main/java/com/lunacode/hook/HookExecutionScope.java` | 保存 session、turn、workspace 等运行时作用域 |
| 新建 | `src/main/java/com/lunacode/hook/HookConditionMode.java` | 定义全部满足和任一满足模式 |
| 新建 | `src/main/java/com/lunacode/hook/HookOperator.java` | 定义 `==`、`!=`、`=~`、`~=` 操作符 |
| 新建 | `src/main/java/com/lunacode/hook/HookPredicate.java` | 表示单个 `field operator value` 条件 |
| 新建 | `src/main/java/com/lunacode/hook/HookCondition.java` | 表示解析后的组合条件 |
| 新建 | `src/main/java/com/lunacode/hook/HookActionType.java` | 定义 command、prompt、http、sub_agent 动作类型 |
| 新建 | `src/main/java/com/lunacode/hook/HookAction.java` | 定义四类 Hook 动作的数据结构 |
| 新建 | `src/main/java/com/lunacode/hook/HookActionResult.java` | 定义动作执行统一结果 |
| 新建 | `src/main/java/com/lunacode/hook/HookRejection.java` | 表示 `pre_tool_use` 拦截结果 |
| 新建 | `src/main/java/com/lunacode/hook/PendingHookReminder.java` | 保存待注入 system reminder |
| 新建 | `src/main/java/com/lunacode/hook/HookConfigException.java` | 聚合配置加载和校验错误 |
| 新建 | `src/main/java/com/lunacode/hook/HookConfigLoader.java` | 读取三层 YAML 配置中的 `hooks` 段 |
| 新建 | `src/main/java/com/lunacode/hook/HookValidator.java` | 集中校验并规范化 Hook 配置 |
| 新建 | `src/main/java/com/lunacode/hook/HookConditionParser.java` | 解析简单条件表达式 |
| 新建 | `src/main/java/com/lunacode/hook/HookConditionEvaluator.java` | 对 `HookContext` 执行条件匹配 |
| 新建 | `src/main/java/com/lunacode/hook/HookRuntime.java` | 定义生命周期事件入口和工具拦截入口 |
| 新建 | `src/main/java/com/lunacode/hook/DefaultHookRuntime.java` | 实现 Hook 匹配、执行、日志、once、异步和拦截 |
| 新建 | `src/main/java/com/lunacode/hook/NoOpHookRuntime.java` | 提供无 Hook 时的空实现 |
| 新建 | `src/main/java/com/lunacode/hook/HookActionExecutor.java` | 定义动作执行器接口 |
| 新建 | `src/main/java/com/lunacode/hook/DefaultHookActionExecutor.java` | 按动作类型分发到具体执行器 |
| 新建 | `src/main/java/com/lunacode/hook/ShellCommandRunner.java` | 抽取 Bash 可复用的命令执行能力 |
| 新建 | `src/main/java/com/lunacode/hook/CommandHookActionExecutor.java` | 执行 command 动作并注入环境变量 |
| 新建 | `src/main/java/com/lunacode/hook/HttpHookActionExecutor.java` | 使用 JDK HttpClient 执行 HTTP 动作 |
| 新建 | `src/main/java/com/lunacode/hook/PromptHookActionExecutor.java` | 将 prompt 动作写入 reminder store |
| 新建 | `src/main/java/com/lunacode/hook/SubAgentPlaceholderActionExecutor.java` | 校验通过后返回未实现占位结果并写日志 |
| 新建 | `src/main/java/com/lunacode/hook/HookReminderStore.java` | 定义 Hook reminder 缓冲接口 |
| 新建 | `src/main/java/com/lunacode/hook/InMemoryHookReminderStore.java` | 提供进程内 reminder 存储 |
| 新建 | `src/main/java/com/lunacode/hook/HookOnceTracker.java` | 定义 once 执行记录接口 |
| 新建 | `src/main/java/com/lunacode/hook/InMemoryHookOnceTracker.java` | 提供进程内 once 记录 |
| 新建 | `src/main/java/com/lunacode/hook/HookLogEntry.java` | 定义 Hook 日志条目结构 |
| 新建 | `src/main/java/com/lunacode/hook/HookLogWriter.java` | 定义 Hook 日志写入接口 |
| 新建 | `src/main/java/com/lunacode/hook/FileHookLogWriter.java` | 写入 `.lunacode/tmp/hooks/<sessionId>.log` |
| 修改 | `src/main/java/com/lunacode/tool/BashTool.java` | 复用 `ShellCommandRunner`，保留现有 Bash 行为 |
| 修改 | `src/main/java/com/lunacode/tool/ToolExecutionContext.java` | 如有需要，补充命令运行所需上下文字段 |
| 修改 | `src/main/java/com/lunacode/prompt/SystemReminderBuilder.java` | 支持渲染 Hook 产生的 system reminder |
| 修改 | `src/main/java/com/lunacode/prompt/MessageChannelBuilder.java` | 构建消息时 drain 当前可用 Hook reminder |
| 修改 | `src/main/java/com/lunacode/prompt/PromptContextBuilder.java` | 传递 session、turn 或 reminder store 依赖 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 启动时加载 Hook 配置并发射 startup、session、shutdown 事件 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 区分 Slash Command 与普通用户消息，并传递 Hook 运行时 |
| 修改 | `src/main/java/com/lunacode/agent/DefaultAgentLoop.java` | 发射 turn、pre_send、post_receive 事件 |
| 修改 | `src/main/java/com/lunacode/agent/execution/AgentToolRunner.java` | 接入 pre_tool_use 拦截、post_tool_use、permission_request、file_change、error |
| 修改 | `src/main/java/com/lunacode/context/DefaultContextManager.java` | 在上下文压缩前后发射 compact 和 error |
| 修改 | `src/main/java/com/lunacode/command/SlashCommandDispatcher.java` | Slash Command 执行后发射 command_execute |
| 修改 | `config.example.yaml` | 增加 Hook 配置示例和字段示范 |
| 新建 | `src/test/java/com/lunacode/hook/HookConditionParserTest.java` | 覆盖条件解析 |
| 新建 | `src/test/java/com/lunacode/hook/HookConditionEvaluatorTest.java` | 覆盖条件匹配 |
| 新建 | `src/test/java/com/lunacode/hook/HookConfigLoaderTest.java` | 覆盖三层配置加载和顺序 |
| 新建 | `src/test/java/com/lunacode/hook/HookValidatorTest.java` | 覆盖配置校验错误聚合 |
| 新建 | `src/test/java/com/lunacode/hook/DefaultHookRuntimeTest.java` | 覆盖运行时匹配、once、异步、拒绝和日志 |
| 新建 | `src/test/java/com/lunacode/hook/CommandHookActionExecutorTest.java` | 覆盖命令动作和环境变量注入 |
| 新建 | `src/test/java/com/lunacode/hook/HttpHookActionExecutorTest.java` | 覆盖 HTTP 动作和变量替换 |
| 新建 | `src/test/java/com/lunacode/hook/HookReminderStoreTest.java` | 覆盖 reminder 可见轮次和 drain 行为 |
| 新建 | `src/test/java/com/lunacode/agent/AgentHookIntegrationTest.java` | 覆盖 Agent 轮次事件与工具事件集成 |
| 新建 | `src/test/java/com/lunacode/orchestrator/HookPromptIntegrationTest.java` | 覆盖 `pre_send` prompt 注入当前请求 |
| 新建 | `src/test/java/com/lunacode/command/SlashCommandHookTest.java` | 覆盖 command_execute 事件 |
| 新建 | `src/test/java/com/lunacode/context/ContextHookIntegrationTest.java` | 覆盖 compact 和 error 事件 |
| 新建 | `src/test/java/com/lunacode/app/HookApplicationWiringTest.java` | 覆盖启动加载失败和启动事件接线 |

## T1: 建立事件、来源和基础配置模型

**文件：** `src/main/java/com/lunacode/hook/HookEventName.java`、`HookSourceLevel.java`、`HookSource.java`、`HookConfig.java`

**依赖：** 无

**步骤：**
1. 新建 `com.lunacode.hook` 包。
2. 定义 `HookEventName`，覆盖 spec 中全部事件，并提供 snake_case 解析和输出方法。
3. 定义 `HookSourceLevel`，固定排序为 `PROJECT -> USER -> LOCAL`。
4. 定义 `HookSource` 和 `HookConfig`，让空配置可通过 `HookConfig.empty()` 创建。

**验证：** 运行 `mvn -Dtest=HookEventNameTest test`；若暂未创建该测试，运行 `mvn -DskipTests compile`，期望编译通过。

## T2: 建立 Hook 定义和运行上下文模型

**文件：** `RawHookDefinition.java`、`HookDefinition.java`、`HookContext.java`、`HookExecutionScope.java`

**依赖：** T1

**步骤：**
1. 按 plan 定义 `RawHookDefinition`，保留来源、顺序、原始 event、if、action、reject、async、once、timeout、inject_result。
2. 定义规范化后的 `HookDefinition`，字段与 plan 保持一致。
3. 定义 `HookContext`，只暴露 `eventName`、`toolName`、`toolArgs`、`filePath`、`message`、`error`。
4. 定义 `HookExecutionScope`，保存 `sessionId`、`turnIndex`、`workspaceRoot`。

**验证：** 运行 `mvn -DskipTests compile`，期望新增 record 和 enum 无编译错误。

## T3: 建立条件表达式模型

**文件：** `HookConditionMode.java`、`HookOperator.java`、`HookPredicate.java`、`HookCondition.java`

**依赖：** T1

**步骤：**
1. 定义 `HookConditionMode.ALL` 和 `HookConditionMode.ANY`。
2. 定义 `HookOperator`，包含四种操作符和从文本符号解析的方法。
3. 定义 `HookPredicate`，保存字段名、操作符、期望值。
4. 定义 `HookCondition`，保存组合模式、子条件列表和原始表达式。

**验证：** 运行 `mvn -DskipTests compile`，期望条件模型可被后续 parser 引用。

## T4: 建立动作、结果和 reminder 模型

**文件：** `HookActionType.java`、`HookAction.java`、`HookActionResult.java`、`HookRejection.java`、`PendingHookReminder.java`

**依赖：** T1、T2

**步骤：**
1. 定义 `HookActionType`，支持 `COMMAND`、`PROMPT`、`HTTP`、`SUB_AGENT`。
2. 定义 sealed `HookAction`，包含 `Command`、`Prompt`、`Http`、`SubAgent` 四个 record。
3. 定义 `HookActionResult`，提供 success 和 failure 工厂方法。
4. 定义 `HookRejection` 和 `PendingHookReminder`，字段与 plan 保持一致。

**验证：** 运行 `mvn -DskipTests compile`，期望动作模型编译通过。

## T5: 实现条件表达式解析器

**文件：** `HookConditionParser.java`、`HookConfigException.java`

**依赖：** T3

**步骤：**
1. 实现空表达式返回空条件。
2. 检测同一表达式同时包含 `&&` 和 `||` 时抛出可诊断错误。
3. 按组合符拆分子条件，并将单个子条件按空格拆为 `field operator value` 三部分。
4. 去掉 value 两侧单引号或双引号，保留内部内容。

**验证：** 运行 `mvn -Dtest=HookConditionParserTest test`，期望覆盖空表达式、单条件、AND、OR、混用报错、格式报错。

## T6: 实现条件表达式求值器

**文件：** `HookConditionEvaluator.java`

**依赖：** T2、T3、T5

**步骤：**
1. 实现 `eventName`、`toolName`、`filePath`、`message`、`error` 字段取值。
2. 支持 `tool` 作为 `toolName` 别名，`args.<key>` 作为 `toolArgs.<key>` 别名。
3. 实现 `==`、`!=`、`=~`、`~=` 四种匹配。
4. 按 `ALL` 和 `ANY` 执行组合逻辑，缺失字段按空字符串处理。

**验证：** 运行 `mvn -Dtest=HookConditionEvaluatorTest test`，期望精确、反向、正则、glob、别名、缺失字段、AND、OR 都通过。

## T7: 实现三层配置读取

**文件：** `HookConfigLoader.java`、`RawHookDefinition.java`

**依赖：** T1、T2、T4

**步骤：**
1. 按项目级 `.lunacode/config.yaml`、用户级 `~/.lunacode/config.yaml`、本地级 `.lunacode/config.local.yaml` 顺序查找。
2. 缺失文件直接跳过，存在文件只读取 `hooks` 段。
3. 将每条 YAML Hook 转为 `RawHookDefinition`，记录来源和文件内顺序。
4. 保留配置文件中的其它字段，不让它们影响 Hook 加载。

**验证：** 运行 `mvn -Dtest=HookConfigLoaderTest test`，期望三层 Hook 追加合并、来源顺序稳定、缺失文件不报错、非 hooks 字段被忽略。

## T8: 实现 Hook 配置字段校验

**文件：** `HookValidator.java`、`HookConfigException.java`

**依赖：** T1、T2、T4、T5

**步骤：**
1. 校验 `event` 必填且属于 `HookEventName`。
2. 校验 `action.type` 必填且属于四种动作类型。
3. 按动作类型校验必填字段，例如 command 需要 `command`，prompt 需要 `prompt`，http 需要 `url`，sub_agent 需要占位字段。
4. 校验 `timeout_ms` 为正数，`inject_result`、`async`、`once`、`reject` 为布尔语义。

**验证：** 运行 `mvn -Dtest=HookValidatorTest test`，期望缺少 event、未知 event、缺少 action、未知 type、动作字段缺失、timeout 非法都被聚合报告。

## T9: 实现 reject、async、条件限制校验

**文件：** `HookValidator.java`

**依赖：** T8

**步骤：**
1. 校验 `reject: true` 只能用于 `pre_tool_use`。
2. 校验 `reject: true` 时不能配置异步执行。
3. 调用 `HookConditionParser` 校验 `if` 表达式，保存解析结果。
4. 聚合所有规则的错误，错误信息包含来源文件、规则顺序和原因。

**验证：** 运行 `mvn -Dtest=HookValidatorTest test`，期望非 `pre_tool_use` 使用 reject、reject 加 async、条件混用 `&&`/`||`、条件格式错误都能一次性报告。

## T10: 实现 once、reminder 和日志基础设施

**文件：** `HookOnceTracker.java`、`InMemoryHookOnceTracker.java`、`HookReminderStore.java`、`InMemoryHookReminderStore.java`、`HookLogEntry.java`、`HookLogWriter.java`、`FileHookLogWriter.java`

**依赖：** T2、T4

**步骤：**
1. 实现 `markIfFirst(sessionId, hookId)`，同一会话同一 Hook 第二次返回 false。
2. 实现 reminder add 和按 `turnIndex` drain，drain 后不再重复返回。
3. 定义 `HookLogEntry` 的状态、耗时、输出摘要和错误摘要字段。
4. 实现 `FileHookLogWriter`，按会话写入 `.lunacode/tmp/hooks/<sessionId>.log`。

**验证：** 运行 `mvn -Dtest=HookReminderStoreTest,DefaultHookRuntimeTest test`，期望 once 行为、reminder drain 行为和日志文件路径生成正确。

## T11: 抽取 Bash 可复用命令执行器

**文件：** `ShellCommandRunner.java`、`src/main/java/com/lunacode/tool/BashTool.java`、`src/main/java/com/lunacode/tool/ToolExecutionContext.java`

**依赖：** T4

**步骤：**
1. 从 `BashTool` 中抽取命令执行、工作目录、沙箱包装、超时、输出截断和脱敏逻辑到 `ShellCommandRunner`。
2. 保持 `BashTool` 对外行为不变，改为调用 `ShellCommandRunner`。
3. 为 Hook 命令预留环境变量注入入口。
4. 如 `ToolExecutionContext` 缺少必要字段，只补充命令执行所需的最小字段。

**验证：** 运行 `mvn -Dtest=BashToolTest test`，期望现有 Bash 工具测试全部通过。

## T12: 实现 command 动作执行器

**文件：** `CommandHookActionExecutor.java`、`ShellCommandRunner.java`

**依赖：** T2、T4、T11

**步骤：**
1. 将 `HookContext` 注入环境变量 `EVENT_NAME`、`TOOL_NAME`、`FILE_PATH`、`MESSAGE`、`ERROR`。
2. 将 `toolArgs` 转为 `ARGS_<KEY>` 环境变量，key 规范化为大写下划线。
3. 调用 `ShellCommandRunner` 执行动作命令，并应用 Hook timeout。
4. 将命令输出和失败信息转换为 `HookActionResult`。

**验证：** 运行 `mvn -Dtest=CommandHookActionExecutorTest test`，期望命令可读取环境变量、遵守 timeout、输出被收集、失败返回 failure。

## T13: 实现 prompt、HTTP、sub_agent 动作执行器

**文件：** `PromptHookActionExecutor.java`、`HttpHookActionExecutor.java`、`SubAgentPlaceholderActionExecutor.java`

**依赖：** T4、T10

**步骤：**
1. `PromptHookActionExecutor` 将 prompt 内容写入 `HookReminderStore`。
2. `HttpHookActionExecutor` 用 JDK `HttpClient` 支持 method、headers、body、timeout。
3. 为 HTTP 的 url、headers、body 实现轻量变量替换。
4. `SubAgentPlaceholderActionExecutor` 返回未实现结果，并为日志提供清晰输出。

**验证：** 运行 `mvn -Dtest=HttpHookActionExecutorTest,HookReminderStoreTest test`，期望 HTTP 请求字段正确、变量替换生效、prompt reminder 可被 drain、sub_agent 不真实启动。

## T14: 实现动作分发器

**文件：** `HookActionExecutor.java`、`DefaultHookActionExecutor.java`

**依赖：** T12、T13

**步骤：**
1. 定义 `HookActionExecutor.execute` 接口。
2. 实现 `DefaultHookActionExecutor`，按 `HookActionType` 分发。
3. 为未知或未支持类型返回 failure，并写入可诊断输出。
4. 确保分发器不吞掉具体执行器返回的输出。

**验证：** 运行 `mvn -Dtest=DefaultHookRuntimeTest test`，期望运行时可通过分发器执行各动作类型。

## T15: 实现 HookRuntime 普通事件路径

**文件：** `HookRuntime.java`、`DefaultHookRuntime.java`、`NoOpHookRuntime.java`

**依赖：** T6、T10、T14

**步骤：**
1. 实现 `emit(event, context, scope)`，按事件筛选 Hook。
2. 对筛选结果执行条件匹配和 once 判断。
3. 同步动作立即执行，异步动作提交到专用 executor。
4. 所有命中、跳过、成功、失败都写 Hook 日志；动作失败不抛到主流程。

**验证：** 运行 `mvn -Dtest=DefaultHookRuntimeTest test`，期望普通事件匹配、条件跳过、once 跳过、异步不阻塞、失败只写日志。

## T16: 实现 pre_tool_use 拦截路径

**文件：** `DefaultHookRuntime.java`、`HookRejection.java`

**依赖：** T15

**步骤：**
1. 实现 `runPreToolHooks(context, scope)`，只匹配 `pre_tool_use`。
2. 按来源和声明顺序同步执行命中的 Hook。
3. 遇到 `reject: true` 时返回 `HookRejection`，拒绝原因使用动作输出。
4. 动作失败或输出为空时，仍返回拒绝，并补充兜底拒绝原因。

**验证：** 运行 `mvn -Dtest=DefaultHookRuntimeTest test`，期望 reject 取消、失败兜底、无输出兜底、reject 后后续 Hook 不再影响原工具调用。

## T17: 实现 inject_result 和 reminder 入队策略

**文件：** `DefaultHookRuntime.java`、`InMemoryHookReminderStore.java`、`PendingHookReminder.java`

**依赖：** T10、T15

**步骤：**
1. prompt 动作总是入队 system reminder。
2. command 和 http 动作仅在 `inject_result: true` 且有输出时入队。
3. `pre_send` 事件产生的 reminder 标记为当前 turn 可用。
4. 其它事件产生的 reminder 标记为下一次 turn 可用。

**验证：** 运行 `mvn -Dtest=DefaultHookRuntimeTest,HookReminderStoreTest test`，期望 prompt 总注入、command/http 默认不注入、inject_result 生效、turn 可见性正确。

## T18: 接入 system reminder 构建链路

**文件：** `SystemReminderBuilder.java`、`MessageChannelBuilder.java`、`PromptContextBuilder.java`

**依赖：** T10、T17

**步骤：**
1. 给 prompt 构建链路注入 `HookReminderStore` 或可选的 Hook reminder provider。
2. 构建 PromptBundle 时按 sessionId 和 turnIndex drain 可用 reminder。
3. 将 Hook reminder 渲染为 system reminder，不改写用户消息或模型回复。
4. 确保没有 Hook reminder 时现有 prompt 输出不变化。

**验证：** 运行 `mvn -Dtest=MessageChannelBuilderTest,PromptContextBuilderTest,HookPromptIntegrationTest test`，期望无 Hook 时行为不变，有 Hook 时出现 system reminder。

## T19: 接入应用启动和关闭生命周期

**文件：** `LunaCodeApplication.java`、`HookConfigLoader.java`、`DefaultHookRuntime.java`、`NoOpHookRuntime.java`

**依赖：** T7、T8、T9、T15

**步骤：**
1. 应用启动时调用 `HookConfigLoader.load(workspaceRoot, userHome)`。
2. 配置校验失败时打印所有错误并终止启动。
3. 配置为空时使用 `NoOpHookRuntime`，配置存在时创建 `DefaultHookRuntime`。
4. 在启动、会话开始、会话结束、关闭时分别发射 `startup`、`session_start`、`session_end`、`shutdown`。

**验证：** 运行 `mvn -Dtest=LunaCodeApplicationTest,HookApplicationWiringTest test`，期望加载失败会启动失败，空配置不影响启动，生命周期事件可被记录。

## T20: 接入普通消息和 Slash Command 分流

**文件：** `DefaultChatOrchestrator.java`、`SlashCommandDispatcher.java`

**依赖：** T15、T19

**步骤：**
1. 确认普通用户消息进入 Agent Loop 前保留 sessionId 和 turnIndex。
2. Slash Command 执行成功或失败后发射 `command_execute`，message 保存命令文本，error 保存错误摘要。
3. 保证 Slash Command 不触发普通 LLM `pre_send`。
4. 保持现有 Slash Command 返回行为不变。

**验证：** 运行 `mvn -Dtest=SlashCommandDispatcherTest,SlashCommandHookTest,DefaultChatOrchestratorTest test`，期望 slash command 事件触发且不污染普通消息流程。

## T21: 接入 Agent 轮次和消息事件

**文件：** `DefaultAgentLoop.java`

**依赖：** T15、T18、T19

**步骤：**
1. 在每轮开始发射 `turn_start`。
2. 在构建发送给 LLM 的消息前发射 `pre_send`。
3. 收到 LLM 响应后发射 `post_receive`，message 保存响应摘要或文本。
4. 每轮结束时发射 `turn_end`，异常时额外发射 `error`。

**验证：** 运行 `mvn -Dtest=DefaultAgentLoopTest,AgentHookIntegrationTest test`，期望 turn、pre_send、post_receive、turn_end 顺序稳定，pre_send prompt 可进入当前请求。

## T22: 接入工具执行前拦截

**文件：** `AgentToolRunner.java`

**依赖：** T16、T19

**步骤：**
1. 在权限检查和真实工具执行前构造 `HookContext`。
2. 调用 `runPreToolHooks`。
3. 如果返回 `HookRejection`，跳过权限检查和工具执行，生成错误 `ToolResult`。
4. 错误结果中包含 Hook 输出的拒绝原因和 hookId。

**验证：** 运行 `mvn -Dtest=AgentToolRunnerTest,AgentHookIntegrationTest test`，期望 `pre_tool_use reject` 能取消工具调用，并把拒绝原因反馈给 Agent。

## T23: 接入工具执行后、权限、文件变化和错误事件

**文件：** `AgentToolRunner.java`

**依赖：** T15、T22

**步骤：**
1. 工具执行完成后发射 `post_tool_use`。
2. 权限审批请求发生时发射 `permission_request`。
3. `WriteFile` 或 `EditFile` 成功修改文件后发射 `file_change`，filePath 保存目标路径。
4. 工具执行失败时发射 `error`，error 保存错误摘要。

**验证：** 运行 `mvn -Dtest=AgentToolRunnerPermissionTest,AgentHookIntegrationTest test`，期望 post_tool_use、permission_request、file_change、error 在对应场景触发。

## T24: 接入上下文压缩事件

**文件：** `DefaultContextManager.java`

**依赖：** T15、T19

**步骤：**
1. 在上下文压缩尝试前发射 `compact`。
2. 压缩失败时发射 `error`，error 保存失败摘要。
3. 保证 Hook 失败不影响原有压缩流程。
4. 保持现有上下文压缩测试行为不变。

**验证：** 运行 `mvn -Dtest=DefaultContextManagerTest,ContextHookIntegrationTest test`，期望 compact 事件触发，压缩失败时 error 事件触发。

## T25: 更新配置示例

**文件：** `config.example.yaml`

**依赖：** T7、T8、T9

**步骤：**
1. 增加 `hooks` 示例，覆盖 `post_tool_use` command 和 `pre_tool_use reject`。
2. 示例中展示 `if` 条件、`action.type`、`timeout_ms`、`inject_result`。
3. 说明三层配置文件路径和追加合并顺序。
4. 保持示例不包含尚未真实运行的 sub_agent 动作。

**验证：** 运行 `mvn -Dtest=HookConfigLoaderTest test`，并用示例片段创建临时配置解析，期望能通过校验。

## T26: 补充配置加载和校验单元测试

**文件：** `HookConfigLoaderTest.java`、`HookValidatorTest.java`

**依赖：** T7、T8、T9、T25

**步骤：**
1. 覆盖项目级、用户级、本地级三个来源都存在时的追加合并。
2. 覆盖同一文件内按声明顺序保留。
3. 覆盖所有配置错误一次性报告。
4. 覆盖 `sub_agent` 配置校验通过但不执行真实子 Agent。

**验证：** 运行 `mvn -Dtest=HookConfigLoaderTest,HookValidatorTest test`，期望所有新增用例通过。

## T27: 补充动作执行单元测试

**文件：** `CommandHookActionExecutorTest.java`、`HttpHookActionExecutorTest.java`、`HookReminderStoreTest.java`

**依赖：** T12、T13、T17

**步骤：**
1. command 测试覆盖上下文环境变量、工具参数环境变量、失败结果、timeout。
2. HTTP 测试使用本地轻量 server 或 mock client，覆盖 method、header、body、变量替换、timeout。
3. reminder 测试覆盖当前 turn、下一 turn 和 drain 后不重复。
4. 确认非 prompt 动作默认不进入 reminder。

**验证：** 运行 `mvn -Dtest=CommandHookActionExecutorTest,HttpHookActionExecutorTest,HookReminderStoreTest test`，期望所有动作行为符合 spec。

## T28: 补充运行时单元测试

**文件：** `DefaultHookRuntimeTest.java`

**依赖：** T15、T16、T17

**步骤：**
1. 覆盖事件筛选、条件命中、条件不命中。
2. 覆盖 once 首次执行、重复跳过。
3. 覆盖异步动作不阻塞、异步失败只写日志。
4. 覆盖 reject 成功、reject 动作失败、reject 空输出兜底。

**验证：** 运行 `mvn -Dtest=DefaultHookRuntimeTest test`，期望运行时所有核心分支通过。

## T29: 补充生命周期集成测试

**文件：** `AgentHookIntegrationTest.java`、`HookPromptIntegrationTest.java`、`SlashCommandHookTest.java`、`ContextHookIntegrationTest.java`、`HookApplicationWiringTest.java`

**依赖：** T19、T20、T21、T22、T23、T24

**步骤：**
1. 覆盖启动、session、turn、pre_send、post_receive、turn_end 的事件顺序。
2. 覆盖 `pre_tool_use reject` 跳过权限和真实工具执行。
3. 覆盖 `post_tool_use` command 能读取 `FILE_PATH`、`TOOL_NAME`、`ARGS_<KEY>`。
4. 覆盖 `command_execute`、`compact`、`file_change`、`error` 事件触发。

**验证：** 运行 `mvn -Dtest=AgentHookIntegrationTest,HookPromptIntegrationTest,SlashCommandHookTest,ContextHookIntegrationTest,HookApplicationWiringTest test`，期望集成行为全部通过。

## T30: 运行全量编译和测试

**文件：** `pom.xml`、全部新增和修改文件

**依赖：** T1-T29

**步骤：**
1. 运行 `mvn test`。
2. 若已有测试受构造函数新增依赖影响失败，补齐 `NoOpHookRuntime` 或测试 fixture。
3. 运行 `git diff --check` 检查空白和行尾问题。
4. 查看 `.lunacode/tmp/hooks` 测试输出是否只在临时目录中生成。

**验证：** `mvn test` 和 `git diff --check` 均通过。

## T31: 执行端到端验收准备

**文件：** `.lunacode/config.yaml`、`.lunacode/config.local.yaml`、`spec/11/checklist.md`

**依赖：** T30、已批准的 checklist

**步骤：**
1. 按 checklist 准备项目级、本地级 Hook 配置。
2. 在 tmux 中启动 LunaCode。
3. 输入真实对话请求，触发工具调用、拦截、post_tool_use、prompt 注入和日志写入。
4. 对照 checklist 逐项记录实际结果。

**验证：** tmux 端到端测试能观察到 Hook 生效，且 checklist 全部可被实际行为验证。

## 执行顺序

```text
T1 -> T2 -> T3 -> T4
  -> T5 -> T6
  -> T7 -> T8 -> T9
  -> T10
  -> T11 -> T12 -> T13 -> T14
  -> T15 -> T16 -> T17
  -> T18
  -> T19 -> T20 -> T21 -> T22 -> T23 -> T24
  -> T25
  -> T26 -> T27 -> T28 -> T29
  -> T30
  -> T31
```
