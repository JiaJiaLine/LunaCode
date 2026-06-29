# LunaCode 权限系统 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/lunacode/config/PermissionConfig.java` | 主配置中的默认权限模式 |
| 新建 | `src/main/java/com/lunacode/config/SandboxConfig.java` | OS 沙箱网络开关和额外根目录配置 |
| 新建 | `src/main/java/com/lunacode/config/SandboxRootConfig.java` | 单个额外根目录配置 |
| 修改 | `src/main/java/com/lunacode/config/ConfigLoader.java` | 解析 `permissions` 和 `sandbox` 配置块 |
| 修改 | `src/main/java/com/lunacode/config/ProviderConfig.java` | 暴露权限和沙箱配置 |
| 修改 | `config.example.yaml` | 提供权限模式、沙箱和额外根目录配置示例 |
| 修改 | `src/main/java/com/lunacode/runtime/AgentRunConfig.java` | 携带有效权限模式 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionMode.java` | 权限模式枚举 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionEffect.java` | 规则效果枚举 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionRuleLevel.java` | 规则层级枚举 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionDecisionLayer.java` | 权限决策来源枚举 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionRule.java` | 规则结构 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionRuleMatch.java` | 命中规则说明 |
| 新建 | `src/main/java/com/lunacode/permission/LoadedPermissionRules.java` | 三层规则加载结果和 warning |
| 新建 | `src/main/java/com/lunacode/permission/PermissionRuleParser.java` | 解析 `Tool(pattern)` |
| 新建 | `src/main/java/com/lunacode/permission/PermissionRuleMatcher.java` | deny/allow 匹配和优先级 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionRuleStore.java` | 规则加载和本地追加接口 |
| 新建 | `src/main/java/com/lunacode/permission/YamlPermissionRuleStore.java` | YAML 规则文件实现 |
| 新建 | `src/main/java/com/lunacode/permission/SandboxRoot.java` | 允许根目录运行时模型 |
| 新建 | `src/main/java/com/lunacode/permission/VirtualPath.java` | 真实路径和虚拟路径映射 |
| 新建 | `src/main/java/com/lunacode/permission/PathIntent.java` | 读、写、命令 cwd、命令参数路径意图 |
| 新建 | `src/main/java/com/lunacode/permission/PathSandbox.java` | 路径沙箱接口 |
| 新建 | `src/main/java/com/lunacode/permission/DefaultPathSandbox.java` | 路径沙箱实现 |
| 新建 | `src/main/java/com/lunacode/permission/BashPathScanner.java` | Bash 命令明显路径片段扫描 |
| 新建 | `src/main/java/com/lunacode/permission/DangerousCommandBlacklist.java` | Bash 危险命令黑名单 |
| 新建 | `src/main/java/com/lunacode/permission/SensitivePathPolicy.java` | 敏感路径默认 ask 策略 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionTarget.java` | 工具规则匹配目标 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionTargetKind.java` | 匹配目标类型 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionTargetExtractor.java` | 从 ToolUse 提取匹配目标和路径 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionEvaluationRequest.java` | 权限评估输入 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionEvaluation.java` | 权限评估结果 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionModePolicy.java` | 权限模式默认矩阵 |
| 新建 | `src/main/java/com/lunacode/permission/DefaultPermissionEngine.java` | 统一权限决策入口 |
| 新建 | `src/main/java/com/lunacode/permission/PermissionModeSession.java` | 当前会话权限模式状态 |
| 修改 | `src/main/java/com/lunacode/tool/ToolPermissionGateway.java` | 接入详细权限结果 |
| 修改 | `src/main/java/com/lunacode/tool/DefaultToolPermissionGateway.java` | 迁移为新权限引擎适配器 |
| 新建 | `src/main/java/com/lunacode/tool/CommandSandbox.java` | Bash 命令沙箱接口 |
| 新建 | `src/main/java/com/lunacode/tool/BubblewrapCommandSandbox.java` | Linux bubblewrap + seccomp 包装器 |
| 新建 | `src/main/java/com/lunacode/tool/DirectCommandSandbox.java` | 非 Linux 直接命令包装器 |
| 修改 | `src/main/java/com/lunacode/tool/BashTool.java` | 通过 CommandSandbox 构造命令 |
| 修改 | `src/main/java/com/lunacode/tool/ToolExecutionContext.java` | 携带 CommandSandbox、SandboxConfig 和允许根目录 |
| 修改 | `src/main/java/com/lunacode/interaction/PermissionConfirmationRequest.java` | 展示权限模式、目标摘要和可选动作 |
| 新建 | `src/main/java/com/lunacode/interaction/PermissionConfirmationAnswer.java` | 本次允许、始终允许、拒绝 |
| 修改 | `src/main/java/com/lunacode/interaction/PermissionConfirmationBroker.java` | 返回 PermissionConfirmationAnswer |
| 修改 | `src/main/java/com/lunacode/interaction/BlockingPermissionConfirmationBroker.java` | 解析确认输入 |
| 修改 | `src/main/java/com/lunacode/agent/execution/AgentToolRunner.java` | 执行权限评估、确认、始终允许追加和拒绝回灌 |
| 修改 | `src/main/java/com/lunacode/agent/event/AgentEvent.java` | 增加权限允许、拒绝、模式切换和规则 warning 事件 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 处理 `/permissions` 命令并维护 PermissionModeSession |
| 修改 | `src/main/java/com/lunacode/orchestrator/StatusSnapshot.java` | 暴露当前权限模式 |
| 修改 | `src/main/java/com/lunacode/tui/LanternaLunaTui.java` | 展示权限模式和权限事件 |
| 修改 | `src/main/java/com/lunacode/app/LunaCodeApplication.java` | 装配权限引擎、规则 store、沙箱和 Bash command sandbox |
| 新建 | `src/test/java/com/lunacode/permission/DangerousCommandBlacklistTest.java` | 黑名单单元测试 |
| 新建 | `src/test/java/com/lunacode/permission/DefaultPathSandboxTest.java` | 路径沙箱单元测试 |
| 新建 | `src/test/java/com/lunacode/permission/BashPathScannerTest.java` | Bash 路径扫描单元测试 |
| 新建 | `src/test/java/com/lunacode/permission/PermissionRuleParserTest.java` | 规则解析单元测试 |
| 新建 | `src/test/java/com/lunacode/permission/PermissionRuleMatcherTest.java` | deny/allow 匹配优先级测试 |
| 新建 | `src/test/java/com/lunacode/permission/YamlPermissionRuleStoreTest.java` | 三层 YAML 加载和追加测试 |
| 新建 | `src/test/java/com/lunacode/permission/PermissionModePolicyTest.java` | 权限模式矩阵测试 |
| 新建 | `src/test/java/com/lunacode/permission/DefaultPermissionEngineTest.java` | 权限决策集成测试 |
| 新建 | `src/test/java/com/lunacode/tool/BubblewrapCommandSandboxTest.java` | bubblewrap 命令构造测试 |
| 新建 | `src/test/java/com/lunacode/agent/execution/AgentToolRunnerPermissionTest.java` | 工具执行权限集成测试 |
| 新建 | `src/test/java/com/lunacode/orchestrator/PermissionCommandTest.java` | `/permissions` 命令测试 |
| 修改 | 既有相关测试文件 | 按新接口更新构造参数和断言 |

## T1: 建立权限基础类型

**文件：** `src/main/java/com/lunacode/permission/PermissionMode.java`、`PermissionEffect.java`、`PermissionRuleLevel.java`、`PermissionDecisionLayer.java`、`PermissionRule.java`、`PermissionRuleMatch.java`、`PermissionEvaluation.java`、`PermissionEvaluationRequest.java`

**依赖：** 无

**步骤：**
1. 新建 `com.lunacode.permission` 包。
2. 添加权限模式、规则效果、规则层级和决策来源枚举。
3. 添加规则、命中结果、评估请求和评估结果 record。
4. 让 `PermissionEvaluation` 能表达 allow、ask、deny、原因、命中规则和可追加 allow 规则。

**验证：** 运行 `mvn -Dtest=PermissionModePolicyTest test`，预期编译能找到所有基础类型；测试可先为空骨架但项目编译通过。

## T2: 增加权限和沙箱配置结构

**文件：** `src/main/java/com/lunacode/config/PermissionConfig.java`、`SandboxConfig.java`、`SandboxRootConfig.java`、`ConfigLoader.java`、`ProviderConfig.java`、`config.example.yaml`

**依赖：** T1

**步骤：**
1. 新增 `PermissionConfig`，包含默认 `PermissionMode`。
2. 新增 `SandboxConfig`，包含 `networkEnabled` 和 `extraRoots`。
3. 新增 `SandboxRootConfig`，包含 `name` 和 `path`。
4. 扩展 `ConfigLoader.RawConfig`，解析 `permissions.mode`、`sandbox.network_enabled` 和 `sandbox.extra_roots`。
5. 扩展 `ProviderConfig` 暴露 `permissions()` 和 `sandbox()`。
6. 更新 `config.example.yaml`，展示 default 权限模式、默认断网和额外根目录配置。

**验证：** 运行 `mvn -Dtest=ConfigLoaderTest test`，预期旧配置仍加载成功，新配置能解析权限模式、网络开关和额外根目录。

## T3: 实现规则解析器

**文件：** `src/main/java/com/lunacode/permission/PermissionRuleParser.java`、`src/test/java/com/lunacode/permission/PermissionRuleParserTest.java`

**依赖：** T1

**步骤：**
1. 实现 `Tool(pattern)` 语法解析。
2. 校验工具名和括号内容不能为空。
3. 校验 effect 只接受 `allow` 和 `deny`。
4. 保留原始规则字符串，记录层级和文件内顺序。
5. 为非法格式返回可诊断错误。

**验证：** 运行 `mvn -Dtest=PermissionRuleParserTest test`，预期合法规则解析成功，缺括号、空工具名、未知 effect 均失败。

## T4: 实现三层 YAML 规则加载

**文件：** `src/main/java/com/lunacode/permission/LoadedPermissionRules.java`、`PermissionRuleStore.java`、`YamlPermissionRuleStore.java`、`src/test/java/com/lunacode/permission/YamlPermissionRuleStoreTest.java`

**依赖：** T3

**步骤：**
1. 定义用户级、项目级、本地级规则路径。
2. 用 Jackson YAML 读取顶层列表。
3. 文件不存在时返回空规则层。
4. 某层 YAML 损坏或字段非法时忽略该层，并记录 warning。
5. 保留同层规则顺序，后定义规则 order 更大。
6. 加载结果区分有效规则和失败层 warning。

**验证：** 运行 `mvn -Dtest=YamlPermissionRuleStoreTest test`，预期三层规则按路径加载，坏掉的项目级规则只影响项目级，用户级和本地级仍生效。

## T5: 实现本地级始终允许追加

**文件：** `src/main/java/com/lunacode/permission/YamlPermissionRuleStore.java`、`src/test/java/com/lunacode/permission/YamlPermissionRuleStoreTest.java`

**依赖：** T4

**步骤：**
1. 实现 `appendLocalAllow`。
2. 追加前先解析现有 `.lunacode/permissions.local.yaml`。
3. 本地级文件不存在时创建父目录和新文件。
4. 追加 `rule/effect` 顶层列表项到文件末尾。
5. 本地级文件损坏时拒绝追加并返回错误。

**验证：** 运行 `mvn -Dtest=YamlPermissionRuleStoreTest test`，预期始终允许追加到文件末尾，损坏文件不会被静默覆盖。

## T6: 建立允许根和虚拟路径模型

**文件：** `src/main/java/com/lunacode/permission/SandboxRoot.java`、`VirtualPath.java`、`PathIntent.java`

**依赖：** T2

**步骤：**
1. 建立项目根 `/project`。
2. 根据 `SandboxConfig.extraRoots` 建立 `/roots/{name}`。
3. 校验额外根目录 name 只包含字母、数字、下划线、连字符。
4. 将真实路径和虚拟路径映射保存为不可变结构。

**验证：** 运行 `mvn -Dtest=DefaultPathSandboxTest test`，预期项目根和额外根能生成稳定虚拟前缀。

## T7: 实现路径沙箱核心算法

**文件：** `src/main/java/com/lunacode/permission/PathSandbox.java`、`DefaultPathSandbox.java`、`src/test/java/com/lunacode/permission/DefaultPathSandboxTest.java`

**依赖：** T6

**步骤：**
1. 将请求路径转成绝对路径并 normalize。
2. 目标存在时调用 `toRealPath()` 解析符号链接。
3. 目标不存在时向上寻找最近存在父目录，解析真实父目录，再拼接剩余路径。
4. 对候选路径执行允许根前缀判断。
5. 返回 `VirtualPath` 或沙箱拒绝错误。
6. 支持读路径、写路径、glob 范围和命令 cwd。

**验证：** 运行 `mvn -Dtest=DefaultPathSandboxTest test`，预期工作区内路径通过，`..`、工作区外绝对路径、指向外部的 symlink、父目录逃逸的新文件均被拒绝。

## T8: 实现 Bash 明显路径扫描

**文件：** `src/main/java/com/lunacode/permission/BashPathScanner.java`、`DefaultPathSandbox.java`、`src/test/java/com/lunacode/permission/BashPathScannerTest.java`

**依赖：** T7

**步骤：**
1. 扫描 Windows 绝对路径、Unix 绝对路径、包含 `..` 的路径。
2. 扫描重定向目标，例如 `> ../out.txt`。
3. 扫描带路径分隔符的相对路径。
4. 对可识别路径片段调用 `DefaultPathSandbox`。
5. 对无法识别为路径的普通参数不做沙箱失败处理。

**验证：** 运行 `mvn -Dtest=BashPathScannerTest test`，预期 `cat /etc/passwd`、`python a.py ../x`、`echo hi > ../x` 被识别并拒绝，`git status --short` 不产生路径拒绝。

## T9: 实现 Bash 危险命令黑名单

**文件：** `src/main/java/com/lunacode/permission/DangerousCommandBlacklist.java`、`src/test/java/com/lunacode/permission/DangerousCommandBlacklistTest.java`

**依赖：** T1

**步骤：**
1. 定义内置正则列表。
2. 覆盖递归强制删除根目录、格式化磁盘、清空系统目录、关闭关键安全机制和 fork bomb。
3. 返回首个命中原因。
4. 确保黑名单不读取用户配置，也不能被 allow 规则关闭。

**验证：** 运行 `mvn -Dtest=DangerousCommandBlacklistTest test`，预期 `rm -rf /`、fork bomb 等命中，普通 `git status` 不命中。

## T10: 实现敏感路径策略

**文件：** `src/main/java/com/lunacode/permission/SensitivePathPolicy.java`、`src/test/java/com/lunacode/permission/DefaultPermissionEngineTest.java`

**依赖：** T6

**步骤：**
1. 定义敏感虚拟路径：`/project/.lunacode/config.yaml`。
2. 定义敏感虚拟路径：`/project/.lunacode/permissions.local.yaml`。
3. 定义敏感目录：`/project/.lunacode/skills/**`。
4. 暴露 `isSensitive(VirtualPath)`。

**验证：** 运行 `mvn -Dtest=DefaultPermissionEngineTest#sensitivePathsAskByDefault test`，预期敏感路径未命中规则时返回 ask。

## T11: 提取工具权限匹配目标

**文件：** `src/main/java/com/lunacode/permission/PermissionTarget.java`、`PermissionTargetKind.java`、`PermissionTargetExtractor.java`

**依赖：** T7、T8

**步骤：**
1. 为 Bash 提取命令文本、cwd 和可识别路径片段。
2. 为 ReadFile 提取读取路径虚拟路径。
3. 为 WriteFile/EditFile 提取写入或编辑路径虚拟路径。
4. 为 Glob 提取 glob 入参或搜索范围虚拟路径。
5. 为 Grep 提取搜索 pattern，并对 path 范围做沙箱校验。
6. 标记目标是否包含敏感路径。

**验证：** 运行 `mvn -Dtest=DefaultPermissionEngineTest#extractsToolTargets test`，预期各工具匹配对象符合 spec 定义。

## T12: 实现规则匹配优先级

**文件：** `src/main/java/com/lunacode/permission/PermissionRuleMatcher.java`、`src/test/java/com/lunacode/permission/PermissionRuleMatcherTest.java`

**依赖：** T4、T11

**步骤：**
1. 实现精确匹配和 glob 匹配。
2. 先收集三层所有 deny，任意命中直接 deny。
3. deny 展示原因按同层后定义优先。
4. 无 deny 时按本地级、项目级、用户级查 allow。
5. 同层多个 allow 命中时选择后定义规则。

**验证：** 运行 `mvn -Dtest=PermissionRuleMatcherTest test`，预期用户级 deny 不能被本地级 allow 覆盖，本地级 allow 能覆盖项目级和用户级 allow。

## T13: 实现权限模式矩阵

**文件：** `src/main/java/com/lunacode/permission/PermissionModePolicy.java`、`src/test/java/com/lunacode/permission/PermissionModePolicyTest.java`

**依赖：** T1、T11

**步骤：**
1. 实现 default 矩阵。
2. 实现 acceptEdits 矩阵。
3. 实现 plan 矩阵，和 default 一致。
4. 实现 bypassPermissions 矩阵。
5. 保留 AskUserQuestion 仅 AgentMode.PLAN 放行的规则。

**验证：** 运行 `mvn -Dtest=PermissionModePolicyTest test`，预期四种模式对 ReadFile、WriteFile、EditFile、Bash 的决策符合 plan 表格。

## T14: 实现统一权限引擎

**文件：** `src/main/java/com/lunacode/permission/DefaultPermissionEngine.java`、`PermissionEvaluationRequest.java`、`PermissionEvaluation.java`、`src/test/java/com/lunacode/permission/DefaultPermissionEngineTest.java`

**依赖：** T9、T11、T12、T13

**步骤：**
1. 按顺序执行黑名单、路径沙箱、deny、allow、敏感路径、权限模式。
2. 黑名单命中返回 `DENY/BLACKLIST`。
3. 沙箱失败返回 `DENY/SANDBOX`。
4. deny 命中返回 `DENY/RULE_DENY`。
5. allow 命中返回 `ALLOW/RULE_ALLOW`。
6. 敏感路径未命中规则返回 `ASK/SENSITIVE_PATH`。
7. 权限模式给出最终 allow 或 ask。
8. ask 结果中生成精确到本次参数的本地 allow 规则。

**验证：** 运行 `mvn -Dtest=DefaultPermissionEngineTest test`，预期覆盖黑名单不可绕过、沙箱不可绕过、deny 不可翻转、敏感路径 ask、模式默认矩阵。

## T15: 扩展权限确认交互

**文件：** `src/main/java/com/lunacode/interaction/PermissionConfirmationAnswer.java`、`PermissionConfirmationRequest.java`、`PermissionConfirmationBroker.java`、`BlockingPermissionConfirmationBroker.java`

**依赖：** T14

**步骤：**
1. 新增 `PermissionConfirmationAnswer`。
2. 扩展请求内容，包含权限模式、目标摘要、原因和可选动作。
3. 把 broker 返回值从 boolean 改为 answer。
4. 解析本次允许、始终允许、拒绝。
5. 兼容旧的 yes/y/允许 输入为本次允许。

**验证：** 运行 `mvn -Dtest=AgentToolRunnerPermissionTest#permissionAnswersAreParsed test`，预期三类输入映射正确。

## T16: 接入 AgentToolRunner 权限执行

**文件：** `src/main/java/com/lunacode/tool/ToolPermissionGateway.java`、`DefaultToolPermissionGateway.java`、`src/main/java/com/lunacode/agent/execution/AgentToolRunner.java`

**依赖：** T14、T15

**步骤：**
1. 将权限 gateway 返回详细 `PermissionEvaluation`。
2. `ALLOW` 时直接执行工具。
3. `ASK` 时发布权限请求事件并等待 answer。
4. `ALLOW_ONCE` 时执行工具。
5. `ALLOW_ALWAYS` 时先追加本地 allow 规则，成功后执行工具。
6. `DENY` 或追加失败时返回结构化工具错误。
7. 权限拒绝不抛出异常，不终止 Agent Loop。

**验证：** 运行 `mvn -Dtest=AgentToolRunnerPermissionTest test`，预期拒绝变成工具结果，始终允许会写本地规则后执行。

## T17: 增加权限事件可观察性

**文件：** `src/main/java/com/lunacode/agent/event/AgentEvent.java`、`src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`StatusSnapshot.java`、`src/main/java/com/lunacode/tui/LanternaLunaTui.java`

**依赖：** T16

**步骤：**
1. 增加权限允许、权限拒绝、规则加载 warning、权限模式切换事件。
2. Orchestrator 根据事件更新 status。
3. `StatusSnapshot` 增加当前权限模式字段。
4. TUI 在权限提示和状态输出中展示当前模式。
5. 避免权限提示泄露 API Key、Token 和敏感内容。

**验证：** 运行 `mvn -Dtest=LanternaLunaTuiTest,DefaultChatOrchestratorTest test`，预期状态渲染和既有 TUI 行为不回退。

## T18: 实现运行时权限模式会话

**文件：** `src/main/java/com/lunacode/permission/PermissionModeSession.java`、`src/main/java/com/lunacode/runtime/AgentRunConfig.java`

**依赖：** T2、T13

**步骤：**
1. `PermissionModeSession` 初始化为配置默认模式。
2. 普通请求返回当前会话模式。
3. AgentMode.PLAN 请求返回 PermissionMode.PLAN。
4. `AgentRunConfig` 增加 `permissionMode`。
5. 更新所有构造调用点和测试。

**验证：** 运行 `mvn -Dtest=DefaultAgentLoopTest,PermissionModePolicyTest test`，预期 AgentMode 和 PermissionMode 解耦且测试编译通过。

## T19: 实现 `/permissions` 命令

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`、`src/test/java/com/lunacode/orchestrator/PermissionCommandTest.java`

**依赖：** T18

**步骤：**
1. 在 `submitUserMessage` 中拦截 `/permissions`。
2. `/permissions` 显示当前模式，不进入模型历史。
3. `/permissions default`、`acceptEdits`、`plan` 立即切换当前会话模式。
4. `/permissions bypassPermissions` 触发二次确认。
5. 二次确认取消后保持原模式。
6. 非法模式显示中文错误。

**验证：** 运行 `mvn -Dtest=PermissionCommandTest test`，预期查看、切换、bypass 二次确认、非法模式均通过。

## T20: 实现 CommandSandbox 接口和 Direct 模式

**文件：** `src/main/java/com/lunacode/tool/CommandSandbox.java`、`DirectCommandSandbox.java`

**依赖：** T2、T6

**步骤：**
1. 定义 `wrapShellCommand` 接口。
2. Direct 模式复用当前 Windows `cmd.exe /d /c` 和非 Windows `/bin/sh -lc` 行为。
3. 为非 Linux 平台选择 Direct 模式。
4. 保持命令参数传递和超时读取逻辑不变。

**验证：** 运行 `mvn -Dtest=BashToolTest test`，预期现有非 Linux 命令构造行为不回退。

## T21: 实现 Linux Bubblewrap 命令沙箱

**文件：** `src/main/java/com/lunacode/tool/BubblewrapCommandSandbox.java`、`src/test/java/com/lunacode/tool/BubblewrapCommandSandboxTest.java`

**依赖：** T20

**步骤：**
1. 构造 `bwrap` 命令参数。
2. 绑定项目根和额外根目录。
3. 挂载运行 shell 所需的最小系统路径。
4. 默认加入 `--unshare-net`。
5. `sandbox.network_enabled: true` 时不加入 `--unshare-net`。
6. 加载 seccomp 规则文件或临时生成的 seccomp 配置。
7. bubblewrap 或 seccomp 不可用时返回结构化错误，不回退直接执行。

**验证：** 运行 `mvn -Dtest=BubblewrapCommandSandboxTest test`，预期命令参数包含 bwrap、默认断网、网络开启时去掉 `--unshare-net`，允许根目录被绑定。

## T22: 将 BashTool 接入 CommandSandbox

**文件：** `src/main/java/com/lunacode/tool/BashTool.java`、`ToolExecutionContext.java`、`src/main/java/com/lunacode/app/LunaCodeApplication.java`

**依赖：** T20、T21

**步骤：**
1. `ToolExecutionContext` 增加 `CommandSandbox`、`SandboxConfig` 和允许根目录列表。
2. `BashTool.shellCommand` 改为调用 `CommandSandbox`。
3. 保留 stdout/stderr 读取、超时、敏感值遮罩和结果截断。
4. `LunaCodeApplication` 根据 OS 和配置装配 CommandSandbox。

**验证：** 运行 `mvn -Dtest=BashToolTest,BubblewrapCommandSandboxTest test`，预期 BashTool 通过 CommandSandbox 执行，旧结果格式不变。

## T23: 装配权限系统

**文件：** `src/main/java/com/lunacode/app/LunaCodeApplication.java`、`src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`

**依赖：** T2、T14、T18、T22

**步骤：**
1. 应用启动时创建 `YamlPermissionRuleStore`。
2. 根据工作区和配置创建 `DefaultPathSandbox`。
3. 创建 `DangerousCommandBlacklist`、`SensitivePathPolicy`、`PermissionRuleMatcher`、`PermissionModePolicy`。
4. 创建 `DefaultPermissionEngine` 和 gateway 适配器。
5. 将 permission engine、rule store、mode session 注入 orchestrator 和 tool runner。
6. 确保测试构造器有默认权限组件，减少既有测试改动。

**验证：** 运行 `mvn -Dtest=DefaultChatOrchestratorTest,ToolOrchestratorTest test`，预期工具调用仍能执行，权限默认行为符合 default。

## T24: 更新既有测试和构造器

**文件：** 既有 `src/test/java/com/lunacode/**` 测试文件

**依赖：** T23

**步骤：**
1. 更新 `AgentRunConfig` 构造参数。
2. 更新 `ToolExecutionContext` 构造参数。
3. 更新 permission broker 返回类型断言。
4. 保留第一至第四阶段已有行为测试。

**验证：** 运行 `mvn test`，预期所有既有测试和新增测试通过。

## T25: 补齐权限引擎集成场景

**文件：** `src/test/java/com/lunacode/permission/DefaultPermissionEngineTest.java`、`src/test/java/com/lunacode/agent/execution/AgentToolRunnerPermissionTest.java`

**依赖：** T23

**步骤：**
1. 测试黑名单即使 allow 和 bypassPermissions 存在也拒绝。
2. 测试路径沙箱即使 bypassPermissions 存在也拒绝。
3. 测试用户级 deny 拒绝本地级 allow。
4. 测试敏感路径默认 ask。
5. 测试拒绝工具调用后返回结构化工具结果。
6. 测试始终允许精确到本次参数。

**验证：** 运行 `mvn -Dtest=DefaultPermissionEngineTest,AgentToolRunnerPermissionTest test`，预期所有权限集成场景通过。

## T26: 补齐 Linux OS 沙箱验收测试

**文件：** `src/test/java/com/lunacode/tool/BubblewrapCommandSandboxTest.java`、`src/test/java/com/lunacode/tool/BashToolTest.java`

**依赖：** T21、T22

**步骤：**
1. 测试默认命令参数包含 `--unshare-net`。
2. 测试 `sandbox.network_enabled: true` 时不包含 `--unshare-net`。
3. 测试 seccomp 参数存在。
4. 测试项目根和额外根目录 bind 参数存在。
5. 测试 bubblewrap 不可用时 BashTool 返回结构化错误。

**验证：** 运行 `mvn -Dtest=BubblewrapCommandSandboxTest,BashToolTest test`，预期 Linux 沙箱命令构造和失败路径符合 spec。

## T27: 全量编译和自动化测试

**文件：** 全项目

**依赖：** T1-T26

**步骤：**
1. 运行 `mvn test`。
2. 修复编译错误和失败测试。
3. 运行 `mvn package -DskipTests`。
4. 确认打包产物仍能生成。

**验证：** `mvn test` 和 `mvn package -DskipTests` 均成功。

## T28: 端到端手动验收准备

**文件：** `spec/05/checklist.md` 生成后使用、`run-LunaCode.bat` 或 Linux 等价启动命令

**依赖：** T27、后续 checklist.md 通过

**步骤：**
1. 根据 checklist.md 准备规则文件和配置文件。
2. 在 tmux 中启动 LunaCode。
3. 输入真实请求，触发 ReadFile 自动放行、WriteFile ask、Bash 黑名单拒绝、敏感路径 ask。
4. 在 Linux 环境中验证 Bash 默认断网；开启 `sandbox.network_enabled: true` 后再验证网络命令可访问网络。
5. 对照 checklist.md 逐项验收。

**验证：** checklist.md 中端到端场景全部通过，并记录实际观察结果。

## 执行顺序

```text
T1 -> T2
T1 -> T3 -> T4 -> T5
T2 -> T6 -> T7 -> T8
T1 -> T9
T6 -> T10
T7 + T8 + T10 -> T11
T3 + T4 + T11 -> T12
T1 -> T13
T9 + T11 + T12 + T13 -> T14
T14 -> T15 -> T16 -> T17
T2 + T13 -> T18 -> T19
T2 + T6 -> T20 -> T21 -> T22
T14 + T18 + T22 -> T23 -> T24 -> T25 -> T26 -> T27 -> T28
```
