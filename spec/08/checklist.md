# LunaCode 项目记忆与会话恢复 Checklist

> 每一项通过运行代码或观察行为来验证，聚焦系统行为。实现过程中可以重构文件或类名，只要外部行为满足这些检查即可。

## 项目指令

- [ ] 三层 `LUNACODE.md` 能按项目根、项目 `.lunacode`、用户级的优先级注入上下文（验证：运行指令加载或 Prompt 注入测试，观察输出顺序）。
- [ ] `@include` 行会被目标文件内容替换，目标文件中的 include 会继续递归展开（验证：运行 include 测试，观察展开后的文本包含嵌套文件内容）。
- [ ] include 最大深度为 5，超过后不会继续展开且有可观察 warning（验证：构造 6 层 include，运行测试观察第 6 层被跳过）。
- [ ] 两个文件互相 include 时不会死循环，重复绝对路径会被跳过（验证：构造 A include B、B include A，运行测试在限定时间内通过）。
- [ ] 项目级 include 指向项目根外路径会被跳过并记录中文原因（验证：构造越界路径，运行测试观察 warning）。
- [ ] 用户级 include 指向 `~/.lunacode/` 外路径会被跳过并记录中文原因（验证：构造越界路径，运行测试观察 warning）。
- [ ] include 不支持 glob 且不会误展开通配路径（验证：构造 `@include *.md`，运行测试观察被拒绝或 warning）。
- [ ] include 失败不会导致 LunaCode 启动失败或普通请求不可用（验证：构造不存在的 include 文件，运行启动或 Prompt 构建测试通过）。

## 会话存档

- [ ] 新会话会在 `.lunacode/sessions/` 下生成 `YYYYMMDD-HHMMSS-xxxx.jsonl` 文件（验证：运行会话存储测试或启动 LunaCode 后检查文件名）。
- [ ] 同秒创建多个会话时不会发生文件名冲突（验证：运行会话 ID 测试，观察生成 ID 唯一）。
- [ ] 每条 JSONL 记录只包含 `role`、`content`、`ts` 三个字段（验证：运行 JSONL 追加测试，读取落盘 JSON 行）。
- [ ] user 文本消息会追加写入当前会话 JSONL（验证：发送用户请求后检查 JSONL 新增 user 行）。
- [ ] assistant 最终文本消息会追加写入当前会话 JSONL（验证：等待最终回复后检查 JSONL 新增 assistant 行）。
- [ ] assistant 混合文本和 `tool_use` 能以 content block 数组保存（验证：运行含工具调用的会话测试，检查 content 数组）。
- [ ] 工具结果能以 `tool_result` content block 保存并关联对应工具调用 ID（验证：运行工具结果会话测试，检查 tool_use_id）。
- [ ] 会话 ID、标题、消息数和最后活跃时间来自扫描 JSONL，而不是独立 meta 文件（验证：删除或不存在 meta 文件时运行 `/session`，仍能显示信息）。
- [ ] 会话标题来自第一条用户消息截断结果（验证：构造第一条用户消息，运行会话列表测试观察标题）。
- [ ] JSONL 中存在坏行时，恢复过程跳过坏行并保留其他合法消息（验证：手动插入坏行，运行恢复测试观察合法消息仍恢复）。
- [ ] 末尾存在未配对 `tool_use` 时，恢复历史从包含该 `tool_use` 的 assistant 消息开始截断（验证：运行恢复策略测试，确认模型请求不包含未配对工具调用）。
- [ ] 恢复历史超过 token 预算时会先尝试压缩一次（验证：运行超限恢复测试，观察压缩分支被调用）。
- [ ] 压缩失败时不会改写原 JSONL，也不会阻塞启动，只保留会话摘要或 warning 提示（验证：模拟压缩失败，比较 JSONL 内容并观察恢复结果）。
- [ ] 启动时会清理超过 30 天的 `.lunacode/sessions/` 文件并显示可见状态（验证：构造过期文件后启动或运行服务测试，观察文件被删除和状态提示）。
- [ ] 启动时存在最近未过期会话会自动恢复，不存在时会自动创建新会话（验证：分别运行有会话和空目录启动测试）。
- [ ] 恢复会话距离上次活跃超过 24 小时时，上下文中出现时间跨度提醒（验证：构造旧会话恢复，检查 Prompt 或恢复结果包含提醒）。

## 会话命令

- [ ] `/session` 或 `/session current` 能显示当前会话 ID、标题、消息数和最后活跃时间（验证：运行命令处理测试或 TUI 中输入命令观察输出）。
- [ ] `/session list` 能按最后活跃时间列出历史会话（验证：构造多个会话后运行命令，观察排序）。
- [ ] `/session resume <id>` 能恢复指定会话，并让后续消息写入该会话文件（验证：恢复后发送消息，检查目标 JSONL 追加）。
- [ ] `/session new` 能创建新会话并清空当前对话历史（验证：运行命令后观察当前 session ID 改变且历史为空）。
- [ ] Agent 正在运行、等待权限确认或等待提问输入时，`/session resume` 和 `/session new` 会被拒绝并给出中文提示（验证：运行 orchestrator 命令测试）。

## 记忆存储

- [ ] 用户偏好记忆写入 `~/.lunacode/memory/`（验证：执行新增用户偏好动作后检查文件目录）。
- [ ] 纠正反馈记忆写入 `~/.lunacode/memory/`（验证：执行新增纠正反馈动作后检查文件目录）。
- [ ] 项目知识记忆写入 `<项目根>/.lunacode/memory/`（验证：执行新增项目知识动作后检查文件目录）。
- [ ] 参考信息记忆写入 `<项目根>/.lunacode/memory/`（验证：执行新增参考信息动作后检查文件目录）。
- [ ] 新增记忆 Markdown 包含 `id`、`type`、`title`、`created_at`、`updated_at`、`source_session` frontmatter 字段（验证：读取落盘 Markdown）。
- [ ] `type` 与实际目录落点一致，不会把用户级记忆写入项目目录或反向写入（验证：运行记忆落点测试）。
- [ ] 坏 frontmatter 记忆文件会被跳过，不会导致主对话不可用（验证：构造坏记忆文件后运行加载测试）。
- [ ] 用户级和项目级各自生成 `MemoryIndex.md`（验证：执行索引重建后检查两个目录）。
- [ ] 合并注入的记忆索引不超过 200 行或 25KB（验证：构造大量记忆后运行索引测试，观察裁剪结果）。
- [ ] 记忆写入和索引内容不会完整暴露 API Key、Token、Authorization 值或其他已知敏感值（验证：输入含敏感值的记忆动作，检查落盘内容被过滤或拒绝）。

## 记忆命令与自动更新

- [ ] `/memory` 能显示索引摘要和自动记忆状态（验证：运行命令处理测试或 TUI 中输入命令观察输出）。
- [ ] `/memory list` 能列出当前可见记忆（验证：预置记忆后运行命令观察列表）。
- [ ] `/memory delete <id>` 能删除指定记忆并重建索引（验证：运行命令后检查记忆文件消失且 `MemoryIndex.md` 更新）。
- [ ] `/memory off` 能关闭当前运行时自动记忆（验证：关闭后触发 LoopComplete，观察 updater 未运行）。
- [ ] `/memory on` 能重新开启当前运行时自动记忆（验证：开启后触发 LoopComplete，观察 updater 被调用）。
- [ ] 配置 `memory.auto_update: false` 时，启动后默认不触发自动记忆（验证：加载配置后运行 LoopComplete 测试）。
- [ ] 模型最终回复且没有继续工具调用时，会在后台异步触发记忆更新（验证：运行 orchestrator 测试，观察 LoopComplete 后提交异步任务）。
- [ ] 自动记忆只使用本轮对话增量和现有 MemoryIndex，不把完整历史会话交给记忆模型（验证：fake memory model 捕获请求，检查输入范围）。
- [ ] 自动记忆可执行新增、更新、删除和 no-op，并在完成后更新对应 `MemoryIndex.md`（验证：运行自动记忆测试覆盖四类 action）。
- [ ] 自动记忆失败不会向聊天回复追加失败文本，只通过状态事件或状态栏提示摘要（验证：模拟 memory model 失败，检查最终回复和状态）。
- [ ] 自动记忆异步执行不阻塞用户下一轮输入（验证：使用延迟 fake updater，观察 orchestrator 仍可接受下一轮输入）。

## Prompt 与上下文

- [ ] 请求前会加载项目指令和记忆索引，使 Agent 相当于已经读过这些上下文（验证：运行 Prompt 构建测试，检查 channel 包含二者）。
- [ ] Prompt 注入顺序为项目指令、记忆索引、恢复提醒或时间跨度提醒、会话历史（验证：运行 provider adapter 测试，观察消息顺序）。
- [ ] OpenAI 适配器渲染项目指令和记忆时不破坏普通历史消息结构（验证：运行 OpenAI Prompt 适配测试）。
- [ ] Anthropic 适配器渲染项目指令和记忆时不破坏普通历史消息结构（验证：运行 Anthropic Prompt 适配测试）。
- [ ] 恢复失败、include 失败、坏记忆或坏索引不会阻止 Prompt 构建（验证：构造异常输入，运行 Prompt 构建测试通过）。

## TUI 与状态

- [ ] TUI 状态栏能显示当前 session 短标识（验证：启动或运行 TUI 状态测试，观察状态文本）。
- [ ] TUI 状态栏能显示 `memory:on` 或 `memory:off`（验证：切换 `/memory on/off` 后观察状态文本）。
- [ ] TUI 状态栏能显示最近一次记忆更新状态，如 updated、noop 或 failed（验证：模拟记忆更新结果后观察状态文本）。
- [ ] 会话恢复失败、记忆失败和过期会话清理有可观察中文状态或事件（验证：运行对应测试或启动场景观察输出）。

## 编译与测试

- [ ] 项目编译无错误（验证：运行 `mvn -DskipTests compile`）。
- [ ] 会话模块单元测试全部通过（验证：运行 `mvn -Dtest='com.lunacode.session.*Test' test`）。
- [ ] 指令和 Prompt 测试全部通过（验证：运行 `mvn -Dtest='com.lunacode.instructions.*Test,PromptContextBuilderMemoryInstructionTest' test`）。
- [ ] 记忆模块测试全部通过（验证：运行 `mvn -Dtest='com.lunacode.memory.*Test' test`）。
- [ ] Orchestrator 和 TUI 集成测试全部通过（验证：运行 `mvn -Dtest='com.lunacode.orchestrator.*Test,com.lunacode.tui.*Test' test`）。
- [ ] 全量测试通过（验证：运行 `mvn test`）。
- [ ] 如项目配置了 lint 或格式检查，检查通过（验证：运行项目已有 lint 或格式命令）。

## 端到端场景

- [ ] 首次启动 LunaCode，输入一段真实请求并等待最终回复后，`.lunacode/sessions/` 中出现当前会话 JSONL 且内容被追加（验证：tmux 中操作后检查文件）。
- [ ] 重启 LunaCode 后，最近未过期会话被自动恢复（验证：tmux 重启后输入 `/session`，观察当前会话为上次会话）。
- [ ] 构造超过 24 小时未活跃的会话后重启，Agent 上下文中出现时间跨度提醒（验证：tmux 中恢复旧会话后观察系统提示或模型请求测试输出）。
- [ ] 输入一条可记忆的用户偏好，Agent 最终回复后自动记忆异步更新并生成或更新 `MemoryIndex.md`（验证：tmux 中等待状态更新后检查记忆索引）。
- [ ] 使用 `/session list` 和 `/session resume <id>` 切换历史会话后，后续请求写入被恢复会话（验证：tmux 中操作后检查对应 JSONL）。
- [ ] 使用 `/memory off` 后继续对话，自动记忆不再更新；使用 `/memory on` 后再次恢复更新（验证：tmux 中观察状态栏和 `MemoryIndex.md` 更新时间）。
- [ ] 自动记忆模型失败时，用户最终回复保持干净，TUI 显示失败摘要（验证：使用 fake 或故障 Provider 运行端到端场景）。
