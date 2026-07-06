# 三个已确认 Bug 修复 Spec

## 背景
用户反馈 LunaCode 存在三个问题：`/skill` 命令不可用；默认网络隔离可以被模型通过“写脚本再执行脚本”的方式绕过；审批命令时选择 `always` 后仍会反复要求审批。

## 目标
- 提供可用的通用 `/skill` 命令入口，同时保留现有 `/commit`、`/test` 等 Skill 直达命令。
- 在默认 `sandbox.network_enabled: false` 下，Bash 直接联网和脚本内容中的联网行为都应被权限层拒绝。
- 显式开启 `sandbox.network_enabled: true` 时，URL 不应被路径沙箱误判为越界路径。
- `always` 写入的权限规则应能复用，并避免重复追加完全相同的规则。
- 在 `config.yaml` 中显式展示网络隔离配置。

## 功能需求
- F1: `/skill` 不带参数时列出当前可用 Skill。
- F2: `/skill <name> [arguments]` 应转发为一次 Skill 调用。
- F3: 默认网络隔离应拒绝 Bash 命令中的 URL、curl/wget、PowerShell Web cmdlet、git 远程操作和常见包管理器联网安装。
- F4: 默认网络隔离应读取被执行的本地脚本文件，发现脚本内网络访问时拒绝执行。
- F5: `sandbox.network_enabled: true` 时跳过网络隔离审计，URL 不再进入路径沙箱校验。
- F6: `always` 建议规则对 Bash 复合命令生成可复用前缀规则，并兼容旧的括号转义写法。

## 非功能需求
- N1: 网络隔离拒绝发生在用户 allow 规则和权限模式之前，不能被 `always` 或 `bypassPermissions` 绕过。
- N2: 脚本审计限制最大读取大小，无法审计过大的脚本时在默认隔离下拒绝。
- N3: 改动保持在 command、permission、orchestrator 和配置范围内。

## 不做的事情
- 不实现 Windows 系统级网络命名空间隔离。
- 不阻止普通文件写入 URL 文本；仅在 Bash 执行命令或脚本时拦截网络访问。
- 不改变已有短 Skill 命令的行为。

## 验收标准
- AC1: `/skill`、`/skills` 出现在可见命令中，`/skill commit args` 能提交 `commit` Skill 调用。
- AC2: `curl https://...` 在默认隔离下被 `NETWORK` 层拒绝。
- AC3: `powershell -File fetch_skill.ps1` 在脚本内含 `Invoke-WebRequest https://...` 时被 `NETWORK` 层拒绝。
- AC4: `network_enabled: true` 时 URL 命令进入权限模式判断而不是路径沙箱拒绝。
- AC5: `always` 对复合 Bash 命令生成 `Bash(<首段>*)` 规则，同一规则不会重复写入。
- AC6: 全量 Maven 测试通过。