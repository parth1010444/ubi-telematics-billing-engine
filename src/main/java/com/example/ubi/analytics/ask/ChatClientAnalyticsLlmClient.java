package com.example.ubi.analytics.ask;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Spring AI {@link ChatClient} adapter. Structured planner calls use
 * {@code entity(Class)} (JSON schema in the prompt).
 */
public class ChatClientAnalyticsLlmClient implements AnalyticsLlmClient {

    private final ChatClient chatClient;

    public ChatClientAnalyticsLlmClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public <T> T completeJson(String systemPrompt, String userPrompt, Class<T> type) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(type);
    }

    @Override
    public String completeText(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }
}
