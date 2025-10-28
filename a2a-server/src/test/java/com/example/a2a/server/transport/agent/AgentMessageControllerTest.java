package com.example.a2a.server.transport.agent;

import com.example.a2a.server.core.AgentSessionService;
import com.example.a2a.server.core.AuthorizationService;
import com.example.a2a.server.core.ConversationContextService;
import com.example.a2a.server.core.StreamingTaskService;
import com.example.a2a.server.core.ConversationContextService.ConversationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ConversationContextService conversationContextService;

    @Autowired
    private StreamingTaskService streamingTaskService;

    private String agentSessionId;

    @BeforeEach
    void setUp() throws Exception {
        String initializePayload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"init\"," +
                "\"method\":\"initialize\"" +
                "}";
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/agent/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initializePayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        agentSessionId = json.path("result").path("agentSessionId").asText();
        assertThat(agentSessionId).isNotBlank();
    }

    @Test
    void notificationsInitializedMarksSession() throws Exception {
        String payload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"init-ack\"," +
                "\"method\":\"notifications/initialized\"" +
                "}";

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/agent/message")
                        .header("agent-session-id", agentSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.path("result").path("status").asText()).isEqualTo("ok");
        assertThat(agentSessionService.isInitialized(agentSessionId)).isTrue();
    }

    @Test
    void messageStreamProducesSseEvents() throws Exception {
        String payload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"stream-1\"," +
                "\"method\":\"message/stream\"," +
                "\"params\":{" +
                "\"id\":\"task-" + UUID.randomUUID() + "\"," +
                "\"sessionId\":\"conversation-1\"," +
                "\"message\":{" +
                "\"role\":\"user\"," +
                "\"parts\":[{" +
                "\"kind\":\"text\",\"text\":\"weather in London\"" +
                "}]}}}";

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/agent/message")
                        .header("agent-session-id", agentSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("artifact-update");
        assertThat(body).contains("\"final\":true");
        assertThat(body).contains("Weather in London");

        ConversationContext context = conversationContextService.getContext(agentSessionId, "conversation-1");
        assertThat(context).isNotNull();
        assertThat(context.messages).isNotEmpty();
    }

    @Test
    void tasksCancelStopsActiveStream() throws Exception {
        String taskId = "task-cancel";
        String streamPayload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"stream-2\"," +
                "\"method\":\"message/stream\"," +
                "\"params\":{" +
                "\"id\":\"" + taskId + "\"," +
                "\"sessionId\":\"conversation-2\"," +
                "\"message\":{" +
                "\"role\":\"user\"," +
                "\"parts\":[{" +
                "\"kind\":\"text\",\"text\":\"weather in Shanghai\"" +
                "}]}}}";

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/agent/message")
                        .header("agent-session-id", agentSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(streamPayload))
                .andExpect(request().asyncStarted())
                .andReturn();

        String cancelPayload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"cancel-1\"," +
                "\"method\":\"tasks/cancel\"," +
                "\"params\":{\"id\":\"" + taskId + "\"}}";

        MvcResult cancelResult = mockMvc.perform(MockMvcRequestBuilders.post("/agent/message")
                        .header("agent-session-id", agentSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode cancelJson = objectMapper.readTree(cancelResult.getResponse().getContentAsString());
        assertThat(cancelJson.path("result").path("status").path("state").asText()).isEqualTo("canceled");

        MvcResult streamResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn();

        String body = streamResult.getResponse().getContentAsString();
        assertThat(body).contains("\"state\":\"canceled\"");
        assertThat(streamingTaskService.hasActiveTask(taskId)).isFalse();
    }

    @Test
    void clearContextRemovesConversationHistory() throws Exception {
        String taskId = "task-clear";
        String streamPayload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"stream-clear\"," +
                "\"method\":\"message/stream\"," +
                "\"params\":{" +
                "\"id\":\"" + taskId + "\"," +
                "\"sessionId\":\"conversation-3\"," +
                "\"message\":{" +
                "\"role\":\"user\"," +
                "\"parts\":[{" +
                "\"kind\":\"text\",\"text\":\"weather in Beijing\"" +
                "}]}}}";

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/agent/message")
                        .header("agent-session-id", agentSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(streamPayload))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

        ConversationContext context = conversationContextService.getContext(agentSessionId, "conversation-3");
        assertThat(context).isNotNull();

        String clearPayload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"clear-1\"," +
                "\"method\":\"clearContext\"," +
                "\"params\":{\"sessionId\":\"conversation-3\"}}";

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/agent/message")
                        .header("agent-session-id", agentSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearPayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.path("result").path("status").path("state").asText()).isEqualTo("cleared");
        assertThat(conversationContextService.getContext(agentSessionId, "conversation-3")).isNull();
    }

    @Test
    void authorizeAndDeauthorizeLifecycle() throws Exception {
        String authorizePayload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"auth-1\"," +
                "\"method\":\"authorize\"," +
                "\"params\":{" +
                "\"message\":{" +
                "\"role\":\"user\"," +
                "\"parts\":[{" +
                "\"kind\":\"data\",\"data\":{\"authCode\":\"CODE-123\"}}]}}}";

        MvcResult authResult = mockMvc.perform(MockMvcRequestBuilders.post("/agent/message")
                        .header("agent-session-id", agentSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizePayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode authJson = objectMapper.readTree(authResult.getResponse().getContentAsString());
        String loginSessionId = authJson.path("result").path("agentLoginSessionId").asText();
        assertThat(loginSessionId).isNotBlank();
        assertThat(authorizationService.isActive(loginSessionId)).isTrue();

        String deauthorizePayload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"deauth-1\"," +
                "\"method\":\"deauthorize\"," +
                "\"params\":{" +
                "\"message\":{" +
                "\"role\":\"user\"," +
                "\"parts\":[{" +
                "\"kind\":\"data\",\"data\":{\"agentLoginSessionId\":\"" + loginSessionId + "\"}}]}}}";

        MvcResult deauthResult = mockMvc.perform(MockMvcRequestBuilders.post("/agent/message")
                        .header("agent-session-id", agentSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deauthorizePayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode deauthJson = objectMapper.readTree(deauthResult.getResponse().getContentAsString());
        assertThat(deauthJson.path("result").path("version").asText()).isEqualTo("1.0");
        assertThat(authorizationService.isActive(loginSessionId)).isFalse();
    }
}
