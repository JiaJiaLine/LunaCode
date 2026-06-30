# LunaCode 权限系统 Checklist

> 每一项都通过运行代码、观察 TUI 行为或检查命令输出验证，聚焦系统行为。

## 实现完整性

- [ ] 所有工具执行前都会产生明确权限决策结果 allow、deny 或 ask（验证：运行 `mvn -Dtest=DefaultPermissionEngineTest,AgentToolRunnerPermissionTest test`，观察测试覆盖所有三类决策）
- [ ] 权限决策结果包含决策层级、原因、命中规则或沙箱/黑名单说明（验证：运行权限引擎测试，断言 metadata 或评估结果包含 `permissionLayer`、`reason`、`matchedRule`）
- [ ] 权限拒绝不会终止 Agent Loop，而是作为结构化工具结果回灌给模型（验证：运行 `mvn -Dtest=AgentToolRunnerPermissionTest test`，观察拒绝工具调用后返回 `ToolResult.error` 且 loop 可继续）
- [ ] 既有纯对话、工具调用、Agent Loop、Plan Mode 和结构化 Prompt 行为不回退（验证：运行 `mvn test`，所有既有测试通过）

## 配置

- [ ] 主配置能解析 `permissions.mode`，未配置时默认为 default（验证：运行 `mvn -Dtest=ConfigLoaderTest test`，断言默认和显式模式解析正确）
- [ ] 主配置能解析 `sandbox.network_enabled`，未配置时默认为 false（验证：运行 `mvn -Dtest=ConfigLoaderTest test`，断言默认断网）
- [ ] 主配置能解析 `sandbox.extra_roots` 的 name 和 path（验证：运行 `mvn -Dtest=ConfigLoaderTest test`，断言额外根目录被加载）
- [ ] `config.example.yaml` 展示权限模式、默认断网和额外根目录配置示例（验证：查看文件内容包含 `permissions.mode`、`sandbox.network_enabled`、`sandbox.extra_roots`）

## 规则文件

- [ ] 用户级、项目级、本地级三层规则文件按固定路径加载（验证：运行 `mvn -Dtest=YamlPermissionRuleStoreTest test`，断言 `~/.lunacode/permissions.yaml`、`.lunacode/permissions.yaml`、`.lunacode/permissions.local.yaml` 均可加载）
- [ ] YAML 顶层列表格式能解析 `rule` 和 `effect`（验证：运行 `mvn -Dtest=PermissionRuleParserTest,YamlPermissionRuleStoreTest test`）
- [ ] 规则支持 YAML 注释且保留文件顺序（验证：运行规则 store 测试，断言带注释文件加载成功且 order 递增）
- [ ] 某一层规则文件损坏时只忽略该层，其他层继续生效（验证：运行 `mvn -Dtest=YamlPermissionRuleStoreTest test`，断言 warning 存在且有效层仍参与匹配）
- [ ] 本地级规则文件损坏时，“始终允许”不会静默写入失败文件（验证：运行 `mvn -Dtest=YamlPermissionRuleStoreTest test`，断言追加失败并返回可理解错误）

## 规则匹配

- [ ] Bash 规则按命令文本匹配（验证：`Bash(git *)` 匹配 `git status --short`，运行 `mvn -Dtest=PermissionRuleMatcherTest test`）
- [ ] ReadFile、WriteFile、EditFile 规则按虚拟路径匹配（验证：`ReadFile(/project/src/**)` 匹配工作区 src 文件，运行规则匹配测试）
- [ ] Glob 规则按规范化后的 glob 入参或搜索范围匹配（验证：运行 `mvn -Dtest=PermissionRuleMatcherTest test`）
- [ ] Grep 规则按搜索 pattern 匹配（验证：`Grep(*password*)` 拒绝敏感搜索，运行规则匹配测试）
- [ ] 任意层级 deny 命中后不可被任何 allow 覆盖（验证：用户级 deny 与本地级 allow 同时存在时最终为 deny）
- [ ] 没有 deny 命中时，allow 按本地级、项目级、用户级优先级判断（验证：三层 allow 同时匹配时最终采用本地级）
- [ ] 同一层级内后定义规则优先（验证：同层多个 allow 或 deny 同时匹配时采用文件中靠后的规则作为命中结果）

## 路径沙箱

- [ ] 工作区内普通相对路径通过沙箱并映射为 `/project/...`（验证：运行 `mvn -Dtest=DefaultPathSandboxTest test`）
- [ ] 额外根目录路径通过沙箱并映射为 `/roots/{name}/...`（验证：配置 `cache` 根目录后，路径映射为 `/roots/cache/...`）
- [ ] `..` 逃逸路径被拒绝（验证：运行 `DefaultPathSandboxTest`，断言 `../outside.txt` 被拒绝）
- [ ] 允许根目录外绝对路径被拒绝（验证：运行 `DefaultPathSandboxTest`，断言 `/etc/passwd` 或 Windows 外部路径被拒绝）
- [ ] 指向允许根目录外的符号链接被拒绝（验证：运行 `DefaultPathSandboxTest`，断言 symlink 逃逸失败）
- [ ] WriteFile 创建不存在的新文件时先校验最近存在父目录真实路径（验证：运行 `DefaultPathSandboxTest`，断言安全父目录内新文件通过，逃逸父目录被拒绝）
- [ ] Bash cwd 位于允许根目录外时被拒绝（验证：运行 `DefaultPermissionEngineTest`，断言 Bash cwd 沙箱失败）
- [ ] Bash 命令文本中可识别的越界路径被拒绝（验证：运行 `mvn -Dtest=BashPathScannerTest test`，断言 `cat /etc/passwd`、`python a.py ../x`、`echo hi > ../x` 被拒绝）

## Bash 黑名单

- [ ] `rm -rf /` 命中黑名单并硬拒绝（验证：运行 `mvn -Dtest=DangerousCommandBlacklistTest,DefaultPermissionEngineTest test`）
- [ ] fork bomb 或等价资源炸弹命中黑名单并硬拒绝（验证：运行黑名单测试）
- [ ] 黑名单命中优先于 allow 规则和 bypassPermissions（验证：配置 allow 并切到 bypassPermissions 后，黑名单命令仍返回 deny）
- [ ] 文件工具不会因为 Bash 黑名单被拒绝（验证：运行权限引擎测试，WriteFile/EditFile 继续按沙箱、敏感路径、规则和模式判断）

## Linux OS 级沙箱

- [ ] Linux Bash 命令通过 bubblewrap 包裹执行（验证：运行 `mvn -Dtest=BubblewrapCommandSandboxTest test`，断言命令参数包含 `bwrap`）
- [ ] Linux Bash 命令启用 seccomp 参数（验证：运行 `BubblewrapCommandSandboxTest`，断言 seccomp 参数存在）
- [ ] 默认情况下 bubblewrap 参数包含 `--unshare-net`（验证：运行 `BubblewrapCommandSandboxTest`，断言默认断网）
- [ ] 配置 `sandbox.network_enabled: true` 后不包含 `--unshare-net`（验证：运行 `BubblewrapCommandSandboxTest`，断言网络开启时参数变化）
- [ ] bubblewrap 绑定项目根和所有额外根目录（验证：运行 `BubblewrapCommandSandboxTest`，断言 bind 参数包含允许根目录）
- [ ] Linux 上 bubblewrap 或 seccomp 不可用时返回结构化错误，不回退到无沙箱直接执行（验证：运行 `mvn -Dtest=BashToolTest test`，模拟不可用依赖并断言 `command_error` 或沙箱错误）
- [ ] 开启网络不绕过黑名单、路径沙箱、deny 规则和确认（验证：运行 `DefaultPermissionEngineTest`，断言 `sandbox.network_enabled: true` 只影响命令包装，不改变权限结果）

## 权限模式

- [ ] default 模式下 ReadFile、Glob、Grep 在沙箱通过后自动 allow（验证：运行 `mvn -Dtest=PermissionModePolicyTest test`）
- [ ] default 模式下 WriteFile、EditFile 和 Bash 默认 ask（验证：运行权限模式测试）
- [ ] acceptEdits 模式下 WriteFile 和 EditFile 自动 allow，Bash 仍 ask（验证：运行权限模式测试）
- [ ] plan 模式权限矩阵与 default 一致（验证：运行权限模式测试）
- [ ] bypassPermissions 模式自动放行大多数未命中操作，但仍受黑名单和路径沙箱限制（验证：运行 `DefaultPermissionEngineTest`）
- [ ] `/plan` 请求使用 plan/default 权限矩阵，不继承 acceptEdits 或 bypassPermissions（验证：运行 `mvn -Dtest=PermissionCommandTest,DefaultChatOrchestratorTest test`）

## 运行时切换

- [ ] `/permissions` 显示当前权限模式且不进入模型历史（验证：运行 `mvn -Dtest=PermissionCommandTest test`）
- [ ] `/permissions default` 能切换到 default（验证：运行 `PermissionCommandTest`，断言后续请求使用 default）
- [ ] `/permissions acceptEdits` 能切换到 acceptEdits（验证：运行 `PermissionCommandTest`，断言后续编辑工具自动 allow）
- [ ] `/permissions plan` 能切换到 plan 权限模式（验证：运行 `PermissionCommandTest`）
- [ ] `/permissions bypassPermissions` 触发二次确认（验证：运行 `PermissionCommandTest`，断言未确认时模式不变）
- [ ] 当前权限模式能在状态栏、权限确认提示或 AgentEvent 中观察到（验证：运行 `DefaultChatOrchestratorTest` 和 TUI 测试，断言状态包含权限模式）

## 人在回路

- [ ] ask 权限提示包含工具名、参数摘要、目标路径或命令摘要、当前权限模式、未命中规则原因和可选动作（验证：运行 `AgentToolRunnerPermissionTest`，断言 prompt 内容）
- [ ] 用户输入 `yes`、`y`、`允许` 或 `本次允许` 时只允许当前工具调用（验证：运行权限确认测试，下一次同类调用仍 ask）
- [ ] 用户输入 `always` 或 `始终允许` 时写入 `.lunacode/permissions.local.yaml`（验证：运行 `AgentToolRunnerPermissionTest`，断言本地规则文件新增条目）
- [ ] 始终允许生成的 allow 规则精确到本次参数（验证：对 `Bash(git status --short)` 选择始终允许后，文件中不出现 `Bash(git status*)` 或 `Bash(git *)`）
- [ ] 用户拒绝后工具不执行，Agent Loop 继续并把拒绝结果回灌给模型（验证：运行 `AgentToolRunnerPermissionTest`，断言 tool result 为 permission_denied）

## 敏感路径

- [ ] `.lunacode/config.yaml` 未命中规则时默认 ask（验证：运行 `DefaultPermissionEngineTest#sensitivePathsAskByDefault test`）
- [ ] `.lunacode/permissions.local.yaml` 未命中规则时默认 ask（验证：运行敏感路径测试）
- [ ] `.lunacode/skills/**` 未命中规则时默认 ask（验证：运行敏感路径测试）
- [ ] 敏感路径被任意层 deny 命中时直接拒绝（验证：配置 `ReadFile(/project/.lunacode/**)` deny 并运行权限引擎测试）
- [ ] 权限提示、错误摘要和工具结果不会泄露 API Key、Token 或已知敏感值（验证：运行 `AgentToolRunnerPermissionTest`，断言输出经过 SensitiveValueMasker）

## 编译与测试

- [ ] 权限子系统单元测试通过（验证：运行 `mvn -Dtest=DefaultPermissionEngineTest,PermissionRuleMatcherTest,DefaultPathSandboxTest,DangerousCommandBlacklistTest test`）
- [ ] Bash OS 沙箱测试通过（验证：运行 `mvn -Dtest=BubblewrapCommandSandboxTest,BashToolTest test`）
- [ ] Orchestrator 和 TUI 权限模式测试通过（验证：运行 `mvn -Dtest=PermissionCommandTest,DefaultChatOrchestratorTest,LanternaLunaTuiTest test`）
- [ ] 全量测试通过（验证：运行 `mvn test`，命令退出码为 0）
- [ ] 打包成功（验证：运行 `mvn package -DskipTests`，生成可运行 jar）

## 端到端场景

- [ ] 场景 1：读取文件自动放行。启动 LunaCode 后输入“读取当前项目的 pom.xml 并总结依赖”，可观察到 ReadFile 自动执行并返回中文总结（验证：tmux 中启动 LunaCode，观察 TUI 工具状态和最终回复）
- [ ] 场景 2：写入操作触发确认并拒绝后继续。输入“在项目里新建一个临时说明文件”，权限提示出现后选择拒绝，可观察到文件未创建，Agent Loop 继续给出中文解释或替代方案（验证：tmux 观察 + 检查目标文件不存在）
- [ ] 场景 3：始终允许写入本地规则。对安全命令 `Bash(git status --short)` 选择始终允许，可观察到 `.lunacode/permissions.local.yaml` 追加精确规则，再次执行同命令不重复询问（验证：tmux 观察 + 查看本地规则文件）
- [ ] 场景 4：deny 不可翻转。用户级配置 `Bash(rm *) deny`，本地级配置 `Bash(rm *) allow`，请求执行 `rm something` 时仍被拒绝（验证：tmux 观察权限拒绝原因来自 deny）
- [ ] 场景 5：敏感路径默认 ask。请求读取 `.lunacode/config.yaml`，可观察到权限确认；拒绝后不显示配置内容（验证：tmux 观察权限提示和最终回复）
- [ ] 场景 6：路径沙箱阻止逃逸。请求读取 `../outside.txt` 或通过 symlink 指向工作区外文件，可观察到沙箱拒绝且 Agent Loop 继续（验证：tmux 观察工具结果）
- [ ] 场景 7：运行时切换权限模式。输入 `/permissions` 查看当前模式，再输入 `/permissions acceptEdits`，随后请求编辑工作区内安全文件，可观察到编辑操作不再 ask；切回 default 后同类编辑再次 ask（验证：tmux 观察状态栏和权限提示）
- [ ] 场景 8：bypassPermissions 二次确认。输入 `/permissions bypassPermissions` 后取消确认，可观察到模式保持不变；确认后切换成功，但黑名单和路径沙箱仍拒绝危险操作（验证：tmux 观察模式和拒绝结果）
- [ ] 场景 9：Linux 默认断网。Linux 环境中未开启 `sandbox.network_enabled` 时请求执行需要网络的命令，可观察到 bubblewrap 断网导致网络访问失败（验证：tmux 中运行 LunaCode，观察 Bash 工具结果）
- [ ] 场景 10：Linux 配置开启网络。设置 `sandbox.network_enabled: true` 后重启 LunaCode，请求执行需要网络的命令，可观察到命令在其它权限层允许后能访问网络（验证：tmux 观察 Bash 工具结果）

