package com.lunacode.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SlashCommandParserTest {
    private final SlashCommandParser parser = new SlashCommandParser();

    @Test
    void ignoresBlankAndNormalInput() {
        assertInstanceOf(SlashCommandParseResult.NotCommand.class, parser.parse(""));
        assertInstanceOf(SlashCommandParseResult.NotCommand.class, parser.parse("   "));
        assertInstanceOf(SlashCommandParseResult.NotCommand.class, parser.parse("hello /help"));
    }

    @Test
    void parsesNameCaseInsensitivelyAndKeepsArgs() {
        SlashCommandParseResult.Command result = (SlashCommandParseResult.Command) parser.parse("/HELP test");

        assertEquals("/HELP", result.invocation().rawName());
        assertEquals("/help", result.invocation().normalizedName());
        assertEquals("test", result.invocation().args());
    }

    @Test
    void trimsOnlyOuterArgumentWhitespace() {
        SlashCommandParseResult.Command result = (SlashCommandParseResult.Command) parser.parse("  /review   并重点看异常处理  ");

        assertEquals("/review", result.invocation().normalizedName());
        assertEquals("并重点看异常处理", result.invocation().args());
    }

    @Test
    void commandWithoutArgsHasEmptyArgs() {
        SlashCommandParseResult.Command result = (SlashCommandParseResult.Command) parser.parse("/status");

        assertEquals("/status", result.invocation().normalizedName());
        assertEquals("", result.invocation().args());
    }
}
