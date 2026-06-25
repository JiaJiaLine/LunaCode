# LunaCode

我正在构建一个终端 AI 编程助手（类似 Claude Code），项目名叫 LunaCode，使用 [Java] 实现。

## 语言
中文回答，中文注释。

## 测试

开发完功能后，用 tmux 做端到端测试：

1. 在 tmux 中启动 LunaCode
2. 输入一段真实的对话请求
3. 观察 LunaCode 是否正确调用工具、生成回复
4. 对照 checklist.md 逐项验收