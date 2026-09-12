package com.example.ubi.analytics.ask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ubi.analytics.AnalyticsTestSupport;
import com.example.ubi.analytics.ast.AnalyticsAst;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class ChatClientAnalyticsLlmClientTest {

    @Test
    void completeJsonDelegatesToChatClientEntity() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(call);

        AnalyticsAst ast = AnalyticsTestSupport.loadAst("/analytics/policies-avg-premium.json");
        PlannerOutput output = new PlannerOutput("ok", ast, null, null);
        when(call.entity(PlannerOutput.class)).thenReturn(output);

        PlannerOutput actual = new ChatClientAnalyticsLlmClient(chatClient)
                .completeJson("sys", "user", PlannerOutput.class);
        assertSame(output, actual);
        assertEquals("ok", actual.status());
    }

    @Test
    void completeTextDelegatesToChatClientContent() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn("Two policies averaged $120.");

        String text = new ChatClientAnalyticsLlmClient(chatClient).completeText("sys", "user");
        assertEquals("Two policies averaged $120.", text);
    }
}
