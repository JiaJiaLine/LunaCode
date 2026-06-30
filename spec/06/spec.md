# LunaCode MCP 客户端工具接入 Spec

## 背景

LunaCode 当前已经具备终端 TUI 对话、内置工具注册、Agent Loop、多轮工具调用、Plan Mode、权限系统和结构化 System Prompt。现有工具主要在启动时由应用直接注册，工具来源固定，Agent 看到的是 LunaCode 内部提供的一组工具声明。

下一阶段需要让 LunaCode 能接入外部 MCP Server，把第三方能力按 MCP 协议发现出来，并包装成 LunaCode 工具中心中的普通工具。用户只需要在配置文件中声明 MCP Server 列表，LunaCode 启动时自动完成连接、初始化、工具发现和注册；后续 Agent 选择和调用这些工具时，不需要知道工具来自本地内置实现还是远端 MCP Server。

用户初步想法中出现的 MewCode 在本文统一按当前项目名 LunaCode 处理。本阶段只聚焦 MCP 的工具能力，不扩展资源、提示词、采样等非工具能力，也不实现 Server 健康检查和自动重连。

## 目标

- LunaCode 启动时从用户级配置和项目级配置读取 MCP Server 列表，并按后加载覆盖先加载的规则合并。
- LunaCode 支持通过本地 stdio 子进程和远程 Streamable HTTP 两种传输连接 MCP Server。
- LunaCode 按 JSON-RPC 2.0 与 MCP Server 通信，能够正确关联带 id 的请求和响应，支持同一连接内多个未完成请求的异步配对。
- LunaCode 对每个可用 MCP Server 执行初始化握手、列出工具和工具调用三类核心流程。
- LunaCode 将发现到的远端工具包装成现有工具中心可注册、可声明、可执行的工具，让 Agent 调用时无感。
- LunaCode 对多个 MCP Server 的连接做缓存和生命周期管理，单个 Server 初始化失败、断开或工具调用失败时不影响其他 Server 和内置工具。
- LunaCode 在 MCP 配置、连接事件、工具注册和工具调用失败时提供可观察、可理解的中文错误摘要，并避免泄露环境变量值、请求头密钥或其他敏感配置。

## 功能需求

- F1: LunaCode 支持在配置文件中使用一个 map 声明 MCP Server 列表。map 的每个 key 是 Server 名字，Server 名字用于日志、错误摘要、连接缓存和远端工具命名空间。
- F2: LunaCode 支持用户级配置和项目级配置两层 MCP Server 合并。相同 Server 名字在后加载配置中出现时，后加载配置完整覆盖先加载配置中同名 Server 的声明；不同 Server 名字并集合并。
- F3: LunaCode 支持 stdio 类型 MCP Server 配置。stdio Server 必须声明 command，可选声明 args 和 env；env 的值支持 `${VAR}` 环境变量展开。
- F4: LunaCode 支持 Streamable HTTP 类型 MCP Server 配置。HTTP Server 必须声明 url，可选声明 headers；headers 的值支持 `${VAR}` 环境变量展开。
- F5: 环境变量展开只支持完整值形式的 `${VAR}`。变量不存在或展开后为空时，该 Server 配置加载失败并被跳过，其他 Server 继续加载。
- F6: 某个 MCP Server 的配置格式错误、启动失败、握手失败或列工具失败时，LunaCode 标记该 Server 不可用并记录原因，但应用启动、内置工具和其他 MCP Server 不受影响。
- F7: stdio 传输启动本地子进程后，通过标准输入和标准输出收发 MCP JSON-RPC 消息。协议消息使用 UTF-8 文本逐条传输；标准错误只作为诊断信息采集，不参与协议解析。
- F8: stdio Server 进程退出、输出非法 JSON、关闭管道或在超时时间内没有响应时，LunaCode 将该 Server 标记为断开，并把所有未完成请求完成为结构化错误。
- F9: Streamable HTTP 传输使用 MCP Streamable HTTP 方式发送 JSON-RPC 请求，并能处理普通 JSON 响应和 SSE 流式响应。需要会话头或协议版本头时，LunaCode 按握手结果保存并在后续请求中携带。
- F10: HTTP Server 返回非成功状态码、协议错误、非法 JSON、超时或网络异常时，LunaCode 只让对应 Server 或对应工具调用失败，不影响其他 Server。
- F11: 每个 Server 连接建立后，LunaCode 先发送初始化请求，校验协议版本、Server 信息和能力声明，再发送初始化完成通知。初始化未完成前，不允许执行列工具或调用工具。
- F12: 初始化完成后，LunaCode 调用列工具能力发现远端工具。若 Server 未声明工具能力或列工具返回失败，该 Server 不注册远端工具。
- F13: 列工具支持分页结果。Server 返回后续游标时，LunaCode 继续拉取，直到收集完整工具列表或遇到错误。
- F14: LunaCode 根据 MCP 工具的名字、描述和输入 schema 生成工具中心可声明的工具。远端工具对 Agent 暴露的名字必须稳定、唯一，并带有 Server 命名空间，避免覆盖内置工具或其他 Server 的同名工具。
- F15: LunaCode 保存远端工具公开名和 MCP 原始工具名之间的映射。Agent 调用公开名时，LunaCode 按映射向对应 Server 发送原始工具名和参数。
- F16: MCP 工具声明缺少描述、输入 schema 为空或 schema 不完整时，LunaCode 仍可注册一个保守的工具声明；若 schema 无法作为对象参数使用，该工具应被跳过并记录原因。
- F17: Agent 调用 MCP 工具时，LunaCode 向对应 Server 发送工具调用请求，参数使用 Agent 提供的 JSON 对象，并按 JSON-RPC id 关联返回结果。
- F18: MCP 工具调用成功时，LunaCode 将远端返回内容转换为普通工具结果。文本内容直接进入结果正文；结构化内容以可读 JSON 摘要呈现；非文本内容不得导致崩溃，应以简短占位说明和元数据摘要呈现。
- F19: MCP 工具调用失败时，LunaCode 将协议错误、远端工具错误、超时和连接断开转换为普通工具错误结果，并包含 Server 名、远端工具名和简短失败原因。
- F20: LunaCode 的 JSON-RPC 客户端为每个外发请求生成唯一 id，并维护未完成请求表。响应到达时必须按 id 精确完成对应请求；未知 id、重复 id 或缺失 id 的响应不得误完成其他请求。
- F21: LunaCode 能接收和忽略本阶段不支持的通知。Server 向客户端发起本阶段不支持的请求时，LunaCode 返回明确的 JSON-RPC 错误，而不是让连接静默卡死。
- F22: 同一 Server 连接在启动发现后被缓存复用。多次调用同一远端工具或同一 Server 的不同工具时，不应重复初始化或重复列工具。
- F23: LunaCode 退出时会关闭所有 MCP 连接。stdio Server 子进程应被正常关闭；仍未结束的进程在合理等待后被终止。HTTP 会话应释放本地资源。
- F24: 没有自动重连。Server 在启动发现后断开时，后续调用该 Server 的工具返回连接不可用错误，用户需要重启 LunaCode 或修改配置后重新启动。
- F25: MCP 远端工具必须走 LunaCode 现有工具执行路径。工具结果、错误结果、权限判断、事件展示和 Agent Loop 后续迭代的行为应与内置工具保持一致。
- F26: MCP 远端工具默认按可能有副作用的外部工具处理。除非 LunaCode 明确有可信依据判断为只读，否则不应因为远端声明就绕过权限确认或安全策略。
- F27: LunaCode 的工具列表中可以同时包含内置工具和多个 Server 暴露的 MCP 工具。单个 Server 失败不能让内置工具或其他 Server 工具从工具列表中消失。
- F28: 配置中的 env、headers、url 中可能包含敏感信息。错误摘要、工具结果、事件流和普通回复不得完整泄露 API Key、Token、Authorization 头或展开后的环境变量值。
- F29: LunaCode 提供配置示例，展示 stdio Server、HTTP Server、env 展开、headers 展开和用户级/项目级覆盖的写法。

## 非功能需求

- N1: MCP 协议实现应尽量贴合官方当前 Streamable HTTP、stdio、生命周期和工具能力规范；若协议细节在实现时有版本差异，应优先保持向后兼容和清晰报错。
- N2: MCP 客户端通信必须有超时控制。初始化、列工具和工具调用都不能无限阻塞 Agent Loop 或 TUI。
- N3: 多 Server 启动发现应具备故障隔离。一个 Server 慢、失败或返回异常数据时，不应拖垮整个应用启动流程。
- N4: JSON-RPC 请求配对逻辑必须可测试、确定且线程安全。并发响应乱序返回时，结果仍应交给正确的调用方。
- N5: 远端工具注册和调用应与 Provider 无关。Anthropic 和 OpenAI 工具声明适配仍只依赖工具中心输出，不感知 MCP 传输细节。
- N6: MCP 配置解析应保持保守失败策略。单个 Server 配置失败时跳过该 Server；主配置文件整体不可解析时仍按现有配置错误处理。
- N7: Server 名、工具名和公开工具名应做格式校验，避免生成模型供应商不接受的工具名，避免路径分隔符、空白字符或控制字符进入公开工具名。
- N8: 远端工具描述和错误内容可能不可信。LunaCode 展示或拼接这些内容时应限制长度，并避免让远端内容破坏工具声明、日志结构或普通回复格式。
- N9: MCP 能力应有自动化测试覆盖，包括配置合并、环境变量展开、stdio 通信、HTTP 通信、初始化、列工具、调用工具、异步 id 配对、失败隔离和敏感值遮蔽。
- N10: 本阶段不改变 LunaCode 的中文协作风格。用户可见的配置错误、连接错误、工具调用错误和端到端验收说明应使用中文。

## 不做的事情

- 本阶段不实现 MCP 资源能力。
- 本阶段不实现 MCP 提示词能力。
- 本阶段不实现 MCP 采样能力，也不允许 MCP Server 反向驱动 LunaCode 调用模型。
- 本阶段不实现 Server 健康检查、后台心跳、自动重连或连接池热替换。
- 本阶段不实现动态工具变更订阅。启动后 Server 工具列表变化，需要重启 LunaCode 才能重新发现。
- 本阶段不实现 OAuth、浏览器授权流程、密钥管理器或交互式认证；远程 Server 认证仅通过配置中的 headers 表达。
- 本阶段不实现 MCP Server 市场、插件安装器或图形化配置界面。
- 本阶段不支持未在配置文件中声明的自动 Server 发现。
- 本阶段不把 MCP Server 的文件系统权限、网络权限或进程隔离当作 LunaCode 的内部沙箱替代品。

## 验收标准

- AC1: 同时存在用户级和项目级 MCP 配置时，LunaCode 启动后能观察到两层 Server 已合并；同名 Server 以项目级声明为准，不同名 Server 同时存在。
- AC2: stdio Server 配置包含 command、args 和 env 时，LunaCode 能展开 env 中的 `${VAR}`，启动子进程，完成初始化和列工具，并在工具中心看到对应远端工具。
- AC3: HTTP Server 配置包含 url 和 headers 时，LunaCode 能展开 headers 中的 `${VAR}`，通过 Streamable HTTP 完成初始化和列工具，并在工具中心看到对应远端工具。
- AC4: 某个 Server 引用不存在的环境变量时，启动日志或事件中能看到该 Server 被跳过的中文原因；其他 Server 和内置工具仍可用。
- AC5: 两个 Server 暴露同名工具，或 MCP 工具与内置工具同名时，LunaCode 暴露给模型的工具名仍然唯一稳定，且不会覆盖内置工具。
- AC6: Agent 调用一个 MCP 远端工具时，LunaCode 向正确 Server 发送工具调用请求，使用远端原始工具名和 Agent 提供的参数，并把成功结果作为普通工具结果回灌给 Agent Loop。
- AC7: MCP 工具返回结构化内容或非文本内容时，LunaCode 不崩溃；Agent 能收到可读摘要，事件或元数据中能看到内容类型摘要。
- AC8: MCP 工具返回远端错误、协议错误或超时时，Agent 收到普通工具错误结果，错误中包含 Server 名、工具名和失败原因摘要，Agent Loop 不直接崩溃。
- AC9: 同一 Server 上两个工具调用并发发出且响应乱序返回时，LunaCode 能按 JSON-RPC id 将结果分别交给正确的工具调用。
- AC10: 一个 MCP Server 在运行中断开后，调用该 Server 的工具会返回连接不可用错误；其他 MCP Server 的工具和内置工具仍可继续调用。
- AC11: 重复调用同一 MCP 工具时，LunaCode 复用启动时缓存的连接，不重复执行初始化握手和列工具流程。
- AC12: 退出 LunaCode 后，stdio MCP Server 子进程不应残留为孤儿进程；未完成请求会被清理为失败结果或随应用退出释放。
- AC13: MCP 远端工具调用经过现有权限路径。需要确认的远端工具会触发权限确认；用户拒绝后，远端工具不会执行，Agent Loop 收到结构化拒绝结果。
- AC14: 错误摘要、事件流、工具结果和普通回复中不出现展开后的 API Key、Token、Authorization 头或配置中的敏感环境变量值。
- AC15: 配置示例中包含一个 stdio Server 示例和一个 HTTP Server 示例，且示例能说明用户级/项目级覆盖规则。
- AC16: 自动化测试覆盖配置合并、环境变量展开、初始化握手、列工具分页、工具调用、JSON-RPC id 配对、单 Server 失败隔离和敏感值遮蔽。
- AC17: 使用 tmux 启动 LunaCode，输入真实请求“调用配置中的 MCP 工具获取一段测试数据，然后总结结果”，可以观察到 LunaCode 自动使用远端工具、返回结果并继续中文回复。
