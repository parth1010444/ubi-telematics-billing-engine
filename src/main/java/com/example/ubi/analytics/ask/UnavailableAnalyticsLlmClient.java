package com.example.ubi.analytics.ask;

import com.example.ubi.exception.AnalyticsLlmUnavailableException;

/**
 * Used when Gemini / Spring AI chat is not configured so Phase A still boots.
 */
public class UnavailableAnalyticsLlmClient implements AnalyticsLlmClient {

    static final String MESSAGE =
            "Gemini is not configured. Set GEMINI_API_KEY (or SPRING_AI_GOOGLE_GENAI_API_KEY) "
                    + "and restart. See docs/analytics-phase-b.md.";

    @Override
    public <T> T completeJson(String systemPrompt, String userPrompt, Class<T> type) {
        throw new AnalyticsLlmUnavailableException(MESSAGE);
    }

    @Override
    public String completeText(String systemPrompt, String userPrompt) {
        throw new AnalyticsLlmUnavailableException(MESSAGE);
    }
}
