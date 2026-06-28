package com.lunacode.prompt;

import org.junit.jupiter.api.Test;

import com.lunacode.runtime.AgentMode;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptBuilderTest {
    @Test
    void staticPromptContainsStableSectionsWithoutEnvironment() {
        String prompt = new SystemPromptBuilder().build(new SystemPromptConfig(Path.of("/work"), "TestOS", Instant.parse("2026-06-27T00:00:00Z"), AgentMode.DEFAULT, Path.of("/work/plan.md")));

        assertTrue(prompt.contains("# 角色设定"));
        assertTrue(prompt.contains("# 行为准则"));
        assertTrue(prompt.contains("# 工具使用指南"));
        assertTrue(prompt.contains("# 代码质量规范"));
        assertTrue(prompt.contains("# 安全边界"));
        assertTrue(prompt.contains("# 任务执行模式"));
        assertTrue(prompt.contains("# 输出风格"));
        assertFalse(prompt.contains("/work"));
        assertFalse(prompt.contains("TestOS"));
        assertFalse(prompt.contains("2026-06-27T00:00:00Z"));
    }

    @Test
    void staticPromptOrderIsStableAndContainsLunaStyleRules() {
        StaticSystemPrompt prompt = new StaticSystemPromptBuilder().build();
        String rendered = prompt.render();
        List<String> titles = prompt.sections().stream().map(PromptSection::title).toList();

        assertEquals(List.of("角色设定", "行为准则", "工具使用指南", "代码质量规范", "安全边界", "任务执行模式", "输出风格"), titles);
        assertEquals(rendered, new StaticSystemPromptBuilder().build().render());
        assertTrue(rendered.contains("朝日"));
        assertTrue(rendered.contains("技术准确性优先"));
        assertTrue(rendered.contains("不得直接引用已有作品台词"));
        assertTrue(rendered.contains("露骨、暧昧、成人化或过度恋爱化"));
        assertTrue(rendered.contains("优先使用专用工具"));
        assertTrue(rendered.contains("编辑文件前必须读过目标内容"));
        assertTrue(rendered.contains("不编造命令输出"));
    }
}
