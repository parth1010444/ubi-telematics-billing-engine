package com.example.ubi.analytics.ask;

/**
 * Thin LLM port so planner/summarizer tests can mock completions without
 * standing up Gemini. Production adapter uses Spring AI {@code ChatClient}.
 */
public interface AnalyticsLlmClient {

    <T> T completeJson(String systemPrompt, String userPrompt, Class<T> type);

    String completeText(String systemPrompt, String userPrompt);
}
