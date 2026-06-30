# LunaCode 权限系统 Plan

## 架构概览

本阶段把现有 `DefaultToolPermissionGateway` 升级为一套独立的权限子系统。新的权限子系统位于 `com.lunacode.permission` 包，负责危险 Bash 黑名单、路径沙箱、三层规则、权限模式、敏感路径和人在回路确认。工具执行层仍由 `AgentToolRunner` 统一调用，但它不再只拿到简单的 `ALLOW/ASK/DENY`，而是拿到包含拒绝层级、命中规则、提示文本和可持久化规则的详细决策结果。

权限系统的决策顺序固定为：工具存在性检查 -> Bash 危险黑名单 -> 路径沙箱 -> 三层 deny 合并硬拒绝 -> 三层 allow 优先级判断 -> 敏感路径默认 ask -> 权限模式默认矩阵 -> 人在回路。黑名单和路径沙箱是不可绕过的硬安全层；deny 跨层合并且不可被 allow 翻转；allow 只在没有 deny 命中时按本地级、项目级、用户级逐层判断。

路径沙箱从现有 `WorkspacePathResolver` 扩展为允许根目录模型。默认允许根是项目根，虚拟路径为 `/project/...`；配置的额外根目录按别名映射为 `/roots/{name}/...`。所有路径类规则都匹配虚拟路径，真实路径只用于沙箱校验和工具执行。路径校验必须先绝对化和规范化，再解析符号链接，最后做允许根前缀判断；不存在的新文件按最近存在父目录解析真实路径，再拼回剩余路径。

Linux 上的 Bash 执行会再进入 OS 级沙箱。`BashTool` 不再直接把命令交给 `/bin/sh -lc`，而是通过命令沙箱包装器构造最终进程命令；Linux 平台使用 bubblewrap 包裹 shell 命令，加载 seccomp 规则，并默认通过 `--unshare-net` 创建空网络命名空间。只有配置 `sandbox.network_enabled: true` 时才不隔离网络。非 Linux 平台保留当前直接执行行为，但仍走权限系统的黑名单、路径沙箱、规则和确认。

`AgentMode` 和 `PermissionMode` 分离。`AgentMode` 继续表达 Default/Plan 这类任务执行语境，影响 System Prompt 和 AskUserQuestion；`PermissionMode` 表达 default、acceptEdits、plan、bypassPermissions 的权限矩阵。`/plan` 请求的有效权限模式强制使用 plan/default 矩阵，避免规划阶段继承 acceptEdits 或 bypassPermissions；普通请求使用当前会话的运行时权限模式。

## 核心数据结构

### PermissionMode

```java
public enum PermissionMode {
    DEFAULT,
    ACCEPT_EDITS,
    PLAN,
    BYPASS_PERMISSIONS
}
```

`PermissionMode` 独立于 `AgentMode`。配置文件提供默认值，`/permissions ...` 在当前会话内覆盖默认值。

### PermissionEffect

```java
public enum PermissionEffect {
    ALLOW,
    DENY
}
```

只用于规则文件。人在回路仍返回本次允许、始终允许或拒绝。

### PermissionRuleLevel

```java
public enum PermissionRuleLevel {
    LOCAL,
    PROJECT,
    USER
}
```

对应 `.lunacode/permissions.local.yaml`、`.lunacode/permissions.yaml` 和 `~/.lunacode/permissions.yaml`。

### PermissionRule

```java
public record PermissionRule(
        String rawRule,
        String toolName,
        String pattern,
        PermissionEffect effect,
        PermissionRuleLevel level,
        int order
) {}
```

`rawRule` 保留原始 `Tool(pattern)` 文本，便于提示和持久化。`order` 是同一层级文件中的定义顺序，数值越大优先级越高。

### PermissionRuleMatch

```java
public record PermissionRuleMatch(
        PermissionRule rule,
        String matchedValue
) {}
```

用于解释为什么 allow 或 deny 命中。deny 命中时，如果同层多个 deny 同时命中，使用 `order` 最大的 deny 作为展示原因。

### PermissionConfig

```java
public record PermissionConfig(
        PermissionMode mode
) {}

public record SandboxConfig(
        boolean networkEnabled,
        List<SandboxRootConfig> extraRoots
) {}

public record SandboxRootConfig(
        String name,
        Path path
) {}
```

`PermissionConfig` 挂在主配置的 `permissions` 字段下，`mode` 默认 `DEFAULT`。`SandboxConfig` 挂在主配置的 `sandbox` 字段下，`networkEnabled` 默认 `false`，`extraRoots` 默认为空。

示例：

```yaml
permissions:
  mode: default
sandbox:
  network_enabled: false
  extra_roots:
    - name: cache
      path: "D:/shared/cache"
```

### SandboxRoot

```java
public record SandboxRoot(
        String name,
        Path realRoot,
        String virtualPrefix
) {}
```

项目根的 `name` 固定为 `project`，`virtualPrefix` 固定为 `/project`。额外根目录的 `virtualPrefix` 为 `/roots/{name}`。

### VirtualPath

```java
public record VirtualPath(
        Path realPath,
        String virtualPath,
        SandboxRoot root
) {}
```

权限规则匹配和权限提示使用 `virtualPath`，工具执行继续使用 `realPath`。

### PermissionTarget

```java
public record PermissionTarget(
        PermissionTargetKind kind,
        String matchValue,
        List<VirtualPath> paths,
        boolean sensitive
) {}
```

`matchValue` 是规则括号里要匹配的对象：Bash 为命令文本，文件工具为虚拟路径，Glob 为规范化后的 glob 或搜索范围，Grep 为搜索 pattern。

### PermissionEvaluationRequest

```java
public record PermissionEvaluationRequest(
        ToolUse toolUse,
        Tool tool,
        AgentMode agentMode,
        PermissionMode permissionMode,
        Path workspaceRoot
) {}
```

由 `AgentToolRunner` 创建并交给权限系统。

### PermissionEvaluation

```java
public record PermissionEvaluation(
        PermissionDecision decision,
        PermissionDecisionLayer layer,
        String reason,
        String prompt,
        Optional<PermissionRuleMatch> matchedRule,
        Optional<PermissionRule> exactAllowRule
) {}
```

`decision` 复用现有 `PermissionDecision` 的 `ALLOW/ASK/DENY`。`layer` 表示 `BLACKLIST`、`SANDBOX`、`RULE_DENY`、`RULE_ALLOW`、`SENSITIVE_PATH`、`MODE` 或 `USER`。`exactAllowRule` 仅在 ask 场景中生成，供用户选择“始终允许”时写入本地级规则。

### PermissionConfirmationAnswer

```java
public enum PermissionConfirmationAnswer {
    ALLOW_ONCE,
    ALLOW_ALWAYS,
    DENY
}
```

替代现有确认 broker 的 boolean 返回值。`ALLOW_ALWAYS` 必须成功写入 `.lunacode/permissions.local.yaml` 后才执行工具。

## 核心接口

### PermissionEngine

```java
public interface PermissionEngine {
    PermissionEvaluation evaluate(PermissionEvaluationRequest request);
}
```

`DefaultPermissionEngine` 编排黑名单、沙箱、规则、敏感路径和模式矩阵。

### PermissionRuleStore

```java
public interface PermissionRuleStore {
    LoadedPermissionRules load(Path workspaceRoot, Path userHome);
    void appendLocalAllow(Path workspaceRoot, PermissionRule rule);
}
```

规则加载失败时只忽略失败层，并记录 warning；其他层继续生效。本地级规则文件损坏时，`appendLocalAllow` 失败并提示用户先修复文件。

### PathSandbox

```java
public interface PathSandbox {
    VirtualPath resolvePath(String requestedPath, Path baseDir, PathIntent intent);
    List<VirtualPath> scanBashPaths(String command, Path cwd);
}
```

`resolvePath` 执行固定的真实路径校验流程。`scanBashPaths` 只识别明显路径片段，不承诺完整 shell 语义解析。

### DangerousCommandBlacklist

```java
public interface DangerousCommandBlacklist {
    Optional<String> firstMatch(String command);
}
```

内置正则不可配置放开。命中后直接 DENY，优先于规则和权限模式。

### CommandSandbox

```java
public interface CommandSandbox {
    List<String> wrapShellCommand(String command, ToolExecutionContext context, List<SandboxRoot> roots);
}
```

`BubblewrapCommandSandbox` 在 Linux 上返回 `bwrap ... /bin/sh -lc <command>`，并根据 `SandboxConfig.networkEnabled()` 决定是否加入 `--unshare-net`。`DirectCommandSandbox` 用于非 Linux 或显式不可用场景，保持当前 shell 执行方式。
### PermissionModeSession

```java
public final class PermissionModeSession {
    PermissionMode current();
    PermissionMode effectiveFor(AgentMode agentMode);
    PermissionModeChangeResult changeTo(PermissionMode target, boolean confirmed);
}
```

`effectiveFor(AgentMode.PLAN)` 返回 `PermissionMode.PLAN`；普通请求返回当前会话模式。切到 `BYPASS_PERMISSIONS` 时必须先完成二次确认。

## 模块设计

### 配置加载模块

**职责：** 扩展主配置，读取默认权限模式和额外根目录。

**对外接口：** `ProviderConfig.permissions()` 返回 `PermissionConfig`。

**依赖：** Jackson YAML、现有 `ConfigLoader`。

**设计细节：**

- `ConfigLoader.RawConfig` 增加 `RawPermissions permissions` 和 `RawSandbox sandbox`。
- `RawPermissions` 支持 `mode`。
- `RawSandbox` 支持 `network_enabled` 和 `extra_roots`。
- 未配置 `permissions.mode` 时使用 `default`。
- 未配置 `sandbox.network_enabled` 时使用 `false`，即 Linux Bash OS 级沙箱默认断网。
- 额外根目录 `name` 必须非空，只允许字母、数字、下划线、连字符，避免生成含路径分隔符的虚拟前缀。
- 额外根目录 `path` 可以是绝对路径，也可以相对当前工作区解析；最终进入沙箱前必须转为真实绝对路径。

### 规则加载模块

**职责：** 从三层 YAML 文件加载顶层列表规则，解析 `Tool(pattern)`，保留顺序和层级。

**对外接口：** `PermissionRuleStore.load(...)` 和 `appendLocalAllow(...)`。

**依赖：** Jackson YAML、`PermissionRuleParser`。

**文件位置：**

```text
用户级: ~/.lunacode/permissions.yaml
项目级: /project/.lunacode/permissions.yaml
本地级: /project/.lunacode/permissions.local.yaml
```

**规则格式：**

```yaml
# 禁止 force push
- rule: "Bash(git push --force*)"
  effect: deny

# 允许所有 git 命令
- rule: "Bash(git *)"
  effect: allow
```

**加载策略：**

- 文件不存在表示空规则集。
- 某一层 YAML 解析失败或字段非法时，只忽略该层，并生成 warning。
- 被忽略层不参与 allow 或 deny 判断。
- 顶层必须是列表，每项必须包含 `rule` 和 `effect`。
- `effect` 只接受 `allow` 和 `deny`。
- 同层规则按文件顺序记录，后定义规则的 `order` 更大。

**追加策略：**

- “始终允许”只写本地级 `.lunacode/permissions.local.yaml`。
- 追加前必须能成功解析现有本地级文件；解析失败则拒绝追加。
- 追加规则精确到本次参数，保持 `Tool(exact-value)`，不自动添加通配符。
- 追加到文件末尾，使其在本地 allow 中具备更高优先级。

### 规则匹配模块

**职责：** 根据工具名和匹配对象判断规则是否命中。

**对外接口：** `PermissionRuleMatcher.findDeny(...)`、`findAllow(...)`。

**依赖：** `PermissionTargetExtractor`、glob 转正则工具。

**匹配对象：**

| 工具 | 匹配对象 |
|------|----------|
| Bash | 命令文本 |
| ReadFile | 读取路径虚拟路径 |
| WriteFile | 写入路径虚拟路径 |
| EditFile | 编辑路径虚拟路径 |
| Glob | glob 入参或搜索范围虚拟路径 |
| Grep | 搜索 pattern |

**匹配顺序：**

1. 收集三层所有 deny，按层内后定义优先选择展示原因；任意 deny 命中即最终 DENY。
2. 没有 deny 命中时，查找本地级 allow；同层多个 allow 命中时选后定义。
3. 本地级未命中时查项目级 allow。
4. 项目级未命中时查用户级 allow。
5. 没有 allow 命中时进入敏感路径和权限模式判断。

### 路径沙箱模块

**职责：** 判断工具参数中的路径是否位于允许根目录内，并产出稳定虚拟路径。

**对外接口：** `PathSandbox.resolvePath(...)` 和 `scanBashPaths(...)`。

**依赖：** Java NIO `Path`、`Files`。

**允许根目录：**

- 项目根：`/project/...`
- 额外根：`/roots/{name}/...`

**校验流程：**

1. 将请求路径转换为绝对路径并 `normalize`。
2. 如果目标存在，对目标执行 `toRealPath()`，解析符号链接。
3. 如果目标不存在，向上查找最近存在父目录，对父目录执行 `toRealPath()`，再拼接剩余路径片段。
4. 将候选路径与所有允许根目录的真实路径做前缀判断。
5. 命中允许根后生成虚拟路径；未命中则返回沙箱拒绝。

**Bash 路径扫描：**

- cwd 必须通过路径沙箱。
- 命令文本中识别明显的 Windows 绝对路径、Unix 绝对路径、`..` 逃逸路径、重定向目标和带路径分隔符的相对路径。
- 可识别路径片段复用同一套沙箱流程。
- 本阶段不解析完整 shell AST，不处理所有变量展开或命令替换。

### Bash 黑名单模块

**职责：** 在规则和权限模式前硬拦截明显危险 Bash 命令。

**对外接口：** `DangerousCommandBlacklist.firstMatch(command)`。

**依赖：** `Pattern` 列表。

**默认覆盖：**

- 删除根目录或允许根外目录的递归强制删除模式。
- 格式化磁盘、清空系统目录、修改启动项、关闭关键安全机制。
- fork bomb 或等价资源炸弹。
- 其它明确高危命令片段。

黑名单只用于 Bash，不用于文件工具。

### Bash OS 级沙箱模块

**职责：** 在 Linux 上把 Bash 命令包裹进 bubblewrap + seccomp 沙箱，默认断网执行。

**对外接口：** `CommandSandbox.wrapShellCommand(...)`。

**依赖：** bubblewrap 可执行文件、seccomp 配置、`ToolExecutionContext`、`SandboxConfig`、允许根目录列表。

**设计细节：**

- `BashTool` 调用 `CommandSandbox` 构造最终 `ProcessBuilder` 命令。
- Linux 上使用 `BubblewrapCommandSandbox`，非 Linux 上使用 `DirectCommandSandbox`。
- `BubblewrapCommandSandbox` 绑定项目根目录和所有额外根目录，并保持它们在沙箱内的真实可执行路径可访问。
- 默认加入 `--unshare-net`，创建空网络命名空间。
- 当 `sandbox.network_enabled: true` 时，不加入 `--unshare-net`，允许命令访问网络。
- seccomp 规则作为内置资源或启动时生成的临时规则加载，用于限制明显危险系统调用。
- 如果 Linux 上找不到 bubblewrap 或 seccomp 初始化失败，Bash 命令返回结构化错误，不回退到无 OS 沙箱直接执行。
- OS 级沙箱只包裹命令执行，不替代权限系统；命令进入沙箱前仍必须通过黑名单、路径沙箱、规则和人在回路。
### 敏感路径模块

**职责：** 对敏感路径提供默认 ask 行为，并在提示和工具结果中避免泄露敏感值。

**对外接口：** `SensitivePathPolicy.isSensitive(VirtualPath)`。

**默认敏感路径：**

```text
/project/.lunacode/config.yaml
/project/.lunacode/permissions.local.yaml
/project/.lunacode/skills/**
```

**决策语义：**

- deny 命中时直接拒绝。
- allow 命中时放行。
- 未命中规则时触发 ask。

### 权限模式模块

**职责：** 在没有具体规则命中且不是敏感路径时给出默认矩阵。

**默认矩阵：**

| 权限模式 | ReadFile/Glob/Grep | WriteFile/EditFile | Bash | AskUserQuestion |
|----------|--------------------|--------------------|------|-----------------|
| default | allow | ask | ask | 仅 AgentMode.PLAN allow |
| acceptEdits | allow | allow | ask | 仅 AgentMode.PLAN allow |
| plan | allow | ask | ask | 仅 AgentMode.PLAN allow |
| bypassPermissions | allow | allow | allow | 仅 AgentMode.PLAN allow |

bypassPermissions 仍受 Bash 黑名单和路径沙箱限制。

### 人在回路模块

**职责：** 把 ask 决策交给用户，支持本次允许、始终允许、拒绝。

**对外接口：** `PermissionConfirmationBroker.confirm(...)` 返回 `PermissionConfirmationAnswer`。

**依赖：** TUI 输入、`PermissionRuleStore`。

**提示内容：**

- 工具名。
- 参数摘要。
- 命令摘要或虚拟目标路径。
- 当前权限模式。
- 未命中规则的原因。
- 可选动作：本次允许、始终允许、拒绝。

**输入解析：**

- `yes`、`y`、`允许`、`本次允许` -> `ALLOW_ONCE`
- `always`、`始终允许` -> `ALLOW_ALWAYS`
- 其它输入默认 `DENY`

`ALLOW_ALWAYS` 先追加本地 allow 规则，追加成功后执行工具；追加失败时不执行工具，并返回权限错误。

### 运行时权限命令模块

**职责：** 支持 `/permissions` 查看和切换会话权限模式。

**对外接口：** 在 `DefaultChatOrchestrator.submitUserMessage` 中拦截 slash command。

**命令：**

```text
/permissions
/permissions default
/permissions acceptEdits
/permissions plan
/permissions bypassPermissions
```

**行为：**

- `/permissions` 显示当前会话权限模式。
- 普通模式切换立即生效。
- 切换到 `bypassPermissions` 必须二次确认。
- 二次确认取消时保持原模式。
- slash command 不进入模型对话历史。

### Agent 集成模块

**职责：** 把新的权限结果接入工具执行和事件流。

**对外接口：** `AgentToolRunner` 使用新的权限 gateway。

**改动点：**

- `ToolPermissionGateway` 改为返回 `PermissionEvaluation` 或新增 `DetailedToolPermissionGateway`。
- `AgentToolRunner` 对 `ALLOW` 直接执行工具。
- `AgentToolRunner` 对 `ASK` 调用确认 broker。
- `AgentToolRunner` 对 `DENY` 直接生成结构化 `ToolResult.error`，不终止 Agent Loop。
- `ToolResult` metadata 包含 `errorType`、`permissionLayer`、`reason`、`matchedRule`。
- 新增或扩展 `AgentEvent`，发布权限允许、权限拒绝、权限模式切换、规则加载 warning。

## 模块交互

```text
用户输入
  -> DefaultChatOrchestrator
      -> /permissions 命令处理，或创建 AgentRequest
      -> AgentRunConfig 携带 AgentMode 和有效 PermissionMode
  -> DefaultAgentLoop
      -> AgentTurnRunner 调用模型
      -> StreamingTurnCollector 收集工具调用
      -> AgentToolRunner 执行工具前请求权限
          -> PermissionEngine
              -> DangerousCommandBlacklist
              -> PathSandbox
              -> PermissionRuleStore + PermissionRuleMatcher
              -> SensitivePathPolicy
              -> PermissionModePolicy
          -> PermissionConfirmationBroker（ASK 时）
          -> CommandSandbox 包裹 Bash（Linux: bubblewrap + seccomp，默认断网）
          -> ToolExecutor（ALLOW 或用户允许时）
      -> 工具结果回灌模型
```

权限拒绝不会抛出到 Agent Loop 外层，而是作为工具结果进入对话历史。模型收到权限拒绝后可以调整路径、换用只读工具或解释无法执行的原因。

## 文件组织

```text
src/main/java/com/lunacode/
├── config/
│   ├── ConfigLoader.java                 # 增加 permissions 配置解析
│   ├── PermissionConfig.java             # 新增权限模式配置
│   ├── SandboxConfig.java                # 新增 OS 沙箱和额外根目录配置
│   └── SandboxRootConfig.java            # 新增额外根目录配置
├── orchestrator/
│   └── DefaultChatOrchestrator.java      # 处理 /permissions，维护会话权限模式
├── runtime/
│   └── AgentRunConfig.java               # 增加 PermissionMode
├── permission/
│   ├── PermissionMode.java
│   ├── PermissionEffect.java
│   ├── PermissionRuleLevel.java
│   ├── PermissionRule.java
│   ├── PermissionRuleMatch.java
│   ├── LoadedPermissionRules.java
│   ├── PermissionRuleParser.java
│   ├── PermissionRuleMatcher.java
│   ├── PermissionRuleStore.java
│   ├── YamlPermissionRuleStore.java
│   ├── SandboxRoot.java
│   ├── VirtualPath.java
│   ├── PathSandbox.java
│   ├── DefaultPathSandbox.java
│   ├── BashPathScanner.java
│   ├── DangerousCommandBlacklist.java
│   ├── SensitivePathPolicy.java
│   ├── PermissionTargetExtractor.java
│   ├── PermissionEvaluationRequest.java
│   ├── PermissionEvaluation.java
│   ├── PermissionDecisionLayer.java
│   ├── PermissionModePolicy.java
│   └── DefaultPermissionEngine.java
├── interaction/
│   ├── PermissionConfirmationBroker.java  # 返回 PermissionConfirmationAnswer
│   ├── PermissionConfirmationRequest.java # 增加规则和选项信息
│   └── BlockingPermissionConfirmationBroker.java
└── tool/
    ├── ToolPermissionGateway.java         # 接入详细权限结果
    ├── DefaultToolPermissionGateway.java  # 替换为新引擎适配器或迁移到 permission 包
    ├── WorkspacePathResolver.java         # 逐步迁移到 DefaultPathSandbox
    ├── CommandSandbox.java               # 新增 Bash 命令沙箱接口
    ├── BubblewrapCommandSandbox.java     # 新增 Linux bubblewrap/seccomp 包装器
    └── DirectCommandSandbox.java         # 新增非 Linux 直接执行包装器

src/test/java/com/lunacode/
├── permission/
│   ├── DangerousCommandBlacklistTest.java
│   ├── DefaultPathSandboxTest.java
│   ├── PermissionRuleParserTest.java
│   ├── PermissionRuleMatcherTest.java
│   ├── YamlPermissionRuleStoreTest.java
│   ├── DefaultPermissionEngineTest.java
│   └── PermissionModePolicyTest.java
├── orchestrator/
│   └── PermissionCommandTest.java
├── tool/
│   └── BubblewrapCommandSandboxTest.java
└── agent/execution/
    └── AgentToolRunnerPermissionTest.java
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 权限模式与 AgentMode | 分离为 `PermissionMode` 和 `AgentMode` | `/plan` 的 Prompt 行为和权限矩阵不是同一个概念，分离后更清晰 |
| deny 规则 | 三层合并，不可翻转 | 用户级安全底线不会被项目或本地 allow 意外覆盖 |
| allow 规则 | 本地级、项目级、用户级逐层判断 | 符合个人覆盖项目、项目覆盖全局偏好的直觉 |
| 同层规则顺序 | 后定义优先 | 方便用户在文件末尾追加覆盖规则，和“始终允许”追加行为一致 |
| 规则格式 | YAML 顶层列表 | 保留注释和顺序，适合自动追加 |
| 路径规则匹配 | 使用 `/project/...` 和 `/roots/{name}/...` | 避免真实绝对路径进入可提交规则，跨机器稳定 |
| 路径安全 | 先解析符号链接，再做前缀判断 | 防止 symlink 和 `..` 逃逸 |
| Bash 路径校验 | 简单扫描明显路径，不做完整 shell AST | 提升安全性，同时避免把本阶段变成 shell 解析器项目 |
| Linux OS 级沙箱 | Bash 使用 bubblewrap + seccomp，默认 `--unshare-net` 断网 | 通过 namespace 和系统调用限制增加运行时隔离，默认安全姿态更稳 |
| 黑名单范围 | 只覆盖 Bash | 文件工具风险交给沙箱、敏感路径、规则和模式，边界更清楚 |
| 敏感路径默认行为 | ask | 保留配置诊断和维护能力，同时避免静默读取敏感文件 |
| 始终允许 | 精确到本次参数并写入本地级 | 最小权限，避免自动扩大信任范围 |
| 规则加载失败 | 只忽略失败层 | 保留其他有效层规则，避免单层错误导致权限系统整体失效 |
| bypassPermissions | 二次确认且仍受黑名单和沙箱限制 | 危险模式需要显式确认，硬安全边界不可绕过 |

