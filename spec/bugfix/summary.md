# 修复总结

## 已确认并修复的问题

### 1. `/skill` 命令不可用
原因：系统只把每个 Skill 注册成独立斜杠命令，例如 `/commit`、`/test`，没有通用 `/skill` 命令。

修复：`SkillCommandRegistrar` 现在注册 `/skill` 和 `/skills`。`/skill` 列出可用 Skill；`/skill commit 重点关注安全` 等价于调用 `commit` Skill 并传入参数。

### 2. 默认网络隔离可被脚本绕过
原因：Windows 下命令执行使用 `DirectCommandSandbox`，没有系统级断网；原路径扫描只检查 Bash 命令字符串，不读取被执行脚本内容。模型可以用 `WriteFile` 写入含 URL 的脚本，再用 Bash 执行脚本绕过字符串检查。

修复：新增 `BashNetworkAccessScanner`，在默认 `sandbox.network_enabled: false` 时扫描 Bash 命令和被执行脚本内容。发现 URL、curl/wget、PowerShell Web cmdlet、git 远程操作、常见包管理器联网安装时，权限引擎在 `NETWORK` 层直接拒绝。`always` 和 `bypassPermissions` 不能绕过该层。

同时修复 URL 被路径沙箱误判的问题：`BashPathScanner` 不再把 `https://...` 当路径。显式配置 `sandbox.network_enabled: true` 时，URL 命令会进入普通权限模式判断。

### 3. `always` 仍反复审批
原因：Bash 的建议 allow 规则过去倾向于完整命令精确匹配，复合命令或轻微参数变化会重复询问；旧逻辑还会把括号写成转义形式，匹配器按字面反斜杠处理时可能匹配失败。

修复：复合 Bash 命令的建议规则现在保存为首段前缀，例如 `Bash(mkdir SkillTest*)`。规则解析器兼容旧的 `\)`、`\(` 写法；本地权限规则追加前会去重，避免重复写入。

## 配置变更
`config.yaml` 已加入：

```yaml
sandbox:
  network_enabled: false
```

## 验证结果
- 相关局部测试通过。
- 相关模块测试通过。
- 全量 `mvn -q test` 通过。
- tmux E2E 未执行，原因是当前 Windows PowerShell 环境没有可用 tmux；已用自动化测试覆盖 `/skill`、网络绕过复现和 `always` 规则行为。