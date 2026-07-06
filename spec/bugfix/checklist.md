# 三个已确认 Bug 修复 Checklist

## 实现完整性
- [x] `/skill` 不带参数列出 Skill（验证：`SkillCommandRegistrarTest` 通过）。
- [x] `/skill <name> [arguments]` 转发到 Skill 调用（验证：`SkillCommandRegistrarTest` 通过）。
- [x] 默认网络隔离拒绝命令文本中的 URL/curl 等网络访问（验证：`DefaultPermissionEngineTest` 通过）。
- [x] 默认网络隔离拒绝被执行脚本中的 URL/PowerShell Web cmdlet（验证：`DefaultPermissionEngineTest` 通过）。
- [x] `network_enabled: true` 时 URL 不再被路径沙箱误判（验证：`DefaultPermissionEngineTest` 和 `BashPathScannerTest` 通过）。
- [x] `always` 生成可复用 Bash 前缀规则（验证：`DefaultPermissionEngineTest` 通过）。
- [x] 本地权限规则追加去重（验证：`YamlPermissionRuleStoreTest` 通过）。

## 集成
- [x] Orchestrator 将真实 sandbox 配置传给权限抽取器（验证：相关模块测试通过）。
- [x] `config.yaml` 显式包含 `sandbox.network_enabled: false`（验证：人工读取配置确认）。

## 编译与测试
- [x] 局部测试通过：`mvn -q "-Dtest=SkillCommandRegistrarTest,DefaultPermissionEngineTest,BashPathScannerTest,PermissionRuleParserTest,YamlPermissionRuleStoreTest" test`。
- [x] 相关模块测试通过：`mvn -q "-Dtest=SkillCommandRegistrarTest,SkillOrchestratorTest,ToolPermissionGatewayTest,AgentToolRunnerPermissionTest,DefaultPermissionEngineTest,BashPathScannerTest,PermissionRuleMatcherTest,PermissionRuleParserTest,YamlPermissionRuleStoreTest,ConfigLoaderTest" test`。
- [x] 全量测试通过：`mvn -q test`。

## 端到端
- [ ] tmux 端到端测试未执行：当前环境是 Windows PowerShell，未发现可直接使用的 tmux 会话能力；本次以全量自动化测试和权限复现场景测试完成验证。