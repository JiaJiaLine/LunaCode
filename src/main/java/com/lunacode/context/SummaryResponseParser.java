package com.lunacode.context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SummaryResponseParser {
    private static final Pattern FINAL = Pattern.compile("(?is)<final_summary>\\s*(.*?)\\s*</final_summary>");

    public String parseFinalSummary(String response) {
        String safe = response == null ? "" : response;
        Matcher matcher = FINAL.matcher(safe);
        if (matcher.find()) {
            return matcher.group(1).strip();
        }
        return safe.replaceAll("(?is)<analysis_draft>.*?</analysis_draft>", "").strip();
    }
}
