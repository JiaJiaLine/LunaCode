# DefaultAgentLoop 显式注入重构 Checklist

> 每一项都通过运行命令、查看源码或观察实际行为来验证，聚焦系统行为是否保持稳定。

## 实现完整性

- [ ] `DefaultAgentLoop` 只接收已创建好的 `AgentToolRunner`、`AgentTurnRunner`、`LoopDecisionMaker` 和 `PromptContextBuilder`（验证：查看 `src/main/java/com/lunacode/agent/DefaultAgentLoop.java` 的构造函数参数）
- [ ] `DefaultAgentLoop` 内部不再直接创建四个协作对象（验证：搜索 `DefaultAgentLoop.java`，看不到 `new AgentToolRunner`、`new AgentTurnRunner`、`new LoopDecisionMaker`、`new PromptContextBuilder`）
- [ ] `DefaultAgentLoop.run(...)` 的主流程没有改变：仍按构建 Prompt、执行单轮、决策、执行工具、回灌工具结果的顺序运行（验证：查看 diff，确认主循环逻辑未被重写）
- [ ] `DefaultChatOrchestrator` 能创建默认协作对象并传入 `DefaultAgentLoop`（验证：查看 `src/main/java/com/lunacode/orchestrator/DefaultChatOrchestrator.java` 中的装配代码）
- [ ] 现有 public orchestrator 构造方式仍可编译使用（验证：orchestrator 相关测试编译通过）

## 集成

- [ ] 默认运行路径仍能从 orchestrator 创建可运行的 Agent Loop（验证：运行 `mvn -Dtest=DefaultChatOrchestratorTest,ToolOrchestratorTest test`，结果通过）
- [ ] `DefaultAgentLoopTest` 使用显式注入构造 loop（验证：查看测试 helper，不再调用旧的内部装配构造函数）
- [ ] 多轮工具回灌行为保持不变（验证：运行 `mvn -Dtest=DefaultAgentLoopTest test`，测试观察到第二轮模型调用、工具结果消息和 `LoopComplete` 事件）
- [ ] 连续未知工具停止行为保持不变（验证：运行 `mvn -Dtest=DefaultAgentLoopTest test`，测试观察到未知工具错误事件）
- [ ] 本次改动没有修改工具权限、工具批处理、Provider 协议、Prompt 内容结构或具体工具实现（验证：查看 `git diff`，改动范围只落在计划文件、loop、orchestrator 和相关测试）

## 编译与测试

- [ ] 项目全量单元测试通过（验证：运行 `mvn test`，退出码为 0）
- [ ] 搜索确认 `DefaultAgentLoop` 中没有内部 `new` 四个协作对象（验证：运行文本搜索命令，结果为空）
- [ ] 搜索确认四个协作对象的默认创建位置迁移到装配层（验证：运行文本搜索命令，结果位于 `DefaultChatOrchestrator`）
- [ ] 编译产物可以正常生成（验证：运行 `mvn package -DskipTests`，退出码为 0）

## 端到端场景

- [ ] 场景 1：在 tmux 中启动 LunaCode，输入真实请求“请读取当前项目的 pom.xml，并用中文概括这个项目的构建配置”，LunaCode 能调用读取文件工具并生成中文回复（验证：tmux 会话中观察到工具调用状态、文件读取结果进入后续回复、最终有中文总结）
- [ ] 场景 2：在同一个 tmux 会话中继续输入“再看一下 src/main/java/com/lunacode/agent/DefaultAgentLoop.java，说明它现在如何组装每轮请求”，LunaCode 能继续调用工具并基于当前代码回复（验证：观察到第二次工具调用和最终回复，没有因为显式注入重构导致循环中断）
- [ ] 场景 3：端到端运行结束后 LunaCode 仍保持可继续输入状态（验证：tmux 会话中程序未崩溃，提示符或输入区可继续接收下一条消息）
