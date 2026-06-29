# DefaultAgentLoop 显式注入重构 Plan

## 架构概览

本次重构把 `DefaultAgentLoop` 调整为纯流程编排对象。它持有对话管理器、Provider 配置、工具注册表，以及四个已经拆分出来的协作对象：工具执行器、单轮执行器、循环决策器和 Prompt 上下文构建器。`DefaultAgentLoop` 不再知道这些协作对象如何创建。

默认运行时装配仍放在现有 orchestrator 路径中完成。`DefaultChatOrchestrator` 在创建自身时，继续创建权限确认 broker、工具批处理器、权限网关等运行时对象，然后组装 `AgentToolRunner`、`AgentTurnRunner`、`LoopDecisionMaker`、`PromptContextBuilder`，最后传给 `DefaultAgentLoop`。对 TUI 和应用入口而言，启动语义不变。

测试层跟随这个边界调整：`DefaultAgentLoopTest` 直接使用显式注入构造函数，并复用真实协作对象和受控 fake provider / executor 来验证既有行为；orchestrator 测试继续覆盖默认装配路径没有断裂。

## 核心数据结构

### DefaultAgentLoop 构造参数

```java
DefaultAgentLoop(
    ConversationManager conversationManager,
    ProviderConfig providerConfig,
    ToolRegistry toolRegistry,
    AgentToolRunner toolRunner,
    AgentTurnRunner turnRunner,
    LoopDecisionMaker decisionMaker,
    PromptContextBuilder promptContextBuilder
)
```

用途：
- `conversationManager`: 维护用户消息、助手消息和工具结果消息。
- `providerConfig`: 每轮模型调用使用的 Provider 配置。
- `toolRegistry`: 生成工具声明，并辅助工具结果回灌。
- `toolRunner`: 执行模型返回的工具调用批次。
- `turnRunner`: 执行单轮模型调用并收集流式结果。
- `decisionMaker`: 根据当前上下文和单轮结果决定停止或继续。
- `promptContextBuilder`: 每轮构建结构化 Prompt 上下文。

### 默认装配方法

```java
private AgentLoop createAgentLoop(
    ConversationManager conversationManager,
    ChatProvider provider,
    ProviderConfig config,
    ToolRegistry toolRegistry,
    ToolExecutor toolExecutor
)
```

用途：
- 集中创建 `AgentToolRunner`、`AgentTurnRunner`、`LoopDecisionMaker` 和 `PromptContextBuilder`。
- 复用 `DefaultChatOrchestrator` 已持有的 `permissionBroker`、`workspaceRoot` 等运行时上下文。
- 让 orchestrator 构造函数保持可读，避免在主构造函数中堆叠过多细节。

## 模块设计

### DefaultAgentLoop

**职责：** 编排 Agent Loop 主流程：加入用户消息、构建 Prompt、执行单轮模型调用、做循环决策、执行工具、回灌工具结果。

**对外接口：** 保留 `AgentLoop.run(...)`；构造函数改为接收已创建好的协作对象。

**依赖：** 只依赖抽象流程所需对象，不再依赖 `ChatProvider`、`ToolExecutor`、`ToolBatchPlanner`、`ToolPermissionGateway`、`PermissionConfirmationBroker` 等装配细节。

### DefaultChatOrchestrator

**职责：** 保持用户输入编排、后台任务、状态更新和默认 Agent Loop 装配。

**对外接口：** 现有 public 构造函数保持语义不变。

**依赖：** 在内部创建默认协作对象并传给 `DefaultAgentLoop`。如果测试需要更细粒度控制，仍通过现有 package-private 构造函数传入 provider、registry、executor 和 executor service。

### DefaultAgentLoopTest

**职责：** 验证显式注入后的 loop 行为不变。

**对外接口：** 测试内新增一个小型 helper 组装显式注入版本，减少重复构造代码。

**依赖：** 使用真实 `AgentToolRunner`、`AgentTurnRunner`、`LoopDecisionMaker`、`PromptContextBuilder`，并通过 fake provider / fake executor 控制行为。

### Orchestrator 测试

**职责：** 继续验证默认 orchestrator 构造和运行路径。

**对外接口：** 原测试尽量不改；只有编译需要时调整 import 或构造方式。

**依赖：** 使用现有 direct executor service 和 fake provider。

## 模块交互

```text
DefaultChatOrchestrator
  -> createAgentLoop(...)
      -> new AgentToolRunner(...)
      -> new AgentTurnRunner(...)
      -> new LoopDecisionMaker()
      -> new PromptContextBuilder()
      -> new DefaultAgentLoop(...)

DefaultAgentLoop.run(...)
  -> PromptContextBuilder.build(...)
  -> AgentTurnRunner.runTurn(...)
  -> LoopDecisionMaker.decide(...)
  -> AgentToolRunner.executeToolBatches(...)
  -> ConversationManager.addUserToolResultMessage(...)
```

交互重点：
- 组件创建只发生在装配层。
- `DefaultAgentLoop.run(...)` 的运行顺序、事件发射和停止条件保持原样。
- 工具权限 broker 仍由 orchestrator 创建并注入到 `AgentToolRunner`。

## 文件组织

```text
F:\LunaCode
├─ src/main/java/com/lunacode/agent/DefaultAgentLoop.java
├─ src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java
├─ src/test/java/com/lunacode/agent/DefaultAgentLoopTest.java
├─ src/test/java/com/lunacode/orchestrator/DefaultChatOrchestratorTest.java
├─ src/test/java/com/lunacode/orchestrator/ToolOrchestratorTest.java
└─ spec/06/
   ├─ spec.md
   ├─ plan.md
   ├─ task.md
   └─ checklist.md
```

## 技术决策

| 决策点 | 选择 | 理由 |
| --- | --- | --- |
| 注入方式 | 手动构造函数注入 | 符合现有工程风格，不引入新框架 |
| 默认装配位置 | `DefaultChatOrchestrator` 内部 helper | 当前 orchestrator 已承担 TUI 到 Agent 的运行时装配职责，改动集中 |
| `DefaultAgentLoop` 兼容构造 | 不保留内部创建协作对象的构造路径 | 保证 loop 源码完全不直接 new 四个协作对象 |
| 测试策略 | 行为测试迁移到显式注入 helper | 同时验证新边界和旧行为 |
| 协作对象抽象 | 暂不新增接口 | 本次只移动创建责任，不扩大抽象面；后续需要替换实现时再按真实需求抽接口 |
| 行为保持 | 不改 run 循环主体逻辑 | 降低回归风险，让本次重构只改变装配边界 |
