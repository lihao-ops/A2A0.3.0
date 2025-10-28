package com.example.a2a.server.transport.agent;

import com.example.a2a.server.core.AgentSessionException;
import com.example.a2a.server.core.AgentSessionService;
import com.example.a2a.server.core.AuthorizationService;
import com.example.a2a.server.core.ConversationContextService;
import com.example.a2a.server.core.StreamingTaskService;
import com.example.a2a.server.core.AgentSessionService.SessionRecord;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.AckResult;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.AgentMessage;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.AgentRpcRequest;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.AgentRpcResponse;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.AuthorizationParams;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.AuthorizeResult;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.CancelResult;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.ClearContextParams;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.ClearContextResult;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.DeauthorizeParams;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.DeauthorizeResult;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.InitializeResult;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.MessageStreamParams;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskCancelParams;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskStatusEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

/**
 * Handles the HarmonyOS agent JSON-RPC methods on the unified {@code /agent/message} endpoint.
 * The controller intentionally mirrors the behaviour defined by the A2A 0.3.0 SDK but relies on
 * lightweight DTOs so that the sample can be built in an offline environment.
 */
@RestController
@RequestMapping("/agent")
public class AgentMessageController {

    private final ObjectMapper objectMapper;
    private final AgentSessionService agentSessionService;
    private final AuthorizationService authorizationService;
    private final ConversationContextService conversationContextService;
    private final StreamingTaskService streamingTaskService;

    public AgentMessageController(ObjectMapper objectMapper,
                                  AgentSessionService agentSessionService,
                                  AuthorizationService authorizationService,
                                  ConversationContextService conversationContextService,
                                  StreamingTaskService streamingTaskService) {
        this.objectMapper = objectMapper;
        this.agentSessionService = agentSessionService;
        this.authorizationService = authorizationService;
        this.conversationContextService = conversationContextService;
        this.streamingTaskService = streamingTaskService;
    }

    @PostMapping("/message")
    public Object handle(@RequestBody AgentRpcRequest request,
                         @RequestHeader(value = "agent-session-id", required = false) String agentSessionId) {
        if (request.jsonrpc == null || !"2.0".equals(request.jsonrpc)) {
            return ResponseEntity.badRequest()
                    .body(AgentRpcResponse.error(request.id, -32600, "Invalid Request: jsonrpc must be '2.0'"));
        }

        try {
            return switch (request.method) {
                case "initialize" -> handleInitialize(request);
                case "notifications/initialized" -> handleInitialized(request, agentSessionId);
                case "message/stream" -> handleMessageStream(request, agentSessionId);
                case "tasks/cancel" -> handleTaskCancel(request, agentSessionId);
                case "clearContext" -> handleClearContext(request, agentSessionId);
                case "authorize" -> handleAuthorize(request, agentSessionId);
                case "deauthorize" -> handleDeauthorize(request, agentSessionId);
                default -> AgentRpcResponse.error(request.id, -32601,
                        "Method not found: " + request.method);
            };
        } catch (AgentSessionException ex) {
            return AgentRpcResponse.error(request.id, -32001, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return AgentRpcResponse.error(request.id, -32602, ex.getMessage());
        } catch (Exception ex) {
            return AgentRpcResponse.error(request.id, -32603, ex.getMessage());
        }
    }

    private AgentRpcResponse<InitializeResult> handleInitialize(AgentRpcRequest request) {
        SessionRecord record = agentSessionService.createSession();
        InitializeResult result = new InitializeResult();
        result.agentSessionId = record.agentSessionId;
        result.agentSessionTtl = agentSessionService.getDefaultTtlSeconds();
        return AgentRpcResponse.success(request.id, result);
    }

    private AgentRpcResponse<AckResult> handleInitialized(AgentRpcRequest request, String agentSessionId) {
        agentSessionService.markInitialized(agentSessionId);
        return AgentRpcResponse.success(request.id, new AckResult());
    }

    private SseEmitter handleMessageStream(AgentRpcRequest request, String agentSessionId)
            throws JsonProcessingException {
        agentSessionService.requireSession(agentSessionId);

        MessageStreamParams params = readParams(request.params, MessageStreamParams.class);
        if (params == null || params.message == null || params.message.parts == null || params.message.parts.isEmpty()) {
            throw new IllegalArgumentException("Invalid params: message with parts required");
        }

        String textQuery = extractTextParam(params.message).orElse("");
        String summary = buildSummary(request.id, params, agentSessionId);

        conversationContextService.append(agentSessionId, params.sessionId, textQuery);

        return streamingTaskService.startStream(request.id, params, summary, textQuery);
    }

    private AgentRpcResponse<CancelResult> handleTaskCancel(AgentRpcRequest request, String agentSessionId)
            throws JsonProcessingException {
        agentSessionService.requireSession(agentSessionId);
        TaskCancelParams params = readParams(request.params, TaskCancelParams.class);
        if (params == null || params.id == null || params.id.isBlank()) {
            throw new IllegalArgumentException("Invalid params: id required");
        }
        CancelResult result = streamingTaskService.cancelTask(params.id);
        if (result == null) {
            return AgentRpcResponse.error(request.id, -32004, "Task not found");
        }
        return AgentRpcResponse.success(request.id, result);
    }

    private AgentRpcResponse<ClearContextResult> handleClearContext(AgentRpcRequest request, String agentSessionId)
            throws JsonProcessingException {
        agentSessionService.requireSession(agentSessionId);
        ClearContextParams params = readParams(request.params, ClearContextParams.class);
        conversationContextService.clear(agentSessionId, params != null ? params.sessionId : null);

        ClearContextResult result = new ClearContextResult();
        result.status = new TaskStatusEnvelope();
        result.status.state = "cleared";
        return AgentRpcResponse.success(request.id, result);
    }

    private AgentRpcResponse<AuthorizeResult> handleAuthorize(AgentRpcRequest request, String agentSessionId)
            throws JsonProcessingException {
        agentSessionService.requireSession(agentSessionId);
        AuthorizationParams params = readParams(request.params, AuthorizationParams.class);
        String authCode = extractDataField(params != null ? params.message : null, "authCode")
                .orElseThrow(() -> new IllegalArgumentException("Invalid params: authCode required"));

        AuthorizationService.AuthorizationRecord record =
                authorizationService.createLoginSession(agentSessionId, authCode);

        AuthorizeResult result = new AuthorizeResult();
        result.agentLoginSessionId = record.agentLoginSessionId;
        return AgentRpcResponse.success(request.id, result);
    }

    private AgentRpcResponse<DeauthorizeResult> handleDeauthorize(AgentRpcRequest request, String agentSessionId)
            throws JsonProcessingException {
        agentSessionService.requireSession(agentSessionId);
        DeauthorizeParams params = readParams(request.params, DeauthorizeParams.class);
        String agentLoginSessionId = extractDataField(params != null ? params.message : null, "agentLoginSessionId")
                .orElseThrow(() -> new IllegalArgumentException("Invalid params: agentLoginSessionId required"));

        boolean removed = authorizationService.revokeLogin(agentLoginSessionId);
        if (!removed) {
            return AgentRpcResponse.error(request.id, -32005, "agentLoginSessionId not found");
        }

        DeauthorizeResult result = new DeauthorizeResult();
        return AgentRpcResponse.success(request.id, result);
    }

    private String buildSummary(String requestId, MessageStreamParams params, String agentSessionId) {
        StringBuilder builder = new StringBuilder("message/stream requestId=")
                .append(requestId == null ? "<unknown>" : requestId)
                .append(" taskId=")
                .append(params.id == null ? "<unknown>" : params.id);
        if (params.sessionId != null && !params.sessionId.isBlank()) {
            builder.append(" sessionId=").append(params.sessionId);
        }
        if (params.agentLoginSessionId != null && !params.agentLoginSessionId.isBlank()) {
            builder.append(" agentLoginSessionId=").append(params.agentLoginSessionId);
        }
        if (agentSessionId != null && !agentSessionId.isBlank()) {
            builder.append(" agentSessionId=").append(agentSessionId);
        }
        return builder.toString();
    }

    private Optional<String> extractTextParam(AgentMessage message) {
        if (message == null || message.parts == null) {
            return Optional.empty();
        }
        return message.parts.stream()
                .filter(part -> part != null && "text".equals(part.kind))
                .map(part -> part.text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst();
    }

    private Optional<String> extractDataField(AgentMessage message, String fieldName) {
        if (message == null || message.parts == null) {
            return Optional.empty();
        }
        return message.parts.stream()
                .filter(part -> part != null && "data".equals(part.kind) && part.data != null && part.data.has(fieldName))
                .map(part -> part.data.path(fieldName).asText(null))
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private <T> T readParams(JsonNode node, Class<T> type) throws JsonProcessingException {
        if (node == null || node.isNull()) {
            return null;
        }
        return objectMapper.treeToValue(node, type);
    }
}
