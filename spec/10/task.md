# Skill 系统 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/skill/SkillExecutionMode.java` | 定义 `INLINE` 和 `FORK` 执行模式 |
| 新建 | `src/main/java/com/lunacode/skill/SkillContextPolicy.java` | 定义 fork 上下文策略 `FULL`、`RECENT`、`NONE` |
| 新建 | `src/main/java/com/lunacode/skill/SkillSourceKind.java` | 定义项目级、用户级、内置级来源类型 |
| 新建 | `src/main/java/com/lunacode/skill/SkillDiagnosticLevel.java` | 定义 Skill 诊断级别 |
| 新建 | `src/main/java/com/lunacode/skill/SkillOrigin.java` | 记录 Skill 来源、路径和优先级 |
| 新建 | `src/main/java/com/lunacode/skill/SkillDefinition.java` | 表示完整 Skill 定义 |
| 新建 | `src/main/java/com/lunacode/skill/SkillSummary.java` | 表示可注入上下文和命令列表的轻量摘要 |
| 新建 | `src/main/java/com/lunacode/skill/SkillParseResult.java` | 表示单个 Skill 的解析成功或失败 |
| 新建 | `src/main/java/com/lunacode/skill/SkillCandidate.java` | 表示来源扫描得到的候选文件、目录或内置资源 |
| 新建 | `src/main/java/com/lunacode/skill/SkillDiagnostic.java` | 表示坏文件、冲突、白名单错误和缓存回退诊断 |
| 新建 | `src/main/java/com/lunacode/skill/SkillCatalogSnapshot.java` | 表示当前可见 Skill 摘要和诊断集合 |
| 新建 | `src/main/java/com/lunacode/skill/SkillInvocationTrigger.java` | 区分斜杠命令触发和工具触发 |
| 新建 | `src/main/java/com/lunacode/skill/SkillInvocationRequest.java` | 表示一次 Skill 调用请求 |
| 新建 | `src/main/java/com/lunacode/skill/ToolAccessPolicy.java` | 表示本次调用的工具白名单和系统级例外工具 |
| 新建 | `src/main/java/com/lunacode/skill/SkillInvocationPlan.java` | 表示渲染后的 prompt、工具策略和模型覆盖 |
| 新建 | `src/main/java/com/lunacode/skill/LoadedSkillContext.java` | 表示本轮通过 `LoadSkill` 加载出的完整 SOP |
| 新建 | `src/main/java/com/lunacode/skill/SkillForkResult.java` | 表示 fork 子对话回流到主历史的总结 |
| 新建 | `src/main/java/com/lunacode/skill/SkillParser.java` | 定义单文件、目录型、内置 Skill 解析接口 |
| 新建 | `src/main/java/com/lunacode/skill/SkillSource.java` | 定义 Skill 来源扫描接口 |
| 新建 | `src/main/java/com/lunacode/skill/SkillCatalog.java` | 定义 Skill 目录快照、执行加载和诊断接口 |
| 新建 | `src/main/java/com/lunacode/skill/SkillInvocationPlanner.java` | 定义 Skill 执行计划生成接口 |
| 新建 | `src/main/java/com/lunacode/skill/SkillPromptContextLoader.java` | 定义 prompt 构建时读取 Skill 摘要和临时 SOP 的接口 |
| 新建 | `src/main/java/com/lunacode/skill/SkillForkRunner.java` | 定义 fork Skill 子对话执行接口 |
| 新建 | `src/main/java/com/lunacode/skill/SkillForkContextBuilder.java` | 构建 fork 子对话初始上下文 |
| 新建 | `src/main/java/com/lunacode/skill/FrontmatterSkillParser.java` | 实现 Markdown frontmatter 解析和字段校验 |
| 新建 | `src/main/java/com/lunacode/skill/FileSystemSkillSource.java` | 扫描项目级和用户级 Skill 文件或目录 |
| 新建 | `src/main/java/com/lunacode/skill/BuiltinSkillSource.java` | 提供编译进程序的内置 Skill 资源清单 |
| 新建 | `src/main/java/com/lunacode/skill/DefaultSkillCatalog.java` | 合并三层来源、处理缓存和诊断 |
| 新建 | `src/main/java/com/lunacode/skill/DefaultSkillInvocationPlanner.java` | 重新解析 Skill、替换 `$ARGUMENTS`、生成调用级策略 |
| 新建 | `src/main/java/com/lunacode/skill/DefaultSkillPromptContextLoader.java` | 给 prompt 构建器提供 Skill 摘要和本轮完整 SOP |
| 新建 | `src/main/java/com/lunacode/skill/DefaultSkillForkRunner.java` | 执行 fork 子对话并生成主历史回流总结 |
| 新建 | `src/main/java/com/lunacode/tool/LoadSkillTool.java` | 实现系统级 Skill 加载工具 |
| 新建 | `src/main/java/com/lunacode/command/SkillCommandRegistrar.java` | 把有效 Skill 注册成动态斜杠命令 |
| 新建 | `src/main/java/com/lunacode/prompt/SkillPromptRenderer.java` | 渲染 Skill 摘要和临时完整 SOP |
| 新建 | `src/main/resources/lunacode/skills/commit.md` | 内置 `commit` Skill 样板 |
| 新建 | `src/main/resources/lunacode/skills/test.md` | 内置 `test` Skill 样板 |
| 修改 | `src/main/java/com/lunacode/command/SlashCommandRegistry.java` | 支持动态 Skill 命令注册、重建和冲突检测 |
| 修改 | `src/main/java/com/lunacode/command/CommandRuntime.java` | 增加 Skill 调用入口 |
| 修改 | `src/main/java/com/lunacode/prompt/MessageChannel.java` | 携带 Skill prompt 上下文 |
| 修改 | `src/main/java/com/lunacode/prompt/PromptContextBuilder.java` | 每轮注入 Skill 摘要和本轮临时完整 SOP |
| 修改 | `src/main/java/com/lunacode/provider/AnthropicPromptAdapter.java` | 将 Skill 上下文渲染到 Anthropic system blocks |
| 修改 | `src/main/java/com/lunacode/provider/OpenAiPromptAdapter.java` | 将 Skill 上下文渲染到 OpenAI developer messages |
| 修改 | `src/main/java/com/lunacode/tool/ToolRegistry.java` | 让工具声明按调用级工具策略过滤 |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolRegistry.java` | 实现工具声明白名单过滤和系统级工具例外 |
| 修改 | `src/main/java/com/lunacode/tool/AgentToolRunner.java` | 执行工具前按同一工具策略二次校验 |
| 修改 | `src/main/java/com/lunacode/agent/AgentRequest.java` | 区分用户可见消息和模型可见消息 |
| 修改 | `src/main/java/com/lunacode/agent/DefaultAgentLoop.java` | 支持调用级 prompt、工具策略、模型覆盖和 `LoadSkill` 结果清理 |
| 修改 | `src/main/java/com/lunacode/runtime/AgentRunConfig.java` | 增加工具策略、模型覆盖和 Skill prompt 上下文 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 分派 inline/fork Skill 调用，管理 busy 状态和回流总结 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 装配 Skill catalog、`LoadSkill`、动态命令和资源根 |
| 新建 | `src/test/java/com/lunacode/skill/FrontmatterSkillParserTest.java` | 覆盖字段解析、默认值和坏文件 |
| 新建 | `src/test/java/com/lunacode/skill/FileSystemSkillSourceTest.java` | 覆盖单文件和目录型候选发现 |
| 新建 | `src/test/java/com/lunacode/skill/DefaultSkillCatalogTest.java` | 覆盖优先级合并、缓存回退和诊断 |
| 新建 | `src/test/java/com/lunacode/skill/SkillInvocationPlannerTest.java` | 覆盖 `$ARGUMENTS`、工具白名单和模型覆盖 |
| 新建 | `src/test/java/com/lunacode/skill/SkillForkContextBuilderTest.java` | 覆盖 `full`、`recent`、`none` 上下文策略 |
| 新建 | `src/test/java/com/lunacode/tool/LoadSkillToolTest.java` | 覆盖系统级加载工具行为 |
| 新建 | `src/test/java/com/lunacode/command/SkillCommandRegistrarTest.java` | 覆盖动态命令注册、帮助信息和冲突 |
| 新建 | `src/test/java/com/lunacode/prompt/SkillPromptRendererTest.java` | 覆盖 Skill 摘要和完整 SOP 渲染 |
| 新建 | `src/test/java/com/lunacode/agent/SkillAgentLoopTest.java` | 覆盖调用级消息、工具策略和清理 |
| 新建 | `src/test/java/com/lunacode/orchestrator/SkillOrchestratorTest.java` | 覆盖 inline/fork 调用和硬编码 `/review` 保留 |

## T1: 建立 Skill 基础枚举

**文件：** `src/main/java/com/lunacode/skill/SkillExecutionMode.java`、`src/main/java/com/lunacode/skill/SkillContextPolicy.java`、`src/main/java/com/lunacode/skill/SkillSourceKind.java`、`src/main/java/com/lunacode/skill/SkillDiagnosticLevel.java`

**依赖：** 无

**步骤：**
1. 新建 `com.lunacode.skill` 包。
2. 定义 `SkillExecutionMode`，包含 `INLINE` 和 `FORK`。
3. 定义 `SkillContextPolicy`，包含 `FULL`、`RECENT`、`NONE`。
4. 定义 `SkillSourceKind`，包含 `PROJECT`、`USER`、`BUILTIN`。
5. 定义 `SkillDiagnosticLevel`，包含可用于警告和错误的级别。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T2: 建立 Skill 核心记录类型

**文件：** `src/main/java/com/lunacode/skill/SkillOrigin.java`、`src/main/java/com/lunacode/skill/SkillDefinition.java`、`src/main/java/com/lunacode/skill/SkillSummary.java`、`src/main/java/com/lunacode/skill/SkillDiagnostic.java`

**依赖：** T1

**步骤：**
1. 按 `plan.md` 定义 `SkillOrigin`，记录来源类型、来源标识、可选路径和优先级。
2. 定义 `SkillDefinition`，包含名称、说明、模式、上下文策略、可选模型、工具列表、正文、来源和可选资源根。
3. 定义 `SkillSummary`，包含名称、说明、来源和模式。
4. 定义 `SkillDiagnostic`，包含级别、来源标识和消息。
5. 在构造处复制列表类字段，避免外部修改内部状态。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T3: 建立解析和候选结果类型

**文件：** `src/main/java/com/lunacode/skill/SkillParseResult.java`、`src/main/java/com/lunacode/skill/SkillCandidate.java`、`src/main/java/com/lunacode/skill/SkillCatalogSnapshot.java`

**依赖：** T2

**步骤：**
1. 定义 `SkillParseResult` sealed interface，并提供 `Success` 和 `Failure` 记录类型。
2. 定义 `SkillCandidate`，能够表示单文件、目录入口和内置资源三类候选。
3. 定义 `SkillCatalogSnapshot`，包含摘要列表和诊断列表。
4. 对外暴露不可变列表。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T4: 建立 Skill 调用计划类型

**文件：** `src/main/java/com/lunacode/skill/SkillInvocationTrigger.java`、`src/main/java/com/lunacode/skill/SkillInvocationRequest.java`、`src/main/java/com/lunacode/skill/ToolAccessPolicy.java`、`src/main/java/com/lunacode/skill/SkillInvocationPlan.java`、`src/main/java/com/lunacode/skill/LoadedSkillContext.java`、`src/main/java/com/lunacode/skill/SkillForkResult.java`

**依赖：** T2

**步骤：**
1. 定义 `SkillInvocationTrigger`，区分 `SLASH` 和 `TOOL`。
2. 定义 `SkillInvocationRequest`，记录 Skill 名称、原始参数和触发来源。
3. 定义 `ToolAccessPolicy`，包含可选白名单和始终可见工具集合。
4. 定义 `SkillInvocationPlan`，包含完整定义、渲染后的 prompt、工具策略和可选模型覆盖。
5. 定义 `LoadedSkillContext`，记录 Skill 名称、渲染 prompt 和可选资源根。
6. 定义 `SkillForkResult`，记录 Skill 名称、用户请求、总结、产物路径和后续建议。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T5: 定义 Skill 模块接口

**文件：** `src/main/java/com/lunacode/skill/SkillParser.java`、`src/main/java/com/lunacode/skill/SkillSource.java`、`src/main/java/com/lunacode/skill/SkillCatalog.java`、`src/main/java/com/lunacode/skill/SkillInvocationPlanner.java`、`src/main/java/com/lunacode/skill/SkillPromptContextLoader.java`、`src/main/java/com/lunacode/skill/SkillForkRunner.java`

**依赖：** T3、T4

**步骤：**
1. 定义 `SkillParser` 的单文件、目录型和内置资源解析方法。
2. 定义 `SkillSource` 的候选发现方法。
3. 定义 `SkillCatalog` 的快照、执行加载和诊断方法。
4. 定义 `SkillInvocationPlanner` 的执行计划生成方法。
5. 定义 `SkillPromptContextLoader` 的摘要和本轮完整 SOP 读取方法。
6. 定义 `SkillForkRunner` 的 fork 执行方法。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T6: 实现 frontmatter 分割和 YAML 读取

**文件：** `src/main/java/com/lunacode/skill/FrontmatterSkillParser.java`

**依赖：** T5

**步骤：**
1. 新建 `FrontmatterSkillParser` 并实现 `SkillParser`。
2. 检查文件内容必须以 `---` frontmatter 开头。
3. 分割 YAML frontmatter 和 Markdown 正文。
4. 使用项目已有 Jackson YAML 依赖把 frontmatter 解析成字段映射。
5. 解析失败时返回 `SkillParseResult.Failure`，不抛出到调用方。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T7: 实现 frontmatter 字段校验和默认值

**文件：** `src/main/java/com/lunacode/skill/FrontmatterSkillParser.java`

**依赖：** T6

**步骤：**
1. 校验 `name` 和 `description` 必填且为非空字符串。
2. 校验 `name` 只包含小写字母、数字和连字符。
3. 解析 `mode`，缺省为 `inline`，只接受 `inline` 和 `fork`。
4. 解析 `context`，缺省为 `full`，只接受 `full`、`recent`、`none`。
5. 解析可选 `model`。
6. 解析可选 `tools`，接受字符串列表，缺省为空列表。
7. 构造 `SkillDefinition`，目录型解析时设置 `resourceRoot`。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T8: 编写 frontmatter 解析单元测试

**文件：** `src/test/java/com/lunacode/skill/FrontmatterSkillParserTest.java`

**依赖：** T7

**步骤：**
1. 测试合法单文件 Skill 能解析出名称、说明、正文和默认值。
2. 测试目录型 Skill 的 `SKILL.md` 能记录目录资源根。
3. 测试缺少 `name`、缺少 `description`、非法 `name` 均返回失败。
4. 测试非法 `mode` 和非法 `context` 均返回失败。
5. 测试 `tools`、`model` 和正文按预期保留。

**验证：** 运行 `mvn -q -Dtest=FrontmatterSkillParserTest test`，测试通过。

## T9: 实现文件系统 Skill 来源扫描

**文件：** `src/main/java/com/lunacode/skill/FileSystemSkillSource.java`

**依赖：** T3

**步骤：**
1. 支持从给定 Skill 根目录扫描一级 `.md` 文件作为单文件候选。
2. 支持从给定 Skill 根目录扫描包含 `SKILL.md` 的一级子目录作为目录型候选。
3. 忽略没有 `SKILL.md` 的普通目录。
4. 生成带来源类型、来源标识、路径和优先级的 `SkillCandidate`。
5. 根目录不存在时返回空候选列表。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T10: 编写文件系统来源测试

**文件：** `src/test/java/com/lunacode/skill/FileSystemSkillSourceTest.java`

**依赖：** T9

**步骤：**
1. 构造临时项目级 Skill 根，放入单文件 Skill。
2. 构造临时目录型 Skill，目录内放入 `SKILL.md` 和附属文件。
3. 构造没有入口文件的目录。
4. 断言扫描结果只包含单文件和有效目录型候选。
5. 断言候选来源类型和优先级符合项目级或用户级配置。

**验证：** 运行 `mvn -q -Dtest=FileSystemSkillSourceTest test`，测试通过。

## T11: 实现内置 Skill 来源

**文件：** `src/main/java/com/lunacode/skill/BuiltinSkillSource.java`、`src/main/resources/lunacode/skills/commit.md`、`src/main/resources/lunacode/skills/test.md`

**依赖：** T3

**步骤：**
1. 在 resources 中新增 `commit.md`，frontmatter 设置 `name: commit`、中文说明和 `mode: inline`。
2. 在 resources 中新增 `test.md`，frontmatter 设置 `name: test`、中文说明和 `mode: inline`。
3. 在正文中写清 SOP，并使用 `$ARGUMENTS` 支持用户参数。
4. 实现 `BuiltinSkillSource`，返回固定的 `commit.md` 和 `test.md` 资源候选。
5. 不提供内置 `review` Skill。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过且资源路径能被类加载器读取。

## T12: 实现 Skill catalog 基础扫描和合并

**文件：** `src/main/java/com/lunacode/skill/DefaultSkillCatalog.java`

**依赖：** T5、T7、T9、T11

**步骤：**
1. 接收项目级、用户级和内置级 `SkillSource`。
2. 扫描所有候选并逐个调用 `SkillParser`。
3. 对解析失败的候选记录 `SkillDiagnostic`。
4. 按内置级、用户级、项目级顺序合并有效定义。
5. 同名有效定义由高优先级覆盖低优先级。
6. `snapshot()` 返回有效摘要和诊断。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T13: 实现 Skill catalog 冲突和工具存在性校验

**文件：** `src/main/java/com/lunacode/skill/DefaultSkillCatalog.java`

**依赖：** T12

**步骤：**
1. 接入内置斜杠命令名称集合，用于检测 Skill 名称冲突。
2. 接入当前工具注册表的工具名称集合，用于检测 `tools` 中不存在的工具。
3. Skill 名称与内置命令冲突时记录诊断并跳过该 Skill。
4. `tools` 包含不存在工具时记录诊断并跳过该 Skill。
5. 保证被跳过的高优先级 Skill 不覆盖低优先级有效 Skill。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T14: 实现执行前热更新和缓存回退

**文件：** `src/main/java/com/lunacode/skill/DefaultSkillCatalog.java`

**依赖：** T13

**步骤：**
1. 为每个有效 Skill 保存上一次成功解析的 `SkillDefinition`。
2. `loadForExecution(name)` 每次调用时重新解析对应候选入口。
3. 重新解析成功时刷新缓存。
4. 重新解析失败且存在缓存时返回缓存版本并记录警告诊断。
5. 重新解析失败且没有缓存时返回空并记录警告诊断。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T15: 编写 catalog 单元测试

**文件：** `src/test/java/com/lunacode/skill/DefaultSkillCatalogTest.java`

**依赖：** T14

**步骤：**
1. 测试项目级、用户级和内置级同名 Skill 按项目级优先。
2. 删除项目级候选后，断言用户级生效；删除用户级候选后，断言内置级生效。
3. 测试坏 frontmatter 不阻断其他 Skill。
4. 测试首次解析失败且无缓存时不出现在摘要中。
5. 测试执行前修改文件后下一次加载使用新正文。
6. 测试执行前解析失败时回退到缓存版本并产生诊断。
7. 测试命令冲突和不存在工具会让对应 Skill 无效。

**验证：** 运行 `mvn -q -Dtest=DefaultSkillCatalogTest test`，测试通过。

## T16: 实现 Skill 调用计划生成

**文件：** `src/main/java/com/lunacode/skill/DefaultSkillInvocationPlanner.java`

**依赖：** T4、T14

**步骤：**
1. 根据 `SkillInvocationRequest.name` 调用 `SkillCatalog.loadForExecution`。
2. 找不到有效 Skill 时返回可被上层展示的失败结果或抛出受控异常。
3. 将 `body` 中所有 `$ARGUMENTS` 替换为 `rawArguments`。
4. 当原始参数为空时替换为空字符串。
5. 根据 `tools` 构造 `ToolAccessPolicy`。
6. 始终把 `LoadSkill` 加入 `alwaysVisibleTools`。
7. 根据可选 `model` 设置 `modelOverride`。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T17: 编写调用计划测试

**文件：** `src/test/java/com/lunacode/skill/SkillInvocationPlannerTest.java`

**依赖：** T16

**步骤：**
1. 测试 `$ARGUMENTS` 在正文中多次出现时全部替换。
2. 测试无参数调用时 `$ARGUMENTS` 替换为空字符串。
3. 测试无 `tools` 时不收窄工具。
4. 测试设置 `tools` 时生成白名单。
5. 测试 `LoadSkill` 始终在系统级例外工具集合中。
6. 测试设置 `model` 时生成模型覆盖。

**验证：** 运行 `mvn -q -Dtest=SkillInvocationPlannerTest test`，测试通过。

## T18: 扩展斜杠命令注册表

**文件：** `src/main/java/com/lunacode/command/SlashCommandRegistry.java`

**依赖：** T15

**步骤：**
1. 增加注册动态 Skill 命令的能力。
2. 保留现有内置命令注册方式。
3. 提供获取内置命令名集合的方法，供 catalog 做冲突检测。
4. 支持清空并重建动态 Skill 命令，不影响内置命令。
5. 保证 `/help` 和补全读取同一个注册表视图。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T19: 实现 Skill 命令注册器

**文件：** `src/main/java/com/lunacode/command/SkillCommandRegistrar.java`、`src/main/java/com/lunacode/command/CommandRuntime.java`

**依赖：** T18、T16

**步骤：**
1. 新建 `SkillCommandRegistrar`。
2. 从 `SkillCatalog.snapshot()` 读取有效摘要。
3. 为每个摘要创建无别名斜杠命令，命令名为 `/<name>`。
4. 命令说明使用 `description`。
5. handler 调用 `CommandRuntime.submitSkillInvocation`。
6. 在 `CommandRuntime` 中声明 `submitSkillInvocation(SkillInvocationRequest request)`。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T20: 编写 Skill 命令注册测试

**文件：** `src/test/java/com/lunacode/command/SkillCommandRegistrarTest.java`

**依赖：** T19

**步骤：**
1. 构造带 `commit` 和 `test` 摘要的假 catalog。
2. 注册动态 Skill 命令。
3. 断言 `/help` 可见命令名和说明。
4. 断言补全集合包含有效 Skill 命令。
5. 断言执行 `/commit 参数` 会调用 `submitSkillInvocation` 并保留原始参数。
6. 断言硬编码 `/review` 仍由内置命令提供。

**验证：** 运行 `mvn -q -Dtest=SkillCommandRegistrarTest test`，测试通过。

## T21: 增加 Skill prompt 上下文模型

**文件：** `src/main/java/com/lunacode/skill/SkillPromptContext.java`、`src/main/java/com/lunacode/skill/DefaultSkillPromptContextLoader.java`、`src/main/java/com/lunacode/prompt/MessageChannel.java`

**依赖：** T4、T15

**步骤：**
1. 新建 `SkillPromptContext`，包含轻量摘要列表和可选 `LoadedSkillContext`。
2. 实现 `DefaultSkillPromptContextLoader`，从 catalog 读取摘要，从本轮作用域读取完整 SOP。
3. 扩展 `MessageChannel`，增加 `SkillPromptContext` 字段。
4. 保证缺省情况下 Skill 上下文为空对象。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T22: 实现 Skill prompt 渲染器

**文件：** `src/main/java/com/lunacode/prompt/SkillPromptRenderer.java`

**依赖：** T21

**步骤：**
1. 渲染轻量 Skill 列表，只包含名称、说明和模式。
2. 在列表说明中提示模型需要完整 SOP 时调用 `LoadSkill`。
3. 渲染本轮完整 SOP，包含 Skill 名称、渲染后的正文和可选资源根。
4. 对目录型 Skill 只提示资源根和按需读取原则，不展开附属文件。
5. 没有 Skill 摘要和完整 SOP 时返回空渲染结果。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T23: 接入 PromptContextBuilder 和 provider adapter

**文件：** `src/main/java/com/lunacode/prompt/PromptContextBuilder.java`、`src/main/java/com/lunacode/provider/AnthropicPromptAdapter.java`、`src/main/java/com/lunacode/provider/OpenAiPromptAdapter.java`

**依赖：** T22

**步骤：**
1. 在 `PromptContextBuilder` 构建 `MessageChannel` 时读取 Skill prompt 上下文。
2. Anthropic 适配器把 Skill 上下文渲染进 system blocks 的显眼位置。
3. OpenAI 适配器把 Skill 上下文渲染进 developer messages 的显眼位置。
4. 保证普通项目指令、memory 和历史消息顺序保持稳定。
5. 保证只有摘要默认进入每轮上下文，完整 SOP 只在本轮作用域存在时进入。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T24: 编写 prompt 渲染测试

**文件：** `src/test/java/com/lunacode/prompt/SkillPromptRendererTest.java`

**依赖：** T23

**步骤：**
1. 测试轻量摘要渲染不包含完整 SOP 正文。
2. 测试完整 SOP 渲染包含 Skill 名称和正文。
3. 测试目录型 Skill 渲染包含资源根提示但不包含附属文件内容。
4. 测试 Anthropic 输出中存在 Skill 上下文 system block。
5. 测试 OpenAI 输出中存在 Skill 上下文 developer message。

**验证：** 运行 `mvn -q -Dtest=SkillPromptRendererTest test`，测试通过。

## T25: 实现 LoadSkill 工具

**文件：** `src/main/java/com/lunacode/tool/LoadSkillTool.java`

**依赖：** T16、T21

**步骤：**
1. 新建 `LoadSkillTool` 并实现现有 Tool 接口。
2. 定义输入 schema，包含必填 `name` 和可选 `arguments`。
3. 执行时构造 `SkillInvocationRequest`，触发来源为 `TOOL`。
4. 调用 `SkillInvocationPlanner` 生成 `SkillInvocationPlan`。
5. 把 `LoadedSkillContext` 写入本轮作用域。
6. 工具结果返回 Skill 名称、渲染后的 SOP 和目录资源根提示。
7. 找不到 Skill 或解析失败时返回可读错误，不导致 Agent loop 崩溃。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T26: 编写 LoadSkill 工具测试

**文件：** `src/test/java/com/lunacode/tool/LoadSkillToolTest.java`

**依赖：** T25

**步骤：**
1. 测试传入 `name` 后工具返回完整 SOP。
2. 测试传入 `arguments` 后 SOP 中 `$ARGUMENTS` 被替换。
3. 测试目录型 Skill 的返回结果包含资源根提示。
4. 测试不存在 Skill 时返回错误结果。
5. 测试工具执行会设置本轮 `LoadedSkillContext`。

**验证：** 运行 `mvn -q -Dtest=LoadSkillToolTest test`，测试通过。

## T27: 扩展工具声明白名单过滤

**文件：** `src/main/java/com/lunacode/tool/ToolRegistry.java`、`src/main/java/com/lunacode/tool/DefaultToolRegistry.java`

**依赖：** T4

**步骤：**
1. 扩展工具声明方法，接收可选 `ToolAccessPolicy`。
2. 没有工具策略时保持现有声明行为。
3. 有白名单时只声明白名单内工具。
4. 始终声明 `ToolAccessPolicy.alwaysVisibleTools` 中存在的工具。
5. 对 deferred tool 发现列表应用同样过滤规则。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T28: 扩展工具执行白名单校验

**文件：** `src/main/java/com/lunacode/tool/AgentToolRunner.java`

**依赖：** T27

**步骤：**
1. 让工具执行路径可以读取本次调用的 `ToolAccessPolicy`。
2. 执行工具前判断工具名是否被允许。
3. 白名单外工具返回与工具不可用一致的受控错误。
4. `LoadSkill` 等系统级例外工具始终允许。
5. 不改变没有工具策略的普通对话行为。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T29: 扩展 AgentRequest 和 AgentRunConfig

**文件：** `src/main/java/com/lunacode/agent/AgentRequest.java`、`src/main/java/com/lunacode/runtime/AgentRunConfig.java`

**依赖：** T4、T21

**步骤：**
1. 在 `AgentRequest` 中增加用户可见消息和模型可见消息。
2. 保留普通对话的兼容构造方式，让两种消息默认相同。
3. 在 `AgentRunConfig` 中增加可选 `ToolAccessPolicy`。
4. 在 `AgentRunConfig` 中增加可选模型覆盖。
5. 在 `AgentRunConfig` 中增加本轮 Skill prompt 上下文。
6. 确保已有调用点仍能编译。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T30: 接入 Agent loop 调用级 prompt 和模型覆盖

**文件：** `src/main/java/com/lunacode/agent/DefaultAgentLoop.java`

**依赖：** T23、T29

**步骤：**
1. 主历史写入 `AgentRequest` 的用户可见消息。
2. 构建首轮模型消息时使用模型可见消息替换本轮用户消息。
3. 调用工具声明时传入 `AgentRunConfig` 的工具策略。
4. 调用工具执行时传入同一工具策略。
5. 存在模型覆盖时派生 provider 配置，只替换模型名。
6. 调用结束后不修改全局默认模型配置。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T31: 实现 LoadSkill 工具结果清理

**文件：** `src/main/java/com/lunacode/agent/DefaultAgentLoop.java`

**依赖：** T25、T30

**步骤：**
1. 识别本轮 `LoadSkill` 工具调用产生的工具结果消息。
2. Agent loop 完成后，把包含完整 SOP 的工具结果替换成短标记。
3. 短标记保留 Skill 名称、加载成功状态和时间点。
4. 替换失败时记录警告，但不影响已经完成的用户响应。
5. 保证下一次普通请求不会再次看到完整 SOP。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T32: 编写 Agent loop Skill 测试

**文件：** `src/test/java/com/lunacode/agent/SkillAgentLoopTest.java`

**依赖：** T31

**步骤：**
1. 测试 inline Skill 主历史保存用户可见调用。
2. 测试模型本轮接收渲染后的 Skill prompt。
3. 测试工具声明按白名单过滤。
4. 测试白名单外工具执行被拒绝。
5. 测试设置模型覆盖时只影响本次调用。
6. 测试 `LoadSkill` 完成后完整 SOP 从持久工具结果中清理。

**验证：** 运行 `mvn -q -Dtest=SkillAgentLoopTest test`，测试通过。

## T33: 实现 fork 上下文构建

**文件：** `src/main/java/com/lunacode/skill/SkillForkContextBuilder.java`、`src/test/java/com/lunacode/skill/SkillForkContextBuilderTest.java`

**依赖：** T4

**步骤：**
1. `NONE` 策略生成空子对话上下文。
2. `RECENT` 策略复制最近 5 条可发送 API 消息。
3. `FULL` 策略调用现有摘要能力生成完整主对话摘要。
4. 摘要成功时把摘要作为子对话初始上下文。
5. 摘要失败时返回受控失败，不回退为完整原文。
6. 添加测试覆盖三种策略和摘要失败。

**验证：** 运行 `mvn -q -Dtest=SkillForkContextBuilderTest test`，测试通过。

## T34: 实现 fork runner

**文件：** `src/main/java/com/lunacode/skill/DefaultSkillForkRunner.java`

**依赖：** T30、T33

**步骤：**
1. 创建隔离的 `ConversationManager` 作为子对话。
2. 按 `SkillInvocationPlan.definition.context` 初始化子对话上下文。
3. 使用渲染后的 prompt 运行 Agent loop。
4. 收集子对话最终回复、产物路径和后续建议。
5. 生成 `SkillForkResult`。
6. 不把子对话完整消息写入主历史。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T35: 接入 orchestrator 的 Skill 调用入口

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`

**依赖：** T19、T30、T34

**步骤：**
1. 实现 `CommandRuntime.submitSkillInvocation`。
2. 调用 `SkillInvocationPlanner` 生成计划。
3. `INLINE` 模式构造调用级 `AgentRequest` 并复用主 Agent loop。
4. `FORK` 模式设置前台 busy 状态并运行 `SkillForkRunner`。
5. fork 完成后向主历史写入一条 assistant 总结。
6. fork 失败时显示可读错误并恢复 busy 状态。
7. 调用结束后丢弃工具策略、模型覆盖和完整 SOP 上下文。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T36: 编写 orchestrator Skill 集成测试

**文件：** `src/test/java/com/lunacode/orchestrator/SkillOrchestratorTest.java`

**依赖：** T35

**步骤：**
1. 测试 `/commit 参数` 会进入 inline Skill 流程。
2. 测试 `/commit` 无参数时仍执行 Skill。
3. 测试 fork Skill 执行期间进入 busy 状态。
4. 测试 fork 完成后主历史只包含简短总结。
5. 测试后续普通对话没有继承 fork 或 inline 的工具白名单。
6. 测试后续普通对话没有继承 Skill 的模型覆盖。
7. 测试现有 `/review` 仍走硬编码命令。

**验证：** 运行 `mvn -q -Dtest=SkillOrchestratorTest test`，测试通过。

## T37: 接入应用启动装配

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`

**依赖：** T15、T20、T25、T35

**步骤：**
1. 启动时创建项目级、用户级和内置级 Skill source。
2. 创建 `DefaultSkillCatalog`。
3. 注册 `LoadSkillTool`，并确保它作为系统级工具存在。
4. 调用 `SkillCommandRegistrar` 注册动态 Skill 命令。
5. 将用户级 Skill 根作为受控只读资源根接入现有路径策略。
6. 启动诊断输出坏 Skill、冲突和工具缺失警告。

**验证：** 运行 `mvn -q -DskipTests compile`，编译通过。

## T38: 补充路径和资源访问测试

**文件：** `src/test/java/com/lunacode/skill/DefaultSkillCatalogTest.java`、`src/test/java/com/lunacode/orchestrator/SkillOrchestratorTest.java`

**依赖：** T37

**步骤：**
1. 构造用户级目录型 Skill。
2. 断言 `SKILL.md` 进入 SOP，附属资源不进入 prompt。
3. 断言资源根被记录并能被路径策略识别。
4. 断言 Bash 执行附属脚本仍走现有权限路径。

**验证：** 运行 `mvn -q -Dtest=DefaultSkillCatalogTest,SkillOrchestratorTest test`，测试通过。

## T39: 运行局部测试组合

**文件：** `src/test/java/com/lunacode/skill/FrontmatterSkillParserTest.java`、`src/test/java/com/lunacode/skill/FileSystemSkillSourceTest.java`、`src/test/java/com/lunacode/skill/DefaultSkillCatalogTest.java`、`src/test/java/com/lunacode/skill/SkillInvocationPlannerTest.java`、`src/test/java/com/lunacode/tool/LoadSkillToolTest.java`、`src/test/java/com/lunacode/command/SkillCommandRegistrarTest.java`、`src/test/java/com/lunacode/prompt/SkillPromptRendererTest.java`、`src/test/java/com/lunacode/agent/SkillAgentLoopTest.java`、`src/test/java/com/lunacode/orchestrator/SkillOrchestratorTest.java`

**依赖：** T38

**步骤：**
1. 运行所有新增和修改的 Skill 相关测试类。
2. 查看失败用例，确认失败属于实现问题还是测试夹具问题。
3. 修复失败项。
4. 重新运行同一组测试，直到通过。

**验证：** 运行 `mvn -q -Dtest=FrontmatterSkillParserTest,FileSystemSkillSourceTest,DefaultSkillCatalogTest,SkillInvocationPlannerTest,LoadSkillToolTest,SkillCommandRegistrarTest,SkillPromptRendererTest,SkillAgentLoopTest,SkillOrchestratorTest test`，测试通过。

## T40: 运行全量编译和测试

**文件：** `pom.xml`、`src/main/java/com/lunacode/**`、`src/test/java/com/lunacode/**`

**依赖：** T39

**步骤：**
1. 运行 Maven 全量测试。
2. 若有架构依赖测试失败，按现有包依赖规则调整 Skill 包依赖。
3. 若有旧测试失败，确认新行为是否需要更新测试预期。
4. 修复失败项后重新运行全量测试。

**验证：** 运行 `mvn test`，测试通过。

## T41: 执行 tmux 端到端验证

**文件：** `spec/10/checklist.md`

**依赖：** T40 和已批准的 `checklist.md`

**步骤：**
1. 在 tmux 中启动 LunaCode。
2. 输入 `/help`，观察内置 Skill 命令是否出现。
3. 输入 `/commit 重点关注安全问题`，观察是否按 Skill prompt 执行。
4. 输入一段自然语言请求，观察 Agent 是否能看到 Skill 摘要并按需调用 `LoadSkill`。
5. 创建或修改一个临时项目级 Skill，下一次调用确认热更新生效。
6. 对照 `checklist.md` 逐项验收。

**验证：** tmux 会话中完成 checklist 的端到端场景，所有条目有实际观察结果。

## 执行顺序

```text
T1 -> T2 -> T3 -> T4 -> T5
T5 -> T6 -> T7 -> T8
T3 -> T9 -> T10
T3 -> T11
T7 + T9 + T11 -> T12 -> T13 -> T14 -> T15
T14 -> T16 -> T17
T15 -> T18 -> T19 -> T20
T15 + T4 -> T21 -> T22 -> T23 -> T24
T16 + T21 -> T25 -> T26
T4 -> T27 -> T28
T21 + T4 -> T29 -> T30 -> T31 -> T32
T4 -> T33
T30 + T33 -> T34
T19 + T30 + T34 -> T35 -> T36
T15 + T20 + T25 + T35 -> T37 -> T38
T38 -> T39 -> T40 -> T41
```

