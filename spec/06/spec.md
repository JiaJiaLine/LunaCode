# LunaCode MCP 客户端工具接入 Spec

## 背景

LunaCode 当前已经具备终端 TUI 对话、内置工具注册、Agent Loop、多轮工具调用、Plan Mode、权限系统和结构化 System Prompt。现有工具主要在启动时由应用直接注册，工具来源固定，Agent 看到的是 LunaCode 内部提供的一组工具声明。

下一阶段需要让 LunaCode 能接入外部 MCP Server，把第三方能力按 MCP 协议发现出来，并
包装成 LunaCode 工具中心中的普通工具。用户只需要在配置文件中声明 MCP Server 列表，LunaCode 启动时自动完成连接、初始化、工具发现和注册；后续 Agent 选择和调用这些工具时，不需要知道工具来自本地内置实现还是远端 MCP Server。

用户初步想法中出现的 MewCode 在本文统一按当前项目名 LunaCode 处理。本阶段只聚焦 MCP 的工具能力，不扩展资源、提示词、采样等非工具能力，也不实现 Server 健康检查和自动重连。

## 目标

- LunaCode 启动时从用户级 `~/.lunacode/config.yaml` 和项目级当前启动配置读取 `mcp.servers`，并按项目级覆盖用户级的规则合并。
- LunaCode 支持通过本地 stdio 子进程和远程 Streamable HTTP 两种传输连接 MCP Server。
- LunaCode 把 MCP 传输能力作为独立抽象边界处理。stdio 和 Streamable HTTP 是该传输抽象的两个实现，JSON-RPC 配对、会话生命周期、工具发现和工具调用流程不得绑定到某一种具体传输。
- LunaCode 按 JSON-RPC 2.0 与 MCP Server 通信，能够正确关联带 id 的请求和响应，支持同一连接内多个未完成请求的异步配对。
- LunaCode 对每个可用 MCP Server 执行初始化握手、列出工具和工具调用三类核心流程。
- LunaCode 将发现到的远端工具包装成现有工具中心可注册、可声明、可执行的工具，让 Agent 调用时无感。
- LunaCode 将 MCP Server 视为外部不可信程序。stdio Server 运行在 LunaCode 启动的子进程中，HTTP Server 运行在远端进程中；两者都不能绕过 LunaCode 的工具审批、权限规则和工具执行路径。
- LunaCode 支持 MCP 工具延迟加载。启动发现阶段只让模型知道延迟工具名字，完整 schema 在模型通过 ToolSearch 明确检索后才进入后续轮次的普通工具列表。
- LunaCode 对多个 MCP Server 的连接做缓存和生命周期管理，单个 Server 初始化失败、断开或工具调用失败时不影响其他 Server 和内置工具。
- LunaCode 在 MCP 配置、连接事件、工具注册和工具调用失败时提供可观察、可理解的中文错误摘要，并避免泄露环境变量值、请求头密钥或其他敏感配置。

## 功能需求

- F1: LunaCode 支持在配置文件的 `mcp.servers` 下使用 map 声明 MCP Server 列表。map 的每个 key 是 Server 名字，Server 名字用于日志、错误摘要、连接缓存和远端工具命名。
- F2: LunaCode 支持用户级 `~/.lunacode/config.yaml` 和项目级当前启动配置两层 MCP Server 合并。先加载用户级，再加载项目级；相同 Server 名字在项目级配置中出现时，项目级声明完整覆盖用户级同名 Server；不同 Server 名字并集合并。
- F3: 每个 MCP Server 配置必须能明确判定一种传输类型。配置声明 `command` 时是 stdio Server，声明 `url` 时是 Streamable HTTP Server；同时声明或同时缺失时，该 Server 配置失败并跳过。
- F4: LunaCode 支持 stdio 类型 MCP Server 配置。stdio Server 必须声明 `command`，可选声明 `args` 和 `env`；本阶段不新增 `cwd`，子进程默认在 LunaCode 当前工作区根目录启动。
- F5: LunaCode 支持 Streamable HTTP 类型 MCP Server 配置。HTTP Server 必须声明 `url`，可选声明 `headers`。
- F6: `env`、`headers` 和其他字符串配置值中的 `${VAR}` 支持嵌入式环境变量展开，例如 `Bearer ${TOKEN}` 和 `${HOME}/.cache/lunacode`。任意引用变量不存在或展开后为空时，该 Server 配置失败并跳过，其他 Server 继续加载。
- F7: 某个 MCP Server 的配置格式错误、启动失败、握手失败或列工具失败时，LunaCode 继续启动，标记该 Server 不可用并显示中文警告；内置工具和其他 MCP Server 不受影响。
- F8: LunaCode 启动时允许并发发现多个 MCP Server。每个 Server 独立使用 30 秒超时；全部发现完成或超时后再进入 TUI。
- F9: stdio 传输启动本地子进程后，通过标准输入和标准输出收发 MCP JSON-RPC 消息。协议消息使用 UTF-8 文本按换行分隔逐条传输；标准错误只作为诊断信息采集，不参与协议解析。
- F10: stdio Server 进程退出、输出非法 JSON、关闭管道或在超时时间内没有响应时，LunaCode 将该 Server 标记为断开，并把所有未完成请求完成为结构化错误。
- F11: Streamable HTTP 传输使用 MCP Streamable HTTP 方式发送 JSON-RPC 请求，并能处理普通 JSON 响应和 SSE 流式响应。需要会话头或协议版本头时，LunaCode 按握手结果保存并在后续请求中携带。
- F12: HTTP Server 返回非成功状态码、协议错误、非法 JSON、超时或网络异常时，LunaCode 只让对应 Server 或对应工具调用失败，不影响其他 Server。
- F13: LunaCode 初始化时声明 MCP 协议版本 `2025-06-18`。每个 Server 连接建立后，LunaCode 先发送 `initialize` 请求，校验协议版本、Server 信息和能力声明，再发送 `notifications/initialized` 通知；Server 返回不兼容版本时，该 Server 初始化失败并跳过。
- F14: 初始化完成后，LunaCode 调用 `tools/list` 发现远端工具。若 Server 未声明工具能力或列工具返回失败，该 Server 不注册远端工具。
- F15: `tools/list` 支持分页结果。Server 返回后续游标时，LunaCode 继续拉取，直到收集完整工具列表或遇到错误。
- F16: LunaCode 根据 MCP 工具的名字、描述和输入 schema 生成工具中心可声明的工具。远端工具对 Agent 暴露的公开名格式为 `"mcp_" + serverName + "_" + toolDef.name`，并在必要时做合法化处理，避免模型供应商拒绝或覆盖内置工具。
- F17: 远端工具公开名合法化后如果发生冲突，LunaCode 追加稳定短哈希后缀保证唯一，例如 `mcp_github_search_issues_a1b2c3`，不得静默覆盖已有工具。
- F18: LunaCode 保存远端工具公开名和 MCP 原始工具名之间的映射。Agent 调用公开名时，LunaCode 按映射向对应 Server 发送原始工具名和参数。
- F19: MCP 工具声明缺少描述、输入 schema 为空或 schema 不完整时，LunaCode 仍可注册一个保守的工具声明；若单个工具的 schema 无法作为对象参数使用，只跳过该坏工具并记录中文警告，同一 Server 的其他合法工具继续注册。
- F20: Agent 调用 MCP 工具时，LunaCode 向对应 Server 发送 `tools/call` 请求，参数使用 Agent 提供的 JSON 对象，并按 JSON-RPC id 关联返回结果。
- F21: MCP 工具调用成功时，LunaCode 将远端返回内容转换为普通工具结果。文本内容直接进入结果正文；结构化内容以压缩后的可读 JSON 摘要呈现；图片、二进制资源或未知非文本内容只返回类型、数量、名称等摘要，不落盘、不做富媒体展示。
- F22: MCP 工具调用失败时，LunaCode 将协议错误、远端工具错误、超时和连接断开转换为普通工具错误结果，并包含 Server 名、远端工具名和简短失败原因。
- F23: LunaCode 的 JSON-RPC 客户端为每个外发请求生成唯一 id，并维护未完成请求表。响应到达时必须按 id 精确完成对应请求；未知 id、重复 id 或缺失 id 的响应不得误完成其他请求。
- F24: LunaCode 能接收和忽略本阶段不支持的通知。Server 向客户端发起本阶段不支持的请求时，LunaCode 返回明确的 JSON-RPC 错误，而不是让连接静默卡死。
- F25: 同一 Server 连接在启动发现后被缓存复用。多次调用同一远端工具或同一 Server 的不同工具时，不应重复初始化或重复列工具。
- F26: LunaCode 退出时会关闭所有 MCP 连接。stdio Server 子进程应被正常关闭；仍未结束的进程在合理等待后被终止。HTTP 会话应释放本地资源。
- F27: 没有自动重连。Server 在启动发现后断开时，后续调用该 Server 的工具返回连接不可用错误，用户需要重启 LunaCode 或修改配置后重新启动。
- F28: MCP 远端工具必须走 LunaCode 现有工具执行路径。工具结果、错误结果、权限判断、事件展示和 Agent Loop 后续迭代的行为应与内置工具保持一致。
- F29: MCP 远端工具本阶段默认全部按可能有副作用的外部工具处理，统一走现有权限确认；不信任 MCP Server 自己的描述来自动放行，也不根据远端声明直接标记为只读。
- F30: LunaCode 的工具列表中可以同时包含内置工具和多个 Server 暴露的 MCP 工具。单个 Server 失败不能让内置工具或其他 Server 工具从工具列表中消失。
- F31: 配置中的 env、headers、url 中可能包含敏感信息。错误摘要、工具结果、事件流和普通回复不得完整泄露 API Key、Token、Authorization 头或展开后的环境变量值。
- F32: LunaCode 提供配置示例，展示 `mcp.servers` 下的 stdio Server、HTTP Server、嵌入式环境变量展开和用户级/项目级覆盖写法。
- F33: MCP Server 是 LunaCode 信任边界外的外部程序。stdio Server 由 LunaCode 作为子进程启动，但不因此获得内置信任；HTTP Server 同样按外部不可信端点处理。Server 返回的工具描述、schema、错误内容和工具结果都必须按不可信输入处理。
- F34: MCP 工具和内置工具一样受权限系统管控。Agent 想调用 MCP 工具时，必须先经过 LunaCode 现有权限检查；权限规则可以按 MCP 公开工具名配置，例如 `mcp_github_search_issues(...)`。权限拒绝时，LunaCode 不得向 MCP Server 发送 `tools/call`。
- F35: MCP 工具注册时必须标记为延迟工具。延迟工具的完整 schema 默认不进入模型普通工具列表，但工具仍存在于客户端 Registry 中，可被本地工具检索能力查询。
- F36: Agent Loop 每轮生成模型可见工具列表时，跳过尚未被发现的延迟 MCP 工具完整 schema，只在 system-reminder 中列出这些延迟工具的公开名字和简短说明，提示模型可以按需检索完整定义。
- F37: LunaCode 提供 ToolSearch 工具用于延迟工具发现。模型需要某个延迟 MCP 工具时，先调用 ToolSearch 查询该公开工具名；ToolSearch 只读取客户端 Registry 中已经发现的本地元数据，不调用 MCP Server，不执行远端工具。
- F38: ToolSearch 找到目标 MCP 工具后，返回该工具完整名称、描述和输入 schema，并把该工具标记为已发现。从下一轮 Agent Loop 开始，该工具的完整 schema 进入普通工具列表，模型可以像调用内置工具一样调用它。
- F39: ToolSearch 查询不存在、已禁用、未注册或 schema 无法公开的工具时，返回普通工具错误结果；该错误不得触发 MCP Server 调用，也不得让 Agent Loop 崩溃。
- F40: LunaCode 必须先抽象 MCP 传输层。上层 JSON-RPC 客户端、初始化握手、列工具、调用工具、连接缓存和生命周期管理只依赖统一传输行为，不直接依赖 stdio 子进程或 HTTP 请求细节。
- F41: 同一 LunaCode 会话中可以同时连接本地 stdio Server 和远程 Streamable HTTP Server。不同传输的 Server 在工具注册、权限审批、延迟加载、ToolSearch 和工具调用结果回灌上的用户可见行为应保持一致。

## 非功能需求

- N1: MCP 协议实现应基于官方 `2025-06-18` 版本的 Streamable HTTP、stdio、生命周期和工具能力规范；若 Server 返回不兼容版本，应清晰报错并跳过该 Server。
- N2: MCP 客户端通信必须有超时控制。初始化、列工具和工具调用本阶段统一使用 30 秒超时，不能无限阻塞 Agent Loop 或 TUI。
- N3: 多 Server 启动发现应具备故障隔离并支持并发发现。一个 Server 慢、失败或返回异常数据时，不应拖垮整个应用启动流程。
- N4: JSON-RPC 请求配对逻辑必须可测试、确定且线程安全。并发响应乱序返回时，结果仍应交给正确的调用方。
- N5: 远端工具注册和调用应与 Provider 无关。Anthropic 和 OpenAI 工具声明适配仍只依赖工具中心输出，不感知 MCP 传输细节。
- N6: MCP 配置解析应保持保守失败策略。单个 Server 配置失败时跳过该 Server；主配置文件整体不可解析时仍按现有配置错误处理。
- N7: Server 名、工具名和公开工具名应做格式校验，避免生成模型供应商不接受的工具名，避免路径分隔符、空白字符或控制字符进入公开工具名。
- N8: 远端工具描述和错误内容可能不可信。LunaCode 展示或拼接这些内容时应限制长度，并避免让远端内容破坏工具声明、日志结构或普通回复格式。
- N9: MCP 能力应有自动化测试覆盖，包括配置合并、嵌入式环境变量展开、公开工具命名、stdio 通信、HTTP 通信、初始化、列工具、调用工具、异步 id 配对、失败隔离和敏感值遮蔽。
- N10: 本阶段不改变 LunaCode 的中文协作风格。用户可见的配置错误、连接错误、工具调用错误和端到端验收说明应使用中文。
- N11: 延迟加载状态必须与会话内 Agent Loop 轮次一致。ToolSearch 标记已发现后，下一轮工具列表必须稳定包含该工具；未被 ToolSearch 发现的 MCP 工具不能因为存在于 Registry 中就泄露完整 schema。
- N12: ToolSearch 是本地元数据检索能力，不跨越 MCP 信任边界。它不得启动新 Server、不得重连 Server、不得调用远端工具，也不得把敏感 headers、env 或连接参数返回给模型。
- N13: 传输抽象必须可测试且行为一致。stdio 和 Streamable HTTP 的差异应被限制在传输边界内，上层 MCP 会话、JSON-RPC 请求配对、工具包装和权限路径不应出现传输类型分支散落。

## 不做的事情

- 本阶段不实现 MCP 资源能力。
- 本阶段不实现 MCP 提示词能力。
- 本阶段不实现 MCP 采样能力，也不允许 MCP Server 反向驱动 LunaCode 调用模型。
- 本阶段不实现 Server 健康检查、后台心跳、自动重连或连接池热替换。
- 本阶段不实现动态工具变更订阅。启动后 Server 工具列表变化，需要重启 LunaCode 才能重新发现。
- 本阶段不实现 OAuth、浏览器授权流程、密钥管理器或交互式认证；远程 Server 认证仅通过配置中的 headers 表达。
- 本阶段不为 stdio Server 增加 `cwd` 配置；本地子进程统一在 LunaCode 当前工作区根目录启动。
- 本阶段不把图片、二进制资源或未知非文本内容保存为本地文件，也不在 TUI 中做富媒体展示。
- 本阶段不实现 MCP Server 市场、插件安装器或图形化配置界面。
- 本阶段不支持未在配置文件中声明的自动 Server 发现。
- 本阶段不把 MCP Server 的文件系统权限、网络权限或进程隔离当作 LunaCode 的内部沙箱替代品。
- 本阶段不把所有 MCP 工具完整 schema 默认塞进模型工具列表；未被 ToolSearch 发现的 MCP 工具只以名称摘要形式出现在 system-reminder 中。
- 本阶段不允许 MCP Server 通过工具描述、schema 或服务端请求绕过 LunaCode 权限系统，或反向要求 LunaCode 调用其他本地工具。

## 验收标准

- AC1: 同时存在用户级 `~/.lunacode/config.yaml` 和项目级当前启动配置时，LunaCode 启动后能观察到 `mcp.servers` 已合并；同名 Server 以项目级声明为准，不同名 Server 同时存在。
- AC2: stdio Server 配置包含 `command`、`args` 和 `env` 时，LunaCode 能在字符串中嵌入展开 `${VAR}`，在当前工作区根目录启动子进程，完成初始化和列工具，并在工具中心看到对应远端工具。
- AC3: HTTP Server 配置包含 `url` 和 `headers` 时，LunaCode 能在 `Bearer ${TOKEN}` 这类字符串中嵌入展开环境变量，通过 Streamable HTTP 完成初始化和列工具，并在工具中心看到对应远端工具。
- AC4: 某个 Server 引用不存在的环境变量时，启动日志或事件中能看到该 Server 被跳过的中文原因；其他 Server 和内置工具仍可用。
- AC5: 两个 Server 暴露同名工具，或 MCP 工具与内置工具同名时，LunaCode 暴露给模型的工具名采用 `mcp_{serverName}_{toolName}` 形式并保持唯一稳定；合法化后冲突时追加短哈希后缀，且不会覆盖内置工具。
- AC6: Agent 调用一个 MCP 远端工具时，LunaCode 向正确 Server 发送 `tools/call` 请求，使用远端原始工具名和 Agent 提供的参数，并把成功结果作为普通工具结果回灌给 Agent Loop。
- AC7: MCP 工具返回结构化内容或非文本内容时，LunaCode 不崩溃、不落盘；Agent 能收到可读摘要，事件或元数据中能看到内容类型、数量或名称摘要。
- AC8: MCP 工具返回远端错误、协议错误或超时时，Agent 收到普通工具错误结果，错误中包含 Server 名、工具名和失败原因摘要，Agent Loop 不直接崩溃。
- AC9: 同一 Server 上两个工具调用并发发出且响应乱序返回时，LunaCode 能按 JSON-RPC id 将结果分别交给正确的工具调用。
- AC10: 一个 MCP Server 在运行中断开后，调用该 Server 的工具会返回连接不可用错误；其他 MCP Server 的工具和内置工具仍可继续调用。
- AC11: 重复调用同一 MCP 工具时，LunaCode 复用启动时缓存的连接，不重复执行初始化握手和列工具流程。
- AC12: 启动时配置多个 MCP Server 且其中一个响应很慢时，LunaCode 并发发现各 Server；慢 Server 在 30 秒超时后被标记不可用，其他已成功 Server 的工具仍注册成功。
- AC13: 退出 LunaCode 后，stdio MCP Server 子进程不应残留为孤儿进程；未完成请求会被清理为失败结果或随应用退出释放。
- AC14: MCP 远端工具调用经过现有权限路径。需要确认的远端工具会触发权限确认；用户拒绝后，远端工具不会执行，Agent Loop 收到结构化拒绝结果。
- AC15: 错误摘要、事件流、工具结果和普通回复中不出现展开后的 API Key、Token、Authorization 头或配置中的敏感环境变量值。
- AC16: 配置示例中包含 `mcp.servers` 下的一个 stdio Server 示例和一个 HTTP Server 示例，示例能说明嵌入式环境变量展开和用户级/项目级覆盖规则。
- AC17: 自动化测试覆盖配置合并、嵌入式环境变量展开、公开工具命名与冲突后缀、初始化握手、协议版本不兼容、列工具分页、坏工具 schema 跳过、工具调用、JSON-RPC id 配对、单 Server 失败隔离和敏感值遮蔽。
- AC18: 使用 tmux 启动 LunaCode，输入真实请求“调用配置中的 MCP 工具获取一段测试数据，然后总结结果”，可以观察到 LunaCode 自动使用远端工具、返回结果并继续中文回复。
- AC19: 启动后 MCP 工具已注册在客户端 Registry 中，但第一轮模型普通工具列表不包含未发现 MCP 工具的完整 schema；system-reminder 中能看到延迟 MCP 工具公开名字列表。
- AC20: 模型调用 ToolSearch 查询某个延迟 MCP 工具公开名后，ToolSearch 返回该工具完整描述和输入 schema，并将该工具标记为已发现；下一轮 Agent Loop 的普通工具列表包含该 MCP 工具完整 schema。
- AC21: ToolSearch 查询 MCP 工具时不会向 MCP Server 发送 `tools/call`，不会启动新 Server，也不会泄露 headers、env 或展开后的密钥值。
- AC22: 针对公开工具名配置权限规则后，MCP 工具调用按该规则得到 allow、ask 或 deny；命中 deny 或用户拒绝时，LunaCode 不向 MCP Server 发送 `tools/call`，Agent Loop 收到结构化拒绝结果。
- AC23: 恶意或异常 MCP Server 返回包含提示注入文本的工具描述、schema、错误或工具结果时，LunaCode 仍按不可信外部输入处理，限制展示长度，不因此绕过权限检查或改变工具注册边界。
- AC24: 使用同一套 MCP 会话和 JSON-RPC 逻辑分别接入一个 stdio Server 和一个 Streamable HTTP Server 时，两者都能完成初始化、列工具、延迟 ToolSearch、权限审批和工具调用；除连接方式不同外，Agent 可观察行为一致。


