package com.example.a2a.server.transport.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 描述华为 HarmonyOS Agent RPC 规范的数据结构集合，保持与 A2A 0.3.0 JSON-RPC SDK 一致，
 * 便于在无外部依赖的情况下实现控制器逻辑并进行测试。
 */
public final class AgentJsonRpcDtos {

    /**
     * 工具类不需要实例化。
     */
    private AgentJsonRpcDtos() {
    }

    /**
     * 通用的 Agent RPC 请求格式。
     */
    public static class AgentRpcRequest {
        public String jsonrpc;
        public String id;
        public String method;
        public JsonNode params;
    }

    /**
     * 通用的 Agent RPC 响应格式。
     */
    public static class AgentRpcResponse<T> {
        public String jsonrpc = "2.0";
        public String id;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public T result;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public AgentRpcError error;

        /**
         * 创建成功响应。
         */
        public static <T> AgentRpcResponse<T> success(String id, T payload) {
            AgentRpcResponse<T> response = new AgentRpcResponse<>();
            response.id = id;
            response.result = payload;
            return response;
        }

        /**
         * 创建错误响应。
         */
        public static <T> AgentRpcResponse<T> error(String id, int code, String message) {
            AgentRpcResponse<T> response = new AgentRpcResponse<>();
            response.id = id;
            response.error = new AgentRpcError(code, message);
            return response;
        }
    }

    /**
     * Agent RPC 错误结构。
     */
    public static class AgentRpcError {
        public int code;
        public String message;

        /**
         * 默认构造函数，方便序列化。
         */
        public AgentRpcError() {
        }

        /**
         * 指定错误码和描述的构造函数。
         */
        public AgentRpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    /**
     * initialize 方法的响应结果。
     */
    public static class InitializeResult {
        public String version = "1.0";
        public String agentSessionId;
        public long agentSessionTtl;
    }

    /**
     * 通用确认响应。
     */
    public static class AckResult {
        public String status = "ok";
    }

    /**
     * authorize 方法的响应结果。
     */
    public static class AuthorizeResult {
        public String version = "1.0";
        public String agentLoginSessionId;
    }

    /**
     * deauthorize 方法的响应结果。
     */
    public static class DeauthorizeResult {
        public String version = "1.0";
    }

    /**
     * 任务状态包裹，用于统一状态字段。
     */
    public static class TaskStatusEnvelope {
        public String state;
    }

    /**
     * 取消任务的响应结果。
     */
    public static class CancelResult {
        public String id;
        public TaskStatusEnvelope status;
    }

    /**
     * 清空上下文的响应结果。
     */
    public static class ClearContextResult {
        public TaskStatusEnvelope status;
    }

    /**
     * message/stream 请求参数。
     */
    public static class MessageStreamParams {
        public String id;
        public String sessionId;
        public String agentLoginSessionId;
        public AgentMessage message;
    }

    /**
     * Agent 消息结构。
     */
    public static class AgentMessage {
        public String role;
        public List<AgentMessagePart> parts;
    }

    /**
     * Agent 消息部件。
     */
    public static class AgentMessagePart {
        public String kind;
        public String text;
        public AgentFilePart file;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public JsonNode data;
    }

    /**
     * Agent 消息中的文件部件。
     */
    public static class AgentFilePart {
        public String name;
        public String mimeType;
        public String bytes;
        public String uri;
    }

    /**
     * 任务状态更新事件。
     */
    public static class TaskStatusUpdateEvent {
        public String taskId;
        public String kind = "status-update";
        @JsonProperty("final")
        public boolean terminal;
        public TaskStatus status;
    }

    /**
     * 任务状态详细信息。
     */
    public static class TaskStatus {
        public TaskMessage message;
        public String state;
    }

    /**
     * 任务消息主体。
     */
    public static class TaskMessage {
        public String role;
        public List<TaskMessagePart> parts;
    }

    /**
     * 任务消息中的部件。
     */
    public static class TaskMessagePart {
        public String kind;
        public String text;
    }

    /**
     * 任务产物更新事件。
     */
    public static class TaskArtifactUpdateEvent {
        public String taskId;
        public String kind = "artifact-update";
        public boolean append;
        public boolean lastChunk;
        @JsonProperty("final")
        public boolean terminal;
        public TaskArtifact artifact;
    }

    /**
     * 任务产物结构。
     */
    public static class TaskArtifact {
        public String artifactId;
        public List<TaskArtifactPart> parts;
    }

    /**
     * 任务产物部件，包含文本或推理说明。
     */
    public static class TaskArtifactPart {
        public String kind;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String reasoningText;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String text;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public JsonNode data;
    }

    /**
     * tasks/cancel 请求参数。
     */
    public static class TaskCancelParams {
        public String id;
        public String sessionId;
    }

    /**
     * clearContext 请求参数。
     */
    public static class ClearContextParams {
        public String sessionId;
    }

    /**
     * authorize 请求参数。
     */
    public static class AuthorizationParams {
        public AgentMessage message;
    }

    /**
     * deauthorize 请求参数。
     */
    public static class DeauthorizeParams {
        public AgentMessage message;
    }
}
