# SubAgent 与后台任务 Spec

## 背景

LunaCode 已经具备 Agent 循环、工具调用、权限审批、上下文压缩、Skill fork 和 Hook 机制。当前 Skill fork 只能以前台阻塞方式运行独立对话，Hook 的 `sub_agent` 动作也只是占位，不会真实启动子 Agent。主 Agent 需要一种稳定能力，把可并行、可隔离的工作委派给独立子 Agent，避免子任务的消息、工具结果和中间推理污染主对话上下文。

本阶段要提供统一的 `Agent` 工具和后台任务生命周期。主 Agent 通过同一个工具发起定义式子 Agent 或 Fork 式子 Agent；定义式子 Agent 按预定义角色从空白对话启动；Fork 式子 Agent 继承父对话原始历史和工具集，并无条件后台运行，以便首次请求尽量命中 prompt cache。子 Agent 以非交互“跑到底”模式执行，完成后把最终结果异步通知回主对话。

## 目标

- 提供一个稳定的 `Agent` 工具入口，让主 Agent 可以委派子任务。
- 支持定义式和 Fork 式两条子 Agent 路径，工具列表对模型保持稳定。
- 支持 Markdown 加 YAML frontmatter 的角色定义，并按多来源优先级加载。
- 隔离子 Agent 的消息、权限追踪、文件读缓存和 token 计数，同时共享基础设施。
- 支持统一后台任务生命周期，覆盖显式后台、自动超时后台、ESC 手动后台和 Fork 隐式后台。
- 接通 Hook 的 `sub_agent` 动作，让 Hook 可以真实启动子 Agent。
- 防止子 Agent 无限嵌套，确保 Fork 不能再 Fork，后台 Agent 不能再 spawn Agent。

## 功能需求

- F1: 系统必须提供一个模型可调用的 `Agent` 工具；工具名称保持稳定，不因为新增或删除角色定义而变化。
- F2: 调用 `Agent` 工具时，如果未指定 `subagent_type`，必须走 Fork 式子 Agent 路径。
- F3: 调用 `Agent` 工具时，如果指定 `subagent_type`，必须按该值查找已加载的定义式子 Agent；找不到时返回可诊断的工具错误。
- F4: `Agent` 工具必须接收子任务描述，并把它作为子 Agent 的本次任务输入。
- F5: `Agent` 工具必须支持 `run_in_background: true`；设置后直接创建后台任务，主 Agent 立刻收到 `async_launched` 状态和后台任务 id。
- F6: 定义式子 Agent 的角色文件必须使用 Markdown 加 YAML frontmatter；frontmatter 中的 `name` 加载后映射为内部 `agentType`，`description` 加载后映射为内部 `whenToUse`。
- F7: 角色定义必须支持 `tools`、`disallowedTools`、`model`、`maxTurns`、`permissionMode` 字段；Markdown 正文必须作为子 Agent 生命周期内持续生效的系统提示。
- F8: 加载后的角色定义必须记录角色来源和定义文件路径，便于诊断同名覆盖和配置错误。
- F9: 角色定义必须从四类来源加载：项目级 `.lunacode/agents/`、用户级 `~/.lunacode/agents/`、内置、插件。
- F10: 同名角色定义按项目级、用户级、内置、插件的优先级覆盖；高优先级有效定义存在时，低优先级同名定义不生效。
- F11: 单个角色定义缺少 `name` 或 `description`、frontmatter 解析失败、字段类型非法、权限模式非法、模型别名无法解析或工具字段非法时，该定义必须跳过并给出可观察警告，不影响 LunaCode 启动和其他角色定义。
- F12: 定义式子 Agent 必须从空白对话启动，只携带角色系统提示和本次任务输入，不继承父对话消息历史。
- F13: Fork 式子 Agent 必须完整继承父对话原始消息历史和当前可用工具集，优先保证首次请求可以复用 provider prompt cache。
- F14: Fork 式子 Agent 必须无条件后台运行；主 Agent 不等待其完成，只收到后台任务 id 和启动状态。
- F15: 子 Agent 运行时必须隔离消息历史、权限追踪、文件读缓存和 token 计数。
- F16: 子 Agent 运行时必须共享 LLM 客户端、Hook 引擎和文件系统访问基础设施；本阶段不做 worktree 文件隔离。
- F17: 子 Agent 必须以非交互“跑到底”模式执行；当模型不再发起工具调用时，视为完成并产出最终结果。
- F18: 子 Agent 达到自身最大轮次后必须停止，并把“达到最大轮次”的状态写入结果或失败原因。
- F19: 定义式子 Agent 的 `maxTurns` 未设置时，必须使用主运行配置中的默认最大轮次；设置后必须覆盖该子 Agent 的最大轮次。
- F20: 子 Agent 需要用户确认权限时，第一版必须直接拒绝对应工具调用，并把拒绝原因写入子任务结果；不得暂停等待用户交互。
- F21: 定义式子 Agent 的 `permissionMode` 未设置时，必须继承主 Agent 当前权限模式；设置后必须覆盖该子 Agent 的权限模式。
- F22: 定义式子 Agent 的 `model` 为 `inherit` 或未设置时，必须继承主 Agent 当前模型。
- F23: 定义式子 Agent 的 `model` 为 `sonnet`、`opus`、`haiku` 时，必须通过配置中的模型别名映射到具体模型名。
- F24: 定义式子 Agent 的最终工具集必须先由 `tools` 白名单或父工具集确定，再移除 `disallowedTools`；同一工具同时出现在两处时，黑名单优先。
- F25: 子 Agent 工具过滤必须至少包含三层防线：全局禁止工具、角色限制和后台安全白名单。
- F26: 后台安全层必须禁止后台 Agent 调用 `Agent` 工具，即使角色定义显式允许也必须移除。
- F27: Fork 式子 Agent 不能再走 Fork 路径；后台 Agent 不能再 spawn 任何 Agent。
- F28: 如果子 Agent 尝试调用被过滤掉的工具，必须收到工具不可用的错误结果，而不是让调用绕过过滤层。
- F29: 系统必须提供统一后台任务生命周期；后台任务至少包含 id、子 Agent、任务描述、状态、结果、开始时间、结束时间、取消函数和进度信息。
- F30: 后台任务状态第一版必须支持 `running`、`completed`、`failed`。
- F31: 后台任务进度必须至少追踪工具调用次数、token 消耗和最近活动。
- F32: 后台任务管理器必须提供创建后台任务的统一入口：生成唯一任务 id，登记为 `running`，异步运行子 Agent 到完成，并立即返回任务 id。
- F33: 后台任务中的子 Agent 崩溃或抛出异常时，任务状态必须变为 `failed`，并记录失败原因，不得影响主程序继续运行。
- F34: 后台任务完成或失败后，必须通过通知通道把任务 id 推送给主 Agent 的消息循环。
- F35: 主 Agent 消息循环收到后台通知后，必须向主对话注入一条 `<task-notification>` 消息，不打断当前对话。
- F36: `<task-notification>` 必须包含任务 id、状态、摘要和完整结果；子 Agent 的完整中间过程不得进入主历史。
- F37: 前台运行的定义式子 Agent 如果超过自动后台阈值仍未完成，系统必须自动把它切入后台；默认阈值为 120 秒，并由 `getAutoBackgroundMs()` 控制。
- F38: 当前台正在运行子 Agent 时，用户按 ESC 必须把该子 Agent 手动切入后台，让主对话恢复可继续交互。
- F39: 前台切后台必须通过接管运行中任务完成，不得杀掉子 Agent 后重启；系统必须保留运行中的 Agent 实例、事件流、取消函数和已收集的部分结果。
- F40: 后台任务管理器必须能接管一个已运行的前台子 Agent，并继续在后台消费事件流直到完成。
- F41: `Agent` 工具前台完成时，必须直接返回子 Agent 最终结果；进入后台时，必须返回 `async_launched` 状态和任务 id。
- F42: Hook 的 `sub_agent` 动作必须从占位升级为真实执行，并复用同一套子 Agent 和后台任务基础设施。
- F43: Hook 触发的 `sub_agent` 动作必须以后台任务方式运行，完成后通过同一条 `<task-notification>` 机制回流。
- F44: Hook 的 `sub_agent` 动作必须沿用已配置的动作字段作为子 Agent 类型和任务输入；指定的子 Agent 不存在时，必须记录 Hook 动作失败并给出可诊断原因。
- F45: 现有 Skill fork 必须保持用户可见兼容；如果迁移到底层子 Agent 基础设施，仍必须保持 fork Skill 只向主历史回流简短结果的既有语义，除非本章明确改变。

## 非功能需求

- N1: 子 Agent 中间消息、工具结果和权限记录不得污染主对话历史。
- N2: Fork 式子 Agent 的父历史继承必须保留原始消息顺序和内容，避免破坏 prompt cache 命中机会。
- N3: 后台任务失败必须局部化，不能拖垮主 Agent、Hook Runtime 或整个 LunaCode 进程。
- N4: 工具过滤必须稳定、可诊断；被禁用工具不能出现在子 Agent 可见工具声明中，也不能在执行层绕过。
- N5: 后台通知必须可观察，但不得抢占用户当前输入或自动触发主 Agent 新一轮回复。
- N6: 子 Agent 权限模式、模型覆盖、工具限制和 token 统计必须是子任务级状态，任务结束后不能影响主 Agent。
- N7: 角色定义格式必须易读，用户能通过一个 Markdown 文件理解角色用途、限制和系统提示。
- N8: 后台任务不做跨会话持久化；重启 LunaCode 后，本进程内后台任务状态可以丢失。
- N9: 第一版应复用现有 Agent、工具、权限、Hook 和上下文基础设施，避免引入新的外部服务依赖。

## 不做的事

- 不做 Worktree 文件隔离。
- 不做多 Agent 团队编排、任务拆解调度或 Agent 之间互相协作。
- 不做后台任务跨会话持久化。
- 不允许 Fork 式子 Agent 再 Fork。
- 不允许后台 Agent 再 spawn Agent。
- 不把子 Agent 的完整中间对话写入主历史。
- 不为后台任务提供完整命令管理界面；第一版只要求启动状态、完成通知和结果回流。
- 不做需要用户确认的子 Agent 权限交互；第一版遇到确认请求直接拒绝该工具调用。

## 验收标准

- AC1: 主 Agent 调用 `Agent` 工具且不传 `subagent_type` 时，系统启动 Fork 式子 Agent，并立即返回 `async_launched` 状态和后台任务 id。
- AC2: 主 Agent 调用 `Agent` 工具并传入有效 `subagent_type` 时，系统按对应角色定义启动定义式子 Agent。
- AC3: 主 Agent 调用 `Agent` 工具并传入不存在的 `subagent_type` 时，工具返回包含原因的错误结果。
- AC4: 在项目级、用户级、内置和插件中放置同名角色定义时，系统使用项目级有效定义；删除项目级后用户级生效；删除用户级后内置生效；删除内置后插件生效。
- AC5: 一个角色定义缺少 `name` 或 `description` 时，该定义被跳过并产生可观察警告，其他角色仍可使用。
- AC6: frontmatter 的 `name` 和 `description` 被加载后，主 Agent 可见的是对应的 `agentType` 和 `whenToUse` 语义。
- AC7: 定义式子 Agent 运行时，子对话不包含父对话历史，只包含角色系统提示和本次任务。
- AC8: Fork 式子 Agent 运行时，子对话继承父对话原始消息历史，并继承父 Agent 当前可用工具集后再应用后台安全过滤。
- AC9: 设置 `tools: [Read, Grep, Bash]` 且 `disallowedTools: [Bash]` 的角色运行时，子 Agent 最终不能看到或执行 `Bash`，但可以使用 `Read` 和 `Grep`。
- AC10: 只设置 `disallowedTools: [Agent, Write, Edit]` 的角色运行时，最终工具集会从继承工具集中移除这些工具。
- AC11: 后台 Agent 即使角色允许 `Agent` 工具，也无法看到或执行 `Agent` 工具。
- AC12: Fork 式子 Agent 尝试再次发起 Fork 时，工具调用被拒绝，并返回可诊断原因。
- AC13: 后台 Agent 尝试 spawn 任意 Agent 时，工具调用被拒绝，并返回可诊断原因。
- AC14: 定义式子 Agent 使用 `model: inherit` 时，实际请求使用主 Agent 当前模型。
- AC15: 定义式子 Agent 使用 `model: sonnet`、`opus` 或 `haiku` 时，实际请求使用配置中对应别名映射的具体模型。
- AC16: 定义式子 Agent 设置 `maxTurns` 后，在达到该轮次上限时停止，并把停止原因写入结果或失败原因。
- AC17: 子 Agent 工具调用需要用户确认权限时，该工具调用被直接拒绝，子 Agent 继续按工具错误结果处理，不弹出用户确认。
- AC18: 使用 `run_in_background: true` 调用定义式子 Agent 时，工具立即返回 `async_launched` 和任务 id，主对话可以继续接收输入。
- AC19: 前台定义式子 Agent 在超过 120 秒仍未完成时，系统自动把它切入后台，并向主 Agent 返回后台任务 id。
- AC20: 当前台定义式子 Agent 正在运行时，用户按 ESC 后，系统把该运行实例切入后台，而不是重新启动一个新任务。
- AC21: 前台切后台后，后台任务能继续接收原运行实例的事件流，最终进入 `completed` 或 `failed` 状态。
- AC22: 后台任务正常完成后，任务状态变为 `completed`，记录结束时间、token 消耗、工具调用次数和最终结果。
- AC23: 后台任务执行过程中抛出异常时，任务状态变为 `failed`，失败原因可观察，主程序不中断。
- AC24: 后台任务完成或失败后，主对话收到一条 `<task-notification>` 消息，消息包含任务 id、状态、摘要和完整结果。
- AC25: `<task-notification>` 不包含子 Agent 的完整中间对话、工具调用转录或权限记录。
- AC26: Hook 配置 `sub_agent` 动作并触发后，系统真实启动对应子 Agent 后台任务，不再只写未实现日志。
- AC27: Hook 的 `sub_agent` 动作指定不存在的子 Agent 时，Hook 日志记录失败原因，主 Agent 流程不被未处理异常打断。
- AC28: 现有 fork Skill 调用在本章后仍可使用，并保持主历史只回流简短结果的兼容行为。
- AC29: 本章不提供后台任务跨会话恢复；重启 LunaCode 后，之前进程内的后台任务不要求可查询。
- AC30: 用真实对话端到端测试时，主 Agent 能调用 `Agent` 工具启动子 Agent，子 Agent 能调用允许的工具完成任务，完成后结果异步回到主对话。
