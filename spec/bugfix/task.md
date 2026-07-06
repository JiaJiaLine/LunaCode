# 三个已确认 Bug 修复 Tasks

## 文件清单
| 操作 | 文件 | 职责 |
|---|---|---|
| 修改 | `src/main/java/com/lunacode/command/SkillCommandRegistrar.java` | 增加 `/skill` 和 `/skills` 通用入口 |
| 修改 | `src/test/java/com/lunacode/command/SkillCommandRegistrarTest.java` | 覆盖通用 Skill 命令 |
| 新建 | `src/main/java/com/lunacode/permission/BashNetworkAccessScanner.java` | 扫描 Bash 命令和脚本内网络访问 |
| 修改 | `src/main/java/com/lunacode/permission/BashPathScanner.java` | URL 不再被当作路径 |
| 修改 | `src/main/java/com/lunacode/permission/PermissionDecisionLayer.java` | 新增 NETWORK 层 |
| 修改 | `src/main/java/com/lunacode/permission/PermissionTargetExtractor.java` | 接入网络扫描结果 |
| 修改 | `src/main/java/com/lunacode/permission/DefaultPermissionEngine.java` | 网络拒绝优先于规则和权限模式；改进 always 建议规则 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 传递真实 sandbox 配置到权限抽取器 |
| 修改 | `src/main/java/com/lunacode/permission/PermissionRuleParser.java` | 兼容旧括号转义规则 |
| 修改 | `src/main/java/com/lunacode/permission/YamlPermissionRuleStore.java` | 本地 allow 规则追加前去重 |
| 修改 | `config.yaml` | 显式展示默认网络隔离配置 |
| 修改 | `src/test/java/com/lunacode/permission/*Test.java` | 覆盖网络隔离、URL、always 和规则存储 |

## T1: 修复 `/skill` 命令
**验证：** `SkillCommandRegistrarTest` 通过，确认 `/skill` 列表和转发行为。

## T2: 修复网络隔离绕过
**验证：** `DefaultPermissionEngineTest` 覆盖直连 URL、curl、PowerShell 脚本内 URL、`network_enabled: true` 行为。

## T3: 修复 `always` 重复审批
**验证：** `DefaultPermissionEngineTest` 覆盖复合 Bash 命令前缀规则；`PermissionRuleParserTest` 覆盖旧括号转义；`YamlPermissionRuleStoreTest` 覆盖去重。

## T4: 配置和集成
**验证：** `config.yaml` 包含 `sandbox.network_enabled: false`；相关模块测试和全量测试通过。