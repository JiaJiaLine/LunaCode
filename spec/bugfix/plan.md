# 三个已确认 Bug 修复 Plan

## 架构概览
本次修复分为命令入口、网络隔离、权限规则和配置/文档四部分。

命令入口由 `SkillCommandRegistrar` 负责。它在动态 Skill 命令之外注册通用 `/skill` 和 `/skills`，不带参数时展示列表，带名称时复用现有 `SkillInvocationRequest` 流程。

网络隔离由权限层处理。新增 `BashNetworkAccessScanner`，由 `PermissionTargetExtractor` 在 Bash 工具抽取目标时调用。它先扫描命令文本，再识别可能被执行的脚本路径并读取脚本内容；发现联网模式时返回 `networkErrors`。`DefaultPermissionEngine` 在规则和模式判断之前处理 `networkErrors`，返回 `PermissionDecisionLayer.NETWORK`。

`always` 规则仍由 `PermissionRuleStore` 写入本地 `.lunacode/permissions.local.yaml`。`DefaultPermissionEngine` 改进 Bash 建议规则生成，复合命令使用首段前缀；`PermissionRuleParser` 兼容历史 `\)` 和 `\(`；`YamlPermissionRuleStore` 追加前先去重。

## 核心数据结构

### `BashNetworkAccessScanner`
- 输入：Bash 命令文本和 `PathSandbox`。
- 输出：首个网络访问拒绝原因。
- 职责：扫描命令文本、识别脚本候选、读取脚本内容、匹配 URL 和常见联网命令。

### `PermissionTargetExtractor.ExtractionResult`
- 新增 `networkErrors` 字段。
- `sandboxErrors` 继续承载路径沙箱错误。

### `PermissionDecisionLayer.NETWORK`
- 表示默认网络隔离拒绝。
- 优先级高于 allow/deny 规则和权限模式。

## 模块设计

### command
`SkillCommandRegistrar` 注册 `/skill` 通用命令，并跳过名为 `skill` 或 `skills` 的短命令冲突。

### permission
`BashPathScanner` 不再把 URI 当作路径；`PermissionTargetExtractor` 在网络隔离关闭时扫描 Bash 网络访问；`DefaultPermissionEngine` 先处理网络拒绝，再处理路径、规则和模式。

### config
`config.yaml` 显式加入：

```yaml
sandbox:
  network_enabled: false
```

### test
新增和更新命令、权限、路径扫描、规则解析和规则存储测试，覆盖三个 bug 的复现路径和修复行为。

## 技术决策
| 决策点 | 选择 | 理由 |
|---|---|---|
| Windows 断网 | 权限层启发式审计 | 当前 Windows 执行器是 direct shell，没有系统级网络 namespace；权限层能立即堵住用户报告的绕过路径。 |
| URL 处理 | URL 不走路径沙箱 | 网络开启时应允许进入普通权限流程，网络关闭时由 NETWORK 层拒绝。 |
| 脚本内容 | 执行前读取脚本审计 | 修复 WriteFile 写脚本再 Bash 执行的跨工具绕过。 |
| always 规则 | Bash 复合命令生成前缀规则 | 更符合用户选择 always 的预期，减少相似命令反复审批。 |