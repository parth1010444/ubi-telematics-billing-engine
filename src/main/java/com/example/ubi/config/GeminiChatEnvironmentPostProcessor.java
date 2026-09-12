package com.example.ubi.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Enables Spring AI Google GenAI chat when a Gemini API key is present and the
 * caller did not explicitly set {@code SPRING_AI_MODEL_CHAT}.
 *
 * <p>Keeps Phase A {@code /execute} bootable without Gemini: default
 * {@code spring.ai.model.chat=none}.
 */
public class GeminiChatEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE = "gemini-chat-auto-enable";
    static final String CHAT_PROPERTY = "spring.ai.model.chat";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String explicitChat = firstNonBlank(
                environment.getProperty("SPRING_AI_MODEL_CHAT"),
                environment.getProperty("spring.ai.model.chat")
        );
        if (explicitChat != null && !"none".equalsIgnoreCase(explicitChat)) {
            return;
        }
        if (explicitChat != null && environment.getSystemEnvironment().containsKey("SPRING_AI_MODEL_CHAT")) {
            return;
        }
        String apiKey = firstNonBlank(
                environment.getProperty("GEMINI_API_KEY"),
                environment.getProperty("SPRING_AI_GOOGLE_GENAI_API_KEY"),
                environment.getProperty("spring.ai.google.genai.api-key")
        );
        if (apiKey == null) {
            return;
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put(CHAT_PROPERTY, "google-genai");
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, properties));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
