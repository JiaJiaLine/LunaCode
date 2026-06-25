# LunaCode TUI 对话内核 Spec

## 背景

LunaCode 是一个从零开始构建的命令行 AI 编程助手，目标形态类似 Claude Code。本阶段实现一个最小可用但具备正式交互形态的 TUI 对话界面：用户在终端启动 LunaCode 后，进入全屏或准全屏的终端交互界面，通过底部输入框输入指令，并在上方对话区域看到模型回复以流式方式逐步显示。

当前项目尚处于初始化阶段，工作区只有项目说明文档，没有已有代码。现有约束是使用 Java 开发，并以中文回答、中文注释作为项目协作规范。

## 目标

- 提供一个可在终端启动的 LunaCode 程序。
- 启动后进入 TUI 对话界面，而不是普通逐行命令行输出。
- TUI 上方为对话区域，展示用户消息和模型回复。
- 模型回复必须在对话区域中流式显示，而不是等待完整生成后一次性展示。
- TUI 下方提供输入框，用户在这里输入问题或指令。
- TUI 底部提供状态栏，展示当前模型、Provider、Token 用量等运行状态信息。
- 支持多轮对话，LunaCode 在单次运行期间保留对话历史，使模型能基于之前的消息继续回答。
- LunaCode 能根据 YAML 配置选择 OpenAI 或 Anthropic Claude 后端。
- LunaCode 调用模型 API 时使用 SSE 流式接收结果，并能识别响应生命周期事件。
- 对 Claude 流式响应，LunaCode 需要处理完整事件序列：
  - `message_start`：整个响应开始，包含 `input_tokens` 等输入统计信息。
  - `content_block_start`：一个内容块开始，内容块可能是文本或其他类型。
  - `content_block_delta`：内容增量到达，文本需要逐步显示在对话区域。
  - `content_block_stop`：一个内容块结束。
  - `message_delta`：消息级别增量，包含 `output_tokens`、停止原因等信息。
  - `message_stop`：整个响应结束。
- 状态栏需要能根据流式事件更新 Token 用量、当前模型和响应状态。
- Provider 层提供统一抽象，后续可以较容易增加新的模型后端。
- 支持 Claude extended thinking 的配置和请求传递能力。
- 本阶段只做纯对话能力，不实现 tool use、文件操作、代码编辑或自动修改项目文件的 agent 能力。

## 功能需求

- F1: LunaCode 启动后进入 TUI 对话界面，上方为对话区域，下方为输入框，底部为状态栏。
- F2: 用户可以在输入框中输入问题或指令，并提交为一条 user 消息。
- F3: 对话区域展示完整消息列表，包括用户消息、模型回复、流式生成中的 assistant 消息和错误状态消息。
- F4: assistant 回复必须流式显示。消息刚创建时处于 streaming 状态，流式结束后变为 complete，出错后变为 error。
- F5: 状态栏展示当前 Provider、当前模型、Token 用量和响应状态等信息。
- F6: LunaCode 使用 YAML 配置选择模型后端。本阶段支持 OpenAI 和 Anthropic Claude，不支持 DeepSeek。
- F7: 配置文件至少包含 protocol、model、base_url、api_key 四个核心字段。
- F8: Provider 层对外暴露统一流式响应行为，不让 TUI 直接依赖 OpenAI 或 Anthropic 的原始事件格式。
- F9: OpenAI 和 Anthropic 的流式响应都必须映射成 LunaCode 内部统一的流事件模型。具体事件结构在 plan.md 中定义。
- F10: Anthropic Claude 流式响应需要识别完整生命周期事件，包括 message_start、content_block_start、content_block_delta、content_block_stop、message_delta、message_stop。
- F11: LunaCode 内部消息模型分两层：API 层消息只包含 role 和 content；内部层消息包含唯一 ID、状态、时间戳、Token 用量、role 和 content。
- F12: LunaCode 提供对话管理器封装消息列表，并由管理器保证并发安全。
- F13: 外部调用方添加消息时能获得唯一 ID；流式更新时能根据 ID 追加内容；渲染时能获取消息列表快照。
- F14: 对话管理器提供从内部消息列表转换为 API 层消息列表的能力。该转换需要过滤 system 和 error 消息，合并相邻同角色消息，并确保发送给模型的消息以 user 开始、user 与 assistant 交替出现。
- F15: Claude extended thinking 需要能通过配置或请求参数启用，并传递给 Anthropic 后端。
- F16: 本阶段只实现纯对话能力，不实现 tool use、文件操作、代码编辑或自动修改项目文件的 agent 能力。

## 非功能需求

- N1: TUI 在模型流式输出期间仍应保持可刷新，不应因为网络读取阻塞界面渲染。
- N2: 对话管理器必须保证并发安全，避免流式更新、用户输入和界面渲染同时访问消息列表时出现数据竞争或不一致快照。
- N3: Provider 层应与 TUI 层解耦，新增 Provider 时不需要修改 TUI 的流式渲染逻辑。
- N4: API 层消息必须保持简单干净，只包含与 LLM 通信必要的信息，避免把内部状态、时间戳、UI 状态泄漏给 Provider。
- N5: Token 用量和响应状态应尽可能从 Provider 流式事件中实时更新；当某个 Provider 无法提供完整用量时，状态栏应能展示未知或部分统计，而不是失败。
- N6: 配置中的 API Key 不应被打印到日志、错误信息、TUI 状态栏或最终报告中。
- N7: 网络异常、认证失败、Provider 返回错误、流式事件解析失败时，当前 assistant 消息应进入 error 状态，并在对话区域显示可理解的错误摘要。
- N8: 项目应提供可自动运行的验证方式，至少覆盖对话管理器、API 格式转换、Provider 流事件映射和配置解析。
- N9: TUI 应能在常见终端窗口尺寸下正常显示，不出现输入框、状态栏和对话区域互相覆盖。

## 不做的事

- 本阶段不实现 tool use，包括函数调用、工具注册、工具执行和工具结果回传。
- 本阶段不实现文件读取、文件写入、代码编辑、补丁生成或自动修改项目文件的 agent 能力。
- 本阶段不实现 DeepSeek Provider。
- 本阶段不实现长期记忆、向量检索、项目索引或跨进程持久化对话历史。
- 本阶段不实现多会话管理；只要求单次启动期间的一条对话线程可用。
- 本阶段不实现复杂命令系统，例如 slash commands、插件系统、任务队列或后台 agent。
- 本阶段不要求精确 Token 计算；Token 用量以 Provider 返回的统计为准。
- 本阶段不要求完整 Markdown 渲染；模型回复可以先按纯文本或基础换行显示。
- 本阶段不实现鼠标交互、富文本选择、复制按钮或复杂布局主题。
- 本阶段不要求兼容所有终端模拟器；优先保证常见 Windows 终端和现代 ANSI 终端可用。

## 验收标准

- AC1: 启动 LunaCode 后，终端显示 TUI 界面，能清楚区分上方对话区域、下方输入框和底部状态栏。
- AC2: 用户在输入框输入一条消息并提交后，对话区域出现对应 user 消息。
- AC3: LunaCode 根据 YAML 配置选择 OpenAI 或 Anthropic Claude Provider，并在状态栏展示当前 Provider 和模型。
- AC4: Provider 返回流式内容时，对话区域中的 assistant 消息逐步追加内容，而不是等完整响应结束后一次性显示。
- AC5: assistant 消息在流式开始时状态为 streaming，正常结束后变为 complete，流式过程出错时变为 error。
- AC6: Anthropic Claude 的 message_start、content_block_start、content_block_delta、content_block_stop、message_delta、message_stop 事件能被识别并映射为内部流事件。
- AC7: OpenAI 的流式响应能被识别并映射为同一套内部流事件。
- AC8: 状态栏能根据 Provider 返回的信息展示或更新 Token 用量；当用量未知时显示明确的未知状态。
- AC9: 对话管理器添加消息时返回唯一 ID，追加流式内容时按 ID 更新指定消息，渲染时返回不会被外部修改影响的消息快照。
- AC10: 对话管理器在并发添加、追加和读取快照时不出现异常、数据丢失或外部可见的半更新结构。
- AC11: 对话管理器转换 API 格式时过滤 system 和 error 消息，合并相邻同角色消息，并保证结果以 user 开始、user 与 assistant 交替出现。
- AC12: API 层消息只包含 role 和 content，不包含内部 ID、状态、时间戳、usage 或 UI 字段。
- AC13: 配置文件缺失必需字段或 API Key 无效时，LunaCode 给出可理解错误，并且不泄露 API Key。
- AC14: Claude extended thinking 启用后，请求参数中包含对应能力开关或预算配置。
- AC15: 使用 checklist 中指定的端到端场景运行 LunaCode，可以完成一次真实或模拟的多轮流式对话。