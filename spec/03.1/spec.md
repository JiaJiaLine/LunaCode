# DefaultAgentLoop 显式注入重构 Spec

## 背景

当前 `DefaultAgentLoop` 已经把工具执行、单轮模型调用、循环决策和 Prompt 上下文构建拆成独立协作对象，但构造函数内部仍直接创建这些对象。这样会让 Agent Loop 同时承担流程编排和依赖装配职责，也让后续继续推进 `tool / loop / prompt` 解耦时难以在测试中替换某个协作对象。

本次重构目标是先收窄 `DefaultAgentLoop` 的职责：它只负责编排一轮到多轮 Agent Loop，不再内部直接创建工具执行器、单轮执行器、循环决策器和 Prompt 构建器。默认装配逻辑迁移到现有装配层，保持现有运行行为不回退。

## 目标

- `DefaultAgentLoop` 的核心依赖由调用方显式传入。
- 现有默认运行路径仍能创建完整可用的 Agent Loop。
- 现有多轮对话、工具执行、未知工具停止、取消、迭代上限和 Prompt 构建行为保持一致。
- 测试可以用更直接的方式验证 `DefaultAgentLoop` 的依赖注入边界。

## 功能需求

- F1: `DefaultAgentLoop` 不在内部直接创建工具执行、单轮执行、循环决策和 Prompt 构建协作对象。
- F2: 默认装配入口负责创建这些协作对象，并把它们传入 `DefaultAgentLoop`。
- F3: 旧有外部调用路径在语义上保持可用，调用方无需知道内部重构细节即可启动 LunaCode。
- F4: 单元测试覆盖显式注入路径，能够证明 `DefaultAgentLoop` 使用传入的协作对象执行循环。
- F5: 现有行为测试继续覆盖多轮工具回灌和连续未知工具停止。

## 非功能需求

- N1: 不引入 Spring、Guice 等依赖注入框架，继续使用项目现有的手动装配风格。
- N2: 改动范围保持在 Agent Loop 装配边界和相关测试内，不重写工具执行、单轮流式收集、Prompt 构建或循环决策逻辑。
- N3: 代码风格保持当前 Java 17 和 Maven 测试体系。
- N4: 中文注释和用户可见文本风格不因本次重构改变。

## 不做的事情

- 不改工具权限策略、工具批处理策略或具体工具实现。
- 不改 Provider 协议、流式事件映射或 Prompt 内容结构。
- 不改变 `DefaultChatOrchestrator` 对外用户交互语义。
- 不引入全局依赖注入容器或新的模块系统。

## 验收标准

- AC1: `DefaultAgentLoop` 源码中不再出现对 `AgentToolRunner`、`AgentTurnRunner`、`LoopDecisionMaker`、`PromptContextBuilder` 的直接 `new`。
- AC2: 默认装配路径仍能创建并运行 Agent Loop，已有 orchestrator 测试不因装配重构失败。
- AC3: 多轮工具回灌测试仍能观察到第二轮模型调用、工具结果消息和循环完成事件。
- AC4: 连续未知工具测试仍能按配置阈值停止，并产生对应错误事件。
- AC5: 新增或更新的测试能直接构造显式注入版本的 `DefaultAgentLoop`。
- AC6: `mvn test` 通过。
- AC7: 按 checklist 指定的端到端场景启动 LunaCode，输入真实对话请求后仍能调用工具并生成回复。
