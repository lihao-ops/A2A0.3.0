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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentMessageControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

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
        ResponseEntity<String> response = postJson(initializePayload, null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode json = objectMapper.readTree(response.getBody());
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

        ResponseEntity<String> response = postJson(payload, agentSessionId);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode json = objectMapper.readTree(response.getBody());
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

        ResponseEntity<String> response = postStream(payload, agentSessionId);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        String body = response.getBody();
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

        CompletableFuture<ResponseEntity<String>> streamFuture = CompletableFuture.supplyAsync(() ->
                postStream(streamPayload, agentSessionId));

        waitUntil(() -> streamingTaskService.hasActiveTask(taskId), 5_000L);

        String cancelPayload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"cancel-1\"," +
                "\"method\":\"tasks/cancel\"," +
                "\"params\":{\"id\":\"" + taskId + "\"}}";

        ResponseEntity<String> cancelResponse = postJson(cancelPayload, agentSessionId);
        assertThat(cancelResponse.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode cancelJson = objectMapper.readTree(cancelResponse.getBody());
        assertThat(cancelJson.path("result").path("status").path("state").asText()).isEqualTo("canceled");

        ResponseEntity<String> streamResponse = streamFuture.get(5, TimeUnit.SECONDS);
        assertThat(streamResponse.getStatusCode().is2xxSuccessful()).isTrue();

        String body = streamResponse.getBody();
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

        ResponseEntity<String> streamResponse = postStream(streamPayload, agentSessionId);
        assertThat(streamResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ConversationContext context = conversationContextService.getContext(agentSessionId, "conversation-3");
        assertThat(context).isNotNull();

        String clearPayload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"clear-1\"," +
                "\"method\":\"clearContext\"," +
                "\"params\":{\"sessionId\":\"conversation-3\"}}";

        ResponseEntity<String> response = postJson(clearPayload, agentSessionId);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode json = objectMapper.readTree(response.getBody());
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

        ResponseEntity<String> authResponse = postJson(authorizePayload, agentSessionId);
        assertThat(authResponse.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode authJson = objectMapper.readTree(authResponse.getBody());
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

        ResponseEntity<String> deauthResponse = postJson(deauthorizePayload, agentSessionId);
        assertThat(deauthResponse.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode deauthJson = objectMapper.readTree(deauthResponse.getBody());
        assertThat(deauthJson.path("result").path("version").asText()).isEqualTo("1.0");
        assertThat(authorizationService.isActive(loginSessionId)).isFalse();
    }

    private ResponseEntity<String> postJson(String payload, String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionId != null) {
            headers.add("agent-session-id", sessionId);
        }
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        return restTemplate.postForEntity("/agent/message", entity, String.class);
    }

    private ResponseEntity<String> postStream(String payload, String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.TEXT_EVENT_STREAM));
        if (sessionId != null) {
            headers.add("agent-session-id", sessionId);
        }
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        return restTemplate.exchange("/agent/message", HttpMethod.POST, entity, String.class);
    }

    private void waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition was not met within " + timeoutMillis + " ms");
    }
}
