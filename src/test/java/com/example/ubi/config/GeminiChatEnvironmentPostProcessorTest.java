package com.example.ubi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class GeminiChatEnvironmentPostProcessorTest {

    private final GeminiChatEnvironmentPostProcessor processor = new GeminiChatEnvironmentPostProcessor();

    @Test
    void enablesGoogleGenAiWhenGeminiKeyPresentAndChatIsNone() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ai.model.chat", "none");
        env.setProperty("GEMINI_API_KEY", "test-key");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("google-genai", env.getProperty("spring.ai.model.chat"));
    }

    @Test
    void leavesChatNoneWhenNoApiKey() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ai.model.chat", "none");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("none", env.getProperty("spring.ai.model.chat"));
    }

    @Test
    void respectsExplicitSpringAiModelChatEnv() {
        MockEnvironment withSys = new MockEnvironment() {
            @Override
            public Map<String, Object> getSystemEnvironment() {
                return Map.of("SPRING_AI_MODEL_CHAT", "none");
            }
        };
        withSys.setProperty("spring.ai.model.chat", "none");
        withSys.setProperty("SPRING_AI_MODEL_CHAT", "none");
        withSys.setProperty("GEMINI_API_KEY", "test-key");

        processor.postProcessEnvironment(withSys, new SpringApplication());
        assertEquals("none", withSys.getProperty("spring.ai.model.chat"));
    }

    @Test
    void doesNotOverrideAlreadyEnabledChat() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ai.model.chat", "google-genai");
        env.setProperty("GEMINI_API_KEY", "test-key");

        processor.postProcessEnvironment(env, new SpringApplication());
        assertEquals("google-genai", env.getProperty("spring.ai.model.chat"));
        assertNull(env.getPropertySources().get(GeminiChatEnvironmentPostProcessor.PROPERTY_SOURCE));
    }
}
