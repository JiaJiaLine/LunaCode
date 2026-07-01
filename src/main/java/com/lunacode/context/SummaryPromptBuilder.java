package com.lunacode.context;

import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageRole;

import java.nio.file.Path;
import java.util.List;

public final class SummaryPromptBuilder {
    public String build(List<ConversationMessageSnapshot> messagesToSummarize, Path sessionLogPath) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                你正在为 LunaCode 生成上下文压缩摘要。

                严禁调用任何工具。你只能阅读下面提供的文本并输出摘要。
                先写 <analysis_draft> 草稿，再写 <final_summary> 正式摘要。
                草稿只用于帮助你组织信息；LunaCode 只会读取 <final_summary> 中的内容。

                正式摘要必须包含以下 9 个固定部分，标题原样保留：
                1. 主要请求和意图
                2. 关键技术概念
                3. 文件和代码段
                4. 错误和修复
                5. 问题解决过程
                6. 所有用户消息
                7. 待办任务
                8. 当前工作
                9. 可能的下一步

                “所有用户消息”部分必须逐条保留所有非工具结果的用户消息原文，不要改写、不要摘要、不要遮蔽。
                如果缺少具体代码、报错或工具结果细节，必须在摘要里说明需要读取完整会话记录或重新读取项目文件，不要脑补。

                完整会话记录路径：
                """);
        prompt.append(sessionLogPath == null ? "<尚未写出>" : sessionLogPath).append("\n\n");
        prompt.append("[待摘要消息]\n");
        int index = 1;
        for (ConversationMessageSnapshot message : messagesToSummarize) {
            prompt.append("\n--- message ").append(index++).append(" ---\n");
            prompt.append("id: ").append(message.id()).append('\n');
            prompt.append("role: ").append(message.role()).append('\n');
            prompt.append("status: ").append(message.status()).append('\n');
            prompt.append("contextSummary: ").append(message.metadata().contextSummary()).append('\n');
            prompt.append("content:\n").append(message.content()).append('\n');
            for (ContentBlock block : message.blocks()) {
                if (block instanceof ContentBlock.ToolUseBlock toolUse) {
                    prompt.append("[tool_use] ").append(toolUse.name()).append(" id=").append(toolUse.id())
                            .append(" input=").append(toolUse.input()).append('\n');
                } else if (block instanceof ContentBlock.ToolResultBlock toolResult) {
                    prompt.append("[tool_result] id=").append(toolResult.toolUseId())
                            .append(" error=").append(toolResult.isError()).append('\n')
                            .append(toolResult.content()).append('\n');
                }
            }
        }
        prompt.append("\n[所有用户消息原文]\n");
        for (ConversationMessageSnapshot message : messagesToSummarize) {
            if (message.role() == MessageRole.USER && !message.content().isBlank()) {
                prompt.append("- ").append(message.content()).append('\n');
            }
        }
        prompt.append("""

                请按以下格式输出：
                <analysis_draft>
                草稿写在这里。
                </analysis_draft>
                <final_summary>
                ## 主要请求和意图
                ...
                ## 关键技术概念
                ...
                ## 文件和代码段
                ...
                ## 错误和修复
                ...
                ## 问题解决过程
                ...
                ## 所有用户消息
                ...
                ## 待办任务
                ...
                ## 当前工作
                ...
                ## 可能的下一步
                ...
                </final_summary>
                """);
        return prompt.toString();
    }
}
