# Worktree 隔离 Spec

## 背景

LunaCode 已经具备定义式子 Agent、Fork 式子 Agent、后台任务和工具执行能力。上一阶段明确不做 Worktree 文件隔离，因此当前子 Agent 即使消息、权限和上下文相互隔离，文件操作仍可能落在主 Agent 的同一个工作目录里。当主 Agent 与多个子 Agent 并行工作时，这会带来文件覆盖、依赖环境不一致、缓存混用和误删成果等风险。

本阶段要为 LunaCode 提供 Git Worktree 隔离能力。系统在同一个 Git 仓库内为需要隔离的子 Agent 或用户手动创建独立工作目录，每个 worktree 使用独立分支，目录放在仓库内不被 Git 追踪的位置。工具调用不通过切换进程当前目录来改变工作区，而是根据当前 worktree 会话显式使用对应目录作为文件和命令的执行根目录。

## 目标

- 为子 Agent 并行修改文件提供稳定的 Git Worktree 隔离。
- 提供用户可见的 `/worktree` 命令族，支持创建、列表、进入、退出和删除。
- 通过严格名称校验、固定管理目录和删除保护，防止路径遍历和误删成果。
- 在创建 worktree 后补齐运行所需的本地配置、Git hooks、大型依赖软链和被忽略但需要的文件。
- 支持 worktree 会话持久化和启动恢复，使工具调用始终能显式落到当前 worktree。
- 为声明 `isolation: worktree` 的定义式子 Agent 自动创建隔离目录，并在任务结束后按变更情况自动清理或保留。
- 提供后台过期清理能力，只清理 LunaCode 可证明安全管理的临时 worktree。

## 功能需求

- F1: 系统必须把所有 LunaCode 管理的 worktree 放在仓库内固定的不追踪目录下，第一版不提供目录配置项。
- F2: 系统必须提供 `/worktree create`、`/worktree list`、`/worktree enter`、`/worktree exit` 和 `/worktree remove` 命令。
- F3: 用户手动创建的 worktree 名称必须由用户提供，并经过严格安全校验。
- F4: worktree 名称允许字母、数字、`-`、`_`、`.` 和 `/`；`/` 只表示嵌套段；名称总长度不得超过 120。
- F5: worktree 名称必须拒绝空段、`.` 段、`..` 段、反斜杠、Windows 保留名和任何超出允许字符集的字符。
- F6: 分支名必须使用 `worktree-` 前缀；名称中的 `/` 在分支名中必须替换为 `+`，例如 `team-refactor/alice` 对应 `worktree-team-refactor+alice`。
- F7: 子 Agent 自动创建的 worktree 名称必须使用 `agent-a` 加 7 位随机十六进制字符，例如 `agent-a3f2b1c`。
- F8: 工作流创建的 worktree 名称必须使用 `wf_` 前缀加固定格式十六进制标识。
- F9: 用户通过 `/worktree create my-feature` 手动创建的 worktree 不得匹配自动清理命名模式，并且永远不被后台自动清理。
- F10: 创建 worktree 时，默认 base ref 必须使用主仓库当前 `HEAD` 的提交；如果当前在分支上，系统只记录原分支名用于诊断。
- F11: `/worktree create` 第一版不得允许用户指定 base ref；所有手动和自动创建都从当前 `HEAD` 提交创建。
- F12: 创建 worktree 时不得携带主仓库未提交修改；如果主仓库存在未提交修改，系统必须给出警告，说明这些修改不会进入 worktree。
- F13: 创建 worktree 时，如果目标目录已经存在，系统必须优先执行快速恢复：只读取 worktree 目录下的 `.git` 指针文件、Git 目录中的 `HEAD` 和必要的 ref 文件来还原当前 commit，不调用 Git 子进程。
- F14: 快速恢复成功时，系统必须复用已有 worktree，记录路径、分支和 HEAD commit，并跳过创建后环境初始化。
- F15: 快速恢复失败或目标目录不存在时，系统必须使用 Git Worktree 创建新目录，并允许覆盖同名残留分支。
- F16: 所有 Git 子进程都必须禁用交互式输入，确保需要凭证或外部输入时不会挂起。
- F17: 新建 worktree 后，系统必须复制本地运行配置文件，例如 `settings.local.json`；缺失时记录警告，不中断创建。
- F18: 新建 worktree 后，系统必须配置该 worktree 的 Git hooks；优先识别主仓库 `.husky/`，没有时回退到主仓库 Git hooks 目录。
- F19: 新建 worktree 后，系统必须根据 `settings.worktree.symlinkDirectories` 声明，为大型依赖目录创建软链；软链失败时记录警告，不中断创建。
- F20: 系统必须在文档和提示中明确依赖目录软链是 best-effort 策略，并说明 Node.js 工具链可能因解析真实路径而需要独立安装依赖或启用保留软链行为。
- F21: 新建 worktree 后，系统必须支持仓库根目录 `.worktreeinclude`；该文件使用 gitignore 语法声明哪些被忽略但运行需要的文件应复制到 worktree。
- F22: 被忽略文件补齐必须只复制 `.worktreeinclude` 匹配到的文件；模式匹配失败、文件不存在或复制失败时记录警告，不中断创建。
- F23: `/worktree enter` 必须进入一个 worktree 会话，只记录原始 cwd、worktree 路径、worktree 分支、原分支、原 HEAD 和 session id，并持久化到本地 session 文件。
- F24: `/worktree enter` 不得切换进程 cwd，不得清空文件内容、系统提示词、项目指令或记忆缓存。
- F25: 当前存在 worktree 会话时，后续文件工具和命令工具必须显式使用 worktree 路径作为执行根目录或 cwd；进程 cwd 理论上保持在原始 cwd。
- F26: 所有路径相关缓存必须使用绝对路径作为 key，使主仓库与 worktree 天然隔离，不依赖进入或退出时清缓存。
- F27: 系统提示词或运行上下文必须向 Agent 注入当前 worktree 路径，使模型能知道文件操作发生在隔离目录中。
- F28: `/worktree exit` 必须清除当前 worktree 会话并持久化清除；退出时必须兜底恢复原始 cwd。
- F29: `/worktree remove` 默认必须检查未提交修改和基线之后的新 commit；存在任意一种成果时拒绝删除。
- F30: 删除保护必须以创建或恢复时记录的 `headCommit` 为基线；只要 worktree 分支包含基线之后的新 commit，就视为有成果，不区分是否已 push。
- F31: 调用方必须显式设置丢弃确认参数后，系统才允许强制删除有未提交修改或新增 commit 的 worktree。
- F32: 删除 worktree 时，系统必须先删除 worktree 目录，再等待 Git lock 释放，然后删除对应 `worktree-` 分支。
- F33: 系统必须记录 active worktree 状态，并在创建、进入、退出、删除时持久化当前 session 或管理状态。
- F34: LunaCode 启动时必须读取本地 worktree session；如果 worktree 目录、`.git` 指针和 HEAD 都有效，则自动恢复当前 worktree 会话。
- F35: LunaCode 启动时如果发现 worktree session 无效，必须清除该 session 并提示可观察警告。
- F36: 定义式子 Agent 的角色 frontmatter 必须支持 `isolation: worktree` 字段。
- F37: 只有定义式子 Agent 显式声明 `isolation: worktree` 时，系统才自动创建并进入独立 worktree；Fork 式子 Agent 和 Hook 子 Agent 默认不自动创建 worktree。
- F38: 子 Agent 自动 worktree 创建后，系统必须向子 Agent 注入当前 worktree 路径说明，使其知道所有文件操作都发生在隔离目录中。
- F39: 子 Agent 结束后，如果 worktree 没有未提交修改且没有基线之后的新 commit，系统必须自动清理该 worktree。
- F40: 子 Agent 结束后，如果 worktree 存在未提交修改或基线之后的新 commit，系统必须保留该 worktree，并把路径和分支通知给主 Agent 供 review。
- F41: 后台过期清理只能处理 LunaCode 管理目录下、session 或管理记录存在、名称匹配自动模式、分支名前缀为 `worktree-` 的 worktree。
- F42: 后台过期清理即使发现 worktree 已过期，只要存在未提交修改或基线之后的新 commit，也必须保留。
- F43: 后台过期清理不得清理用户手动创建的 worktree。
- F44: 本阶段必须保证主 Agent、手动 worktree 会话和子 Agent worktree 会话的工具执行目录互不覆盖。
- F45: 快速恢复、环境初始化、自动清理和删除保护的结果必须可诊断；失败原因或警告应能被用户或主 Agent 观察到。

## 非功能需求

- N1: Worktree 路径和分支名处理必须防路径遍历、防 Windows 保留名、防反斜杠逃逸，并始终限制在 LunaCode 管理目录内。
- N2: 创建、删除和清理操作必须优先保护工作成果；宁可保留临时目录，也不得默认丢弃未提交修改或新增 commit。
- N3: Git 子进程不得等待用户交互输入；需要凭证或确认时必须快速失败并返回可诊断错误。
- N4: 快速恢复路径必须只做文件系统读取，不启动 Git 子进程，以适配 Agent 反复进入同一 worktree 的高频场景。
- N5: 环境初始化是 best-effort，单项失败不得中断 worktree 创建，但必须留下可观察警告。
- N6: Worktree 会话不得依赖全局进程 cwd 作为状态来源；工具执行必须使用显式 cwd 或显式工作根。
- N7: Worktree 会话持久化不得泄露密钥内容；本地配置文件复制只发生在本机仓库管理目录内。
- N8: 本阶段必须保持现有不使用 worktree 的主 Agent、Fork 子 Agent、Hook 子 Agent 和普通定义式子 Agent 行为兼容。
- N9: 后台清理必须保守，只清理系统可证明属于自动临时 worktree 且无成果的目录。

## 不做的事

- 不做 worktree 之间的合并策略；合并交给上层通过 Git merge 或人工 review 决定。
- 不做跨 worktree 的代码同步。
- 不做多 Agent 并行编排或团队协作调度。
- 不允许 `/worktree create` 第一版指定 base 分支、tag 或提交。
- 不把主仓库未提交修改复制或应用到新 worktree。
- 不为 Fork 式子 Agent 和 Hook 子 Agent 默认启用 worktree 隔离。
- 不保证依赖目录软链适用于所有 Node.js、monorepo 或依赖 `__dirname` 的项目；需要时由项目选择独立安装依赖。
- 不自动清理用户手动创建的 worktree。
- 不在进入 worktree 时清空缓存。

## 验收标准

- AC1: 用户执行 `/worktree create my-feature` 后，系统在固定管理目录下创建 worktree，并创建分支 `worktree-my-feature`。
- AC2: 用户执行 `/worktree create team-refactor/alice` 后，系统创建嵌套目录，并创建分支 `worktree-team-refactor+alice`。
- AC3: 用户传入包含 `..`、反斜杠、空段、Windows 保留名或超长名称时，创建被拒绝并返回明确原因。
- AC4: 主仓库存在未提交修改时创建 worktree，创建仍可进行，但结果中包含“未提交修改不会进入 worktree”的警告。
- AC5: 已存在可恢复 worktree 目录时，系统通过读取 `.git` 指针和 HEAD/ref 文件恢复当前 commit，不执行 Git 创建命令，并跳过环境初始化。
- AC6: 已存在目录不可恢复时，系统回退到正常 Git Worktree 创建流程，并返回创建结果或可诊断错误。
- AC7: Git 创建过程中遇到需要凭证或交互输入的场景时，命令不会挂起，而是失败并给出可观察错误。
- AC8: 新建 worktree 后，主仓库存在 `settings.local.json` 时，该文件被复制到 worktree；不存在时只产生警告。
- AC9: 主仓库存在 `.husky/` 时，新 worktree 的 Git hooks 配置指向该目录；没有 `.husky/` 时回退到 Git hooks 目录。
- AC10: `settings.worktree.symlinkDirectories` 声明的依赖目录存在时，新 worktree 中出现指向主仓库对应目录的软链；失败时只记录警告。
- AC11: 根目录 `.worktreeinclude` 匹配到被忽略的 `.env` 文件时，新 worktree 中复制出该文件；未匹配的被忽略文件不会复制。
- AC12: 用户执行 `/worktree list` 时，可以看到 worktree 名称、路径、分支、HEAD、是否当前会话和是否有变更摘要。
- AC13: 用户执行 `/worktree enter my-feature` 后，系统持久化当前 worktree session；随后读取文件、写入文件和执行命令都发生在该 worktree 路径下。
- AC14: `/worktree enter` 后，进程 cwd 不作为切换依据；退出前后路径相关缓存不需要清空也不会混读主仓库与 worktree 文件。
- AC15: LunaCode 重启后，如果 session 指向的 worktree 有效，系统自动恢复该会话，后续工具调用继续落在该 worktree。
- AC16: LunaCode 重启后，如果 session 指向的 worktree 不存在或 HEAD 无法解析，系统清除 session 并提示警告。
- AC17: 用户执行 `/worktree exit` 后，当前 worktree session 被清除；后续工具调用恢复到主仓库工作根。
- AC18: 删除无未提交修改且无新增 commit 的 worktree 时，系统删除 worktree 并删除对应 `worktree-` 分支。
- AC19: 删除存在未提交修改的 worktree 且未显式确认丢弃时，系统拒绝删除并提示需要显式确认。
- AC20: 删除存在基线之后新增 commit 的 worktree 且未显式确认丢弃时，系统拒绝删除并保留路径和分支。
- AC21: 用户显式确认丢弃后，系统允许强制删除有变更的 worktree，并给出删除结果。
- AC22: 定义式子 Agent 角色声明 `isolation: worktree` 后，启动该子 Agent 会自动创建 `agent-a` 加 7 位随机 hex 命名的 worktree。
- AC23: 未声明 `isolation: worktree` 的定义式子 Agent、Fork 式子 Agent 和 Hook 子 Agent 不会自动创建 worktree。
- AC24: 自动 worktree 中的子 Agent 修改文件后，任务结束时该 worktree 被保留，并向主 Agent 通知路径和分支。
- AC25: 自动 worktree 中的子 Agent 只读分析且没有留下变更时，任务结束后该 worktree 被自动清理。
- AC26: 后台过期清理只处理名称匹配 `agent-a` 加 7 位 hex 或工作流 `wf_` 固定 hex 格式、且分支名前缀为 `worktree-` 的 LunaCode 管理 worktree。
- AC27: 后台过期清理遇到用户手动创建的 `my-feature` worktree 时，即使过期也不会删除。
- AC28: 后台过期清理遇到有未提交修改或新增 commit 的自动 worktree 时，即使过期也会保留。
- AC29: 真实对话端到端测试中，主 Agent 可通过 `/worktree create` 创建并进入 worktree，在该目录里调用读写和命令工具，退出后工具调用回到主仓库。
- AC30: 真实对话端到端测试中，声明 `isolation: worktree` 的子 Agent 能在独立 worktree 中完成文件操作，完成后主 Agent 能收到保留或清理结果。
