# DefaultAgentLoop 显式注入重构 Tasks

## 文件清单

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 修改 | `src/main/java/com/lunacode/agent/DefaultAgentLoop.java` | 移除内部直接创建协作对象的构造路径，改为显式接收依赖 |
| 修改 | `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` | 在默认装配层创建协作对象并传入 `DefaultAgentLoop` |
| 修改 | `src/test/java/com/lunacode/agent/DefaultAgentLoopTest.java` | 迁移测试到显式注入构造方式，保持既有行为断言 |
| 视情况修改 | `src/test/java/com/lunacode/orchestrator/DefaultChatOrchestratorTest.java` | 如果装配调整导致编译需要，做最小测试适配 |
| 视情况修改 | `src/test/java/com/lunacode/orchestrator/ToolOrchestratorTest.java` | 如果装配调整导致编译需要，做最小测试适配 |
| 新增 | `spec/06/checklist.md` | 定义最终验收项和端到端场景 |

## T1: 调整 DefaultAgentLoop 构造边界

**文件：** `src/main/java/com/lunacode/agent/DefaultAgentLoop.java`

**依赖：** `spec/06/spec.md`、`spec/06/plan.md`

**步骤：**
1. 删除接收 `ChatProvider`、`ToolExecutor`、`ToolBatchPlanner`、`ToolPermissionGateway`、`PermissionConfirmationBroker` 并在内部创建协作对象的构造函数。
2. 新增或保留一个主构造函数，参数为 `ConversationManager`、`ProviderConfig`、`ToolRegistry`、`AgentToolRunner`、`AgentTurnRunner`、`LoopDecisionMaker`、`PromptContextBuilder`。
3. 对所有构造参数使用 `Objects.requireNonNull`，保持空依赖尽早失败。
4. 清理不再需要的 import。
5. 不修改 `run(...)` 主循环、工具回灌、未知工具计数和事件发射逻辑。

**验证：** 运行文本搜索，确认 `DefaultAgentLoop.java` 中不再存在 `new AgentToolRunner`、`new AgentTurnRunner`、`new LoopDecisionMaker`、`new PromptContextBuilder`。

## T2: 迁移默认装配到 DefaultChatOrchestrator

**文件：** `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`

**依赖：** T1

**步骤：**
1. 添加 `AgentToolRunner`、`AgentTurnRunner`、`LoopDecisionMaker`、`PromptContextBuilder` 所需 import。
2. 在 orchestrator 内部新增 `createAgentLoop(...)` 私有方法。
3. 在 `createAgentLoop(...)` 中创建 `AgentToolRunner`，继续传入现有 `safeRegistry`、`toolExecutor`、`new ToolBatchPlanner()`、`new DefaultToolPermissionGateway(workspaceRoot)` 和 `permissionBroker`。
4. 在 `createAgentLoop(...)` 中创建 `AgentTurnRunner`、`LoopDecisionMaker`、`PromptContextBuilder`。
5. 主构造函数用 `createAgentLoop(...)` 替代原来的 `new DefaultAgentLoop(...)` 调用。
6. 保持 public 和 package-private 构造函数签名不变。

**验证：** 运行 `mvn -Dtest=DefaultChatOrchestratorTest,ToolOrchestratorTest test`，期望 orchestrator 相关测试编译并通过。

## T3: 更新 DefaultAgentLoopTest 为显式注入

**文件：** `src/test/java/com/lunacode/agent/DefaultAgentLoopTest.java`

**依赖：** T1、T2

**步骤：**
1. 添加测试 helper，用受控 `ConversationManager`、`CapturingProvider`、`ToolRegistry`、`ToolExecutor`、`ToolBatchPlanner`、`ToolPermissionGateway` 组装真实协作对象。
2. 让多轮工具回灌测试通过 helper 创建 `DefaultAgentLoop`。
3. 让连续未知工具测试通过 helper 创建 `DefaultAgentLoop`。
4. 删除测试中对旧构造函数的直接调用。
5. 保持现有断言语义：第二轮模型调用、工具结果消息、循环完成事件、未知工具错误事件都仍被验证。

**验证：** 运行 `mvn -Dtest=DefaultAgentLoopTest test`，期望测试通过。

## T4: 编译并执行全量单元测试

**文件：** 全项目测试源码

**依赖：** T1、T2、T3

**步骤：**
1. 运行 `mvn test`。
2. 如果出现仅由构造签名变化引起的编译错误，按显式注入边界做最小适配。
3. 如果出现行为回归，先定位是否误改了 `DefaultAgentLoop.run(...)` 主循环，再修复。

**验证：** `mvn test` 退出码为 0。

## T5: 检查直接 new 与行为边界

**文件：** `src/main/java/com/lunacode/agent/DefaultAgentLoop.java`、`src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java`

**依赖：** T4

**步骤：**
1. 搜索 `DefaultAgentLoop.java`，确认四个协作对象不再在 loop 内部直接创建。
2. 搜索 `DefaultChatOrchestrator.java`，确认默认装配集中创建四个协作对象。
3. 查看 `git diff`，确认改动集中在计划文件、loop、orchestrator 和相关测试。
4. 确认没有修改工具权限、Prompt 内容、Provider 协议或具体工具实现。

**验证：** 搜索结果和 diff 与计划一致。

## T6: 按 checklist 执行端到端验收

**文件：** `spec/06/checklist.md`

**依赖：** T4、T5、已批准的 checklist

**步骤：**
1. 根据 checklist 中的端到端场景启动 LunaCode。
2. 输入真实对话请求，让模型读取项目文件并生成回复。
3. 观察是否仍能正常触发工具调用、展示工具状态并完成回复。
4. 对照 checklist 记录通过或失败证据。

**验证：** checklist 的端到端场景通过，或记录失败现象并修复后重跑。

## 执行顺序

```text
T1 -> T2 -> T3 -> T4 -> T5 -> T6
```
