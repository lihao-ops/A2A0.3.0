package com.example.a2a.server.transport.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * DTO definitions that model the Huawei HarmonyOS agent RPC specification while keeping
 * the wire format aligned with the A2A 0.3.0 JSON-RPC SDK.  These classes purposely mirror
 * the structure defined by the reference SDK so the controller logic can be implemented
 * without pulling the SDK dependency during tests.
 */
public final class AgentJsonRpcDtos {

    private AgentJsonRpcDtos() {
    }

    public static class AgentRpcRequest {
        public String jsonrpc;
        public String id;
        public String method;
        public JsonNode params;
    }

    public static class AgentRpcResponse<T> {
        public String jsonrpc = "2.0";
        public String id;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public T result;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public AgentRpcError error;

        public static <T> AgentRpcResponse<T> success(String id, T payload) {
            AgentRpcResponse<T> response = new AgentRpcResponse<>();
            response.id = id;
            response.result = payload;
            return response;
        }

        public static <T> AgentRpcResponse<T> error(String id, int code, String message) {
            AgentRpcResponse<T> response = new AgentRpcResponse<>();
            response.id = id;
            response.error = new AgentRpcError(code, message);
            return response;
        }
    }

    public static class AgentRpcError {
        public int code;
        public String message;

        public AgentRpcError() {
        }

        public AgentRpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public static class InitializeResult {
        public String version = "1.0";
        public String agentSessionId;
        public long agentSessionTtl;
    }

    public static class AckResult {
        public String status = "ok";
    }

    public static class AuthorizeResult {
        public String version = "1.0";
        public String agentLoginSessionId;
    }

    public static class DeauthorizeResult {
        public String version = "1.0";
    }

    public static class TaskStatusEnvelope {
        public String state;
    }

    public static class CancelResult {
        public String id;
        public TaskStatusEnvelope status;
    }

    public static class ClearContextResult {
        public TaskStatusEnvelope status;
    }

    public static class MessageStreamParams {
        public String id;
        public String sessionId;
        public String agentLoginSessionId;
        public AgentMessage message;
    }

    public static class AgentMessage {
        public String role;
        public List<AgentMessagePart> parts;
    }

    public static class AgentMessagePart {
        public String kind;
        public String text;
        public AgentFilePart file;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public JsonNode data;
    }

    public static class AgentFilePart {
        public String name;
        public String mimeType;
        public String bytes;
        public String uri;
    }

    public static class TaskStatusUpdateEvent {
        public String taskId;
        public String kind = "status-update";
        @JsonProperty("final")
        public boolean terminal;
        public TaskStatus status;
    }

    public static class TaskStatus {
        public TaskMessage message;
        public String state;
    }

    public static class TaskMessage {
        public String role;
        public List<TaskMessagePart> parts;
    }

    public static class TaskMessagePart {
        public String kind;
        public String text;
    }

    public static class TaskArtifactUpdateEvent {
        public String taskId;
        public String kind = "artifact-update";
        public boolean append;
        public boolean lastChunk;
        @JsonProperty("final")
        public boolean terminal;
        public TaskArtifact artifact;
    }

    public static class TaskArtifact {
        public String artifactId;
        public List<TaskArtifactPart> parts;
    }

    public static class TaskArtifactPart {
        public String kind;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String reasoningText;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String text;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public JsonNode data;
    }

    public static class TaskCancelParams {
        public String id;
        public String sessionId;
    }

    public static class ClearContextParams {
        public String sessionId;
    }

    public static class AuthorizationParams {
        public AgentMessage message;
    }

    public static class DeauthorizeParams {
        public AgentMessage message;
    }
}
