# Skill 系统 Checklist

> 每一项都通过运行代码、查看测试结果或观察 LunaCode 行为来验证，聚焦用户可见行为和系统集成结果。

## 实现完整性

- [ ] Skill 单文件格式可用：Markdown 文件开头的 YAML frontmatter 被解析，`name`、`description` 必填，正文作为 SOP prompt body 保留（验证：运行 `mvn -q -Dtest=FrontmatterSkillParserTest test`，合法单文件用例通过）。
- [ ] Skill 目录格式可用：包含 `SKILL.md` 的目录被识别为目录型 Skill，目录资源根被记录，其它附属文件不自动进入 prompt（验证：运行 `mvn -q -Dtest=FileSystemSkillSourceTest,FrontmatterSkillParserTest test`，目录型用例通过）。
- [ ] frontmatter 默认值正确：缺省 `mode` 按 `inline`，缺省 `context` 按 `full`，缺省 `tools` 为空列表，缺省 `model` 为空（验证：运行 `mvn -q -Dtest=FrontmatterSkillParserTest test`，默认值断言通过）。
- [ ] frontmatter 非法值被局部跳过：缺少 `name`、缺少 `description`、非法 `name`、非法 `mode`、非法 `context` 都产生诊断且不影响其他 Skill（验证：运行 `mvn -q -Dtest=FrontmatterSkillParserTest,DefaultSkillCatalogTest test`，坏文件用例通过）。
- [ ] 内置样板只包含 `commit` 和 `test`，不包含内置 `review` Skill（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest,SkillCommandRegistrarTest test`，内置清单和 `/review` 保留用例通过）。
- [ ] Skill catalog 能合并项目级、用户级和内置级来源，并生成摘要和诊断（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest test`，来源合并用例通过）。
- [ ] Skill 调用计划能重新解析 Skill、替换 `$ARGUMENTS`、生成工具策略和模型覆盖（验证：运行 `mvn -q -Dtest=SkillInvocationPlannerTest test`，调用计划用例通过）。
- [ ] Skill 动态命令能注册到 `/help`、命令解析和补全视图（验证：运行 `mvn -q -Dtest=SkillCommandRegistrarTest test`，帮助信息和补全用例通过）。
- [ ] `LoadSkill` 工具能按名称加载完整 SOP，并把加载结果限制在本轮调用作用域（验证：运行 `mvn -q -Dtest=LoadSkillToolTest,SkillAgentLoopTest test`，加载和清理用例通过）。
- [ ] fork 运行模块能创建隔离子对话，并只把简短总结回流到主历史（验证：运行 `mvn -q -Dtest=SkillForkContextBuilderTest,SkillOrchestratorTest test`，fork 用例通过）。

## 验收标准覆盖

- [ ] AC1：项目级、用户级、内置级存在同名有效 Skill 时使用项目级；删除项目级后用户级生效；删除用户级后内置级生效（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest test`，同名覆盖用例通过）。
- [ ] AC2：缺少必填字段、非法名称、非法模式或非法上下文的 Skill 被跳过并产生可观察警告，其他 Skill 仍可用（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest test`，坏 Skill 诊断和旁路用例通过）。
- [ ] AC3：Skill 名称与内置斜杠命令冲突时，该 Skill 被跳过并产生可观察警告，原内置命令仍可使用（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest,SkillCommandRegistrarTest test`，命令冲突用例通过）。
- [ ] AC4：`/help` 和命令补全显示有效 Skill 的名称与说明，首次解析失败且无缓存的 Skill 不出现（验证：运行 `mvn -q -Dtest=SkillCommandRegistrarTest,DefaultSkillCatalogTest test`，帮助和补全用例通过）。
- [ ] AC5：输入 `/commit 重点关注安全问题` 后，模型本轮实际接收的 prompt 中所有 `$ARGUMENTS` 都替换为 `重点关注安全问题`（验证：运行 `mvn -q -Dtest=SkillInvocationPlannerTest,SkillAgentLoopTest test`，带参数替换用例通过）。
- [ ] AC6：输入 `/commit` 且不带参数后，模型本轮实际接收的 prompt 中所有 `$ARGUMENTS` 都替换为空字符串（验证：运行 `mvn -q -Dtest=SkillInvocationPlannerTest,SkillOrchestratorTest test`，无参数调用用例通过）。
- [ ] AC7：普通自然语言请求中，模型只能先看到 Skill 名称和说明；调用 `LoadSkill` 后本轮可见完整 SOP；下一次请求不再看到该完整 SOP（验证：运行 `mvn -q -Dtest=SkillPromptRendererTest,LoadSkillToolTest,SkillAgentLoopTest test`，两阶段加载和清理用例通过）。
- [ ] AC8：设置 `tools` 的 Skill 执行期间只暴露白名单工具；调用结束后普通对话恢复原工具集合；`LoadSkill` 不被白名单隐藏（验证：运行 `mvn -q -Dtest=SkillInvocationPlannerTest,SkillAgentLoopTest test`，工具声明和执行过滤用例通过）。
- [ ] AC9：`tools` 引用不存在工具的 Skill 被视为无效，不注册命令，不影响其他 Skill 和普通工具（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest,SkillCommandRegistrarTest test`，不存在工具用例通过）。
- [ ] AC10：设置 `model` 的 Skill 调用期间使用指定模型，调用结束后后续请求恢复默认模型（验证：运行 `mvn -q -Dtest=SkillInvocationPlannerTest,SkillAgentLoopTest,SkillOrchestratorTest test`，模型覆盖用例通过）。
- [ ] AC11：`inline` Skill 与主对话共享历史，模型回复正常进入主历史（验证：运行 `mvn -q -Dtest=SkillAgentLoopTest,SkillOrchestratorTest test`，inline 历史用例通过）。
- [ ] AC12：`fork` Skill 前台阻塞运行并显示运行状态，完成后主历史只出现包含 Skill 名称、用户请求、关键结论、产物路径或后续建议的简短总结（验证：运行 `mvn -q -Dtest=SkillOrchestratorTest test`，fork busy 和总结用例通过）。
- [ ] AC13：`context: recent` 的 fork 调用只带入最近 5 条主对话消息；`context: none` 不带入主对话消息；未设置 `context` 按 `full` 处理（验证：运行 `mvn -q -Dtest=SkillForkContextBuilderTest test`，三种上下文策略用例通过）。
- [ ] AC14：修改有效 Skill 文件后，下次执行该 Skill 使用新 prompt，无需重启 LunaCode（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest,SkillOrchestratorTest test`，热更新用例通过）。
- [ ] AC15：已有缓存版本的 Skill 在热更新解析失败时，下一次执行回退到上一次成功解析版本并给出警告（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest test`，缓存回退用例通过）。
- [ ] AC16：目录型 Skill 执行时只加载 `SKILL.md` 的 SOP；附属示例、模板、脚本和参考文档不会自动进入上下文，只在 Agent 按 SOP 使用工具读取或执行时消耗 token（验证：运行 `mvn -q -Dtest=SkillPromptRendererTest,SkillOrchestratorTest test`，目录资源按需加载用例通过）。
- [ ] AC17：内置 `/commit` 和 `/test` 可作为 Skill 调用；现有 `/review` 仍按原内置斜杠命令工作（验证：运行 `mvn -q -Dtest=SkillCommandRegistrarTest,SkillOrchestratorTest test`，三个命令行为用例通过）。
- [ ] AC18：用户可见能力中不出现市场分发、版本管理、自定义工具注册、后台 fork 和复杂模板表达式（验证：运行 `mvn -q -Dtest=SkillCommandRegistrarTest,LoadSkillToolTest test`，帮助信息、工具 schema 和内置 Skill 内容不暴露这些能力）。

## 集成行为

- [ ] Prompt 构建每轮注入 Skill 摘要，但不默认注入完整 SOP（验证：运行 `mvn -q -Dtest=SkillPromptRendererTest test`，摘要和完整 SOP 分离用例通过）。
- [ ] Anthropic provider 将 Skill 上下文放入 system blocks，OpenAI provider 将 Skill 上下文放入 developer messages（验证：运行 `mvn -q -Dtest=SkillPromptRendererTest test`，两个 provider 输出断言通过）。
- [ ] `LoadSkill` 工具结果在 Agent loop 结束后被替换为短标记，主历史不长期保存完整 SOP（验证：运行 `mvn -q -Dtest=SkillAgentLoopTest test`，工具结果清理用例通过）。
- [ ] 工具白名单同时作用于模型可见声明和实际工具执行（验证：运行 `mvn -q -Dtest=SkillAgentLoopTest test`，声明过滤和执行拒绝用例通过）。
- [ ] 用户级目录型 Skill 的资源根可被受控路径策略识别，附属脚本执行仍经过现有权限路径（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest,SkillOrchestratorTest test`，资源根和 Bash 权限用例通过）。
- [ ] fork `full` 上下文生成摘要失败时，调用以可读错误失败，不把完整主历史原文塞入子对话（验证：运行 `mvn -q -Dtest=SkillForkContextBuilderTest,SkillOrchestratorTest test`，摘要失败用例通过）。
- [ ] 调用级工具白名单、模型覆盖和完整 SOP 上下文在 Skill 调用结束后被丢弃（验证：运行 `mvn -q -Dtest=SkillAgentLoopTest,SkillOrchestratorTest test`，后续普通对话恢复用例通过）。
- [ ] 解析失败、命令冲突、工具缺失和缓存回退诊断能被用户观察到（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest,SkillOrchestratorTest test`，诊断输出用例通过）。

## 编译与测试

- [ ] Skill 解析测试通过（验证：运行 `mvn -q -Dtest=FrontmatterSkillParserTest,FileSystemSkillSourceTest test`，测试通过）。
- [ ] Skill catalog 和调用计划测试通过（验证：运行 `mvn -q -Dtest=DefaultSkillCatalogTest,SkillInvocationPlannerTest test`，测试通过）。
- [ ] Skill 命令、prompt 和工具测试通过（验证：运行 `mvn -q -Dtest=SkillCommandRegistrarTest,SkillPromptRendererTest,LoadSkillToolTest test`，测试通过）。
- [ ] Agent、fork 和 orchestrator 测试通过（验证：运行 `mvn -q -Dtest=SkillAgentLoopTest,SkillForkContextBuilderTest,SkillOrchestratorTest test`，测试通过）。
- [ ] Skill 相关局部测试组合通过（验证：运行 `mvn -q -Dtest=FrontmatterSkillParserTest,FileSystemSkillSourceTest,DefaultSkillCatalogTest,SkillInvocationPlannerTest,LoadSkillToolTest,SkillCommandRegistrarTest,SkillPromptRendererTest,SkillAgentLoopTest,SkillForkContextBuilderTest,SkillOrchestratorTest test`，测试通过）。
- [ ] 项目全量测试通过（验证：运行 `mvn test`，测试通过）。
- [ ] 代码格式和空白检查通过（验证：运行 `git diff --check`，没有输出错误）。

## 端到端场景

- [ ] E2E 1：启动 LunaCode 后输入 `/help`，能看到 `/commit` 和 `/test` 的 Skill 说明，能看到现有 `/review`，坏 Skill 不在列表中（验证：在 tmux 中启动 LunaCode，输入 `/help`，观察命令列表）。
- [ ] E2E 2：输入 `/commit 重点关注安全问题`，LunaCode 按 commit Skill 执行，模型看到替换后的参数，主历史保留用户可见调用和模型回复（验证：在 tmux 中执行命令，观察回复围绕 git diff 和安全关注点）。
- [ ] E2E 3：输入 `/commit`，LunaCode 仍执行 commit Skill，不因缺少参数报错（验证：在 tmux 中执行命令，观察 `$ARGUMENTS` 为空时的兜底逻辑生效）。
- [ ] E2E 4：用自然语言请求一次适合 `test` Skill 的任务，模型先看到 Skill 摘要，再按需调用 `LoadSkill`，完成后下一轮普通对话不再受完整 SOP 影响（验证：在 tmux 中观察工具调用日志和下一轮回复内容）。
- [ ] E2E 5：创建项目级 `.lunacode/skills/commit.md` 覆盖内置 commit，执行 `/commit` 使用项目级版本；删除项目级版本后用户级或内置版本按优先级恢复（验证：在 tmux 会话中修改文件并重复调用）。
- [ ] E2E 6：把一个已有缓存的 Skill 改成非法 frontmatter 后再次调用，LunaCode 给出警告并回退到上一次成功解析版本（验证：在 tmux 中修改文件、调用命令、观察警告和输出）。
- [ ] E2E 7：创建目录型 Skill，`SKILL.md` 引用 `examples/` 或 `scripts/`，调用时附属文件不自动进入上下文，Agent 只有在 SOP 指引下才读取或执行附属资源（验证：在 tmux 中观察 prompt 摘要、工具调用和资源读取时机）。
- [ ] E2E 8：执行一个 `fork` 模式 Skill，运行期间前台阻塞并显示运行状态；完成后主历史只出现简短总结，没有混入完整子对话过程（验证：在 tmux 中触发 fork Skill，观察 busy 状态和主历史内容）。
- [ ] E2E 9：执行设置 `tools` 白名单的 Skill，白名单外工具不可见且不可执行；随后普通对话恢复完整工具集合（验证：在 tmux 中触发该 Skill，再发普通工具请求，观察工具集合恢复）。
- [ ] E2E 10：执行设置 `model` 的 Skill，本次调用显示或使用指定模型；随后普通对话恢复默认模型（验证：在 tmux 中触发该 Skill，再发普通请求，观察模型状态或调用记录）。

