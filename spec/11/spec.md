# Agent Hook 自动化 Spec

## 背景

LunaCode 已经具备 Agent 循环、工具调用、权限审批、上下文压缩、Slash Command 和会话管理等能力，但生命周期关键节点上的重复动作仍然需要人工盯着做。格式化、工具调用拦截、上下文提醒、HTTP 通知、文件变更后的检查等行为如果都交给模型临场判断，会增加不确定性，也会浪费 Token。

本阶段要提供一套声明式 Hook 机制，让用户用 YAML 规则描述“在什么事件上、满足什么条件时、执行什么固定动作”。Hook 应覆盖 Agent 生命周期中的会话级、轮次级、消息级、工具级和系统级事件，并且在工具执行前支持同步拦截，让明确的安全策略能稳定地把工具调用挡下来，并把拒绝原因反馈给 Agent。

## 目标

- 提供声明式 Hook 规则，让固定自动化动作能在生命周期事件上自动触发。
- 覆盖会话级、轮次级、消息级、工具级和系统级事件，第一版事件都要能真实触发。
- 支持工具执行前拦截，拦截后取消工具调用，并把 Hook 输出作为错误信息反馈给 Agent。
- 支持简单、可校验的条件表达式，不引入完整表达式引擎。
- 支持命令、提示词、HTTP、子 Agent 四类动作，其中子 Agent 第一版只占位。
- 保证 Hook 自身失败只记录日志，不中断 Agent 主流程；安全拦截类 Hook 除外，配置了拒绝时必须拒绝。

## 功能需求

- F1: Hook 规则必须用 `event`、可选 `if`、`action` 三要素描述；`event` 和 `action` 必填，`if` 省略时表示无条件触发。
- F2: Hook 配置从三个来源追加合并：项目级 `.lunacode/config.yaml`、用户级 `~/.lunacode/config.yaml`、本地级 `.lunacode/config.local.yaml`；三个来源声明的 Hook 同时生效。
- F3: 多个 Hook 命中同一事件时，按项目级、用户级、本地级的顺序执行；同一文件内按声明顺序执行；第一版不提供显式优先级字段。
- F4: Hook 配置必须集中校验；任意 Hook 配置非法时，LunaCode 启动失败，并一次性报告所有校验错误。
- F5: 会话级事件必须支持 `session_start` 和 `session_end`。
- F6: 轮次级事件必须支持 `turn_start` 和 `turn_end`。
- F7: 消息级事件必须支持 `pre_send` 和 `post_receive`；`pre_send` 在消息发送给 LLM 前触发，`post_receive` 在收到 LLM 响应后触发。
- F8: 工具级事件必须支持 `pre_tool_use` 和 `post_tool_use`；`pre_tool_use` 在工具真正执行前触发，`post_tool_use` 在工具执行完成后触发。
- F9: 系统级事件必须支持 `startup`、`shutdown`、`error`、`compact`、`permission_request`、`file_change`、`command_execute`。
- F10: `file_change` 第一版只表示 LunaCode 自己的文件工具成功修改文件后触发，不监听外部编辑器或系统文件变化。
- F11: Hook 条件上下文必须暴露稳定字段：`eventName`、`toolName`、`toolArgs`、`filePath`、`message`、`error`。
- F12: 条件表达式必须支持四种比较操作符：`==` 精确匹配、`!=` 反向匹配、`=~` 正则匹配、`~=` glob 匹配。
- F13: 条件表达式必须支持 `&&` 表示全部满足、`||` 表示任一满足；同一条表达式禁止混用 `&&` 和 `||`，需要复杂逻辑时用户应拆成多条 Hook。
- F14: 条件解析只需要支持简单格式：按 `&&` 或 `||` 拆分子条件，每个子条件按空格解析为 `field operator value` 三部分。
- F15: Hook 规则必须支持命令动作，命令动作复用现有 Bash 工具执行环境的工作目录、超时、输出截断、敏感信息脱敏和沙箱策略，但不再弹出权限确认。
- F16: 命令动作执行时必须把 Hook 上下文注入为环境变量，至少包括 `EVENT_NAME`、`TOOL_NAME`、`FILE_PATH`、`MESSAGE`、`ERROR`，以及由工具参数生成的 `ARGS_<KEY>`。
- F17: Hook 规则必须支持提示词动作；提示词动作作为 system reminder 注入模型上下文，`pre_send` 触发时影响当前请求，其它事件触发时影响下一次请求。
- F18: 消息级 Hook 第一版不得改写用户原文或模型回复原文，只能通过提示词动作补充 system reminder。
- F19: Hook 规则必须支持 HTTP 动作，字段包括 `url`、`method`、`headers`、`body`、`timeout_ms`，并允许这些字段引用 Hook 上下文变量。
- F20: Hook 规则必须支持子 Agent 动作的配置加载和校验；第一版命中后只记录日志并返回未实现状态，不真实启动子 Agent。
- F21: `command` 和 `http` 动作默认只记日志，不进入对话；配置 `inject_result: true` 时，动作输出可作为 system reminder 注入后续模型上下文。
- F22: `prompt` 动作总是注入模型上下文。
- F23: `pre_tool_use` 可配置 `reject: true`；命中后必须同步执行动作、等待动作结果、取消原工具调用，并把动作输出作为工具错误信息反馈给 Agent。
- F24: `reject: true` 只能用于 `pre_tool_use`；其它事件上配置 `reject` 必须校验失败。
- F25: `reject: true` 的 Hook 如果动作失败或没有输出，仍然必须拒绝工具调用，并使用兜底拒绝原因反馈给 Agent。
- F26: 拦截类 Hook 不允许异步执行；其它 Hook 可配置后台异步执行。
- F27: Hook 必须支持动作超时；命令动作和 HTTP 动作都能配置超时时间。
- F28: Hook 必须支持 `once`，第一版范围为本进程内同一会话只执行一次；不做持久化，重启或恢复会话后可重新执行。
- F29: 后台异步动作失败只写 Hook 日志，不影响 Agent、不进入对话、不更新状态栏。
- F30: Hook 日志按会话写入 `.lunacode/tmp/hooks/<sessionId>.log`，用于追踪命中、执行、失败和拒绝原因。

## 非功能需求

- N1: Hook 失败不得中断 Agent 主流程；配置了 `reject: true` 的 `pre_tool_use` 命中后必须按安全拦截语义处理。
- N2: Hook 条件和配置错误必须可诊断，启动失败时要报告来源、规则位置和原因。
- N3: Hook 执行顺序必须稳定、可预测，不能依赖并发调度或配置读取的偶然顺序。
- N4: Hook 日志不得泄漏已被现有敏感信息脱敏机制覆盖的敏感值。
- N5: 默认行为必须安静，非提示词动作结果不应自动污染对话上下文。
- N6: Hook 机制应复用 LunaCode 现有安全边界，不能绕过工作区沙箱和命令执行限制。

## 不做的事

- 不做子 Agent 动作的真实运行，等待 SubAgent 章节对接。
- 不做 `once` 标记的持久化。
- 不做 Hook 执行顺序的显式优先级字段。
- 不做完整表达式引擎，不支持 `&&` 和 `||` 混用、括号优先级或任意代码执行。
- 不做外部文件 watcher；`file_change` 只覆盖 LunaCode 文件工具造成的修改。
- 不做消息原文改写；Hook 只能通过 system reminder 注入上下文。
- 不做 HTTP 动作的重试、状态码白名单、复杂 Webhook 策略和响应截断策略。
- 不做 Hook 命令的交互式权限确认。

## 验收标准

- AC1: 在项目级、用户级、本地级三个配置来源分别声明 Hook 后，LunaCode 启动时能追加合并，并按项目级、用户级、本地级顺序触发。
- AC2: 配置缺少 `event` 或 `action`、使用未知事件、非法动作字段、非法条件表达式、非 `pre_tool_use` 使用 `reject` 时，LunaCode 启动失败并报告所有错误。
- AC3: 会话级、轮次级、消息级、工具级和系统级事件在对应生命周期节点都能触发匹配的 Hook。
- AC4: `if` 省略时 Hook 无条件触发；配置 `==`、`!=`、`=~`、`~=` 的条件时，命中结果符合精确、反向、正则、glob 语义。
- AC5: 使用 `&&` 的条件必须所有子条件通过才触发；使用 `||` 的条件任一子条件通过即可触发；混用 `&&` 和 `||` 时配置校验失败。
- AC6: `pre_tool_use` 上配置 `reject: true` 后，命中 Hook 会取消原工具调用，并把 Hook 动作输出作为工具错误信息反馈给 Agent。
- AC7: `reject: true` 的 Hook 动作失败或无输出时，原工具调用仍被取消，Agent 收到兜底拒绝原因。
- AC8: `post_tool_use` 中配置命令动作后，工具执行完成会触发命令，并通过环境变量读取 `FILE_PATH`、`TOOL_NAME` 或 `ARGS_<KEY>` 等上下文。
- AC9: 命令动作复用现有 Bash 工具执行环境，能遵守工作目录、超时、输出截断、敏感信息脱敏和沙箱限制，且不会弹出权限确认。
- AC10: 提示词动作触发后，模型在后续请求中能看到对应 system reminder；`pre_send` 触发的提示词能影响当前请求。
- AC11: `command` 和 `http` 动作默认只写日志；配置 `inject_result: true` 后，其输出能作为 system reminder 注入模型上下文。
- AC12: HTTP 动作能按配置发送请求，并能在 `url`、`headers`、`body` 中使用 Hook 上下文变量。
- AC13: `once` Hook 在本进程内同一会话重复遇到匹配事件时只执行一次；重启或恢复会话后可重新执行。
- AC14: 异步 Hook 不阻塞 Agent 主流程；异步动作失败只写会话 Hook 日志，不影响状态栏和对话。
- AC15: `file_change` 在 LunaCode 文件工具成功修改文件后触发，外部编辑器修改文件不会触发。
- AC16: `sub_agent` 动作能通过配置校验并在触发时写入未实现日志，不启动真实子 Agent。
- AC17: Hook 执行、失败、跳过、拦截原因会写入 `.lunacode/tmp/hooks/<sessionId>.log`。
