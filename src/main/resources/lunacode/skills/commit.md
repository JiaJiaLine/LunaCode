---
name: commit
description: 分析 git diff 并生成规范的 commit
mode: inline
tools:
  - Bash
---

请分析当前仓库的 git diff，并生成一个清晰、规范、可直接使用的 commit message。

$ARGUMENTS

执行步骤：

1. 如果用户没有指定范围，使用 `git diff` 查看当前未提交变更。
2. 总结变更意图，区分功能、修复、重构、测试、文档等类别。
3. 生成一条简洁的标题，必要时补充正文。
4. 如果变更包含潜在风险、遗漏测试或不适合提交的内容，请在 commit message 之后单独提醒。

输出要求：

- 优先使用 Conventional Commits 风格，例如 `feat: ...`、`fix: ...`、`test: ...`。
- 不要直接执行 `git commit`，除非用户明确要求。
- 如果没有 diff，请说明没有可提交变更。
