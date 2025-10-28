package com.example.a2a.server.transport;

import java.util.List;

// 简单的 JSON-RPC 2.0 DTO 模型与结果结构（与A2A的Message/Part风格类似）
public class JsonRpcDtos {
    public static class JsonRpcRequest {
        public String jsonrpc;
        public String method;
        public Object params;
        public String id;
    }

    public static class WeatherParams {
        public String text;
    }

    public static class JsonRpcResponse<T> {
        public String jsonrpc = "2.0";
        public String id;
        public T result;
        public JsonRpcError error;
    }

    public static class JsonRpcError {
        public int code;
        public String message;

        public JsonRpcError() {}
        public JsonRpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    // 结果采用接近A2A消息的结构：一个Message包含多个Part，这里只实现TextPart
    public static class ResponseMessage {
        public List<PartDto> parts;
    }

    public static class PartDto {
        public String type = "text";
        public String text;

        public PartDto() {}
        public PartDto(String text) { this.text = text; }
    }

    // ---- 扩展：AgentCard 与 Task 交互相关 DTO ----
    public static class AgentCardDto {
        public String name;
        public String description;
        public String url;
        public String version;
        public List<String> defaultInputModes;
        public List<String> defaultOutputModes;
        public List<AgentSkillDto> skills;
        public String protocolVersion;
    }

    public static class AgentSkillDto {
        public String id;
        public String name;
        public String description;
        public List<String> tags;
        public List<String> examples;
    }

    public static class TaskSubmitParams {
        public String text;
    }

    public static class TaskIdParams {
        public String taskId;
    }

    public static class TaskSubmitResult {
        public String taskId;
        public String state; // SUBMITTED
    }

    public static class TaskStatusResult {
        public String taskId;
        public String state; // RUNNING/COMPLETED/FAILED/CANCELED
    }

    public static class TaskResult {
        public ResponseMessage message; // 仅在COMPLETED时返回
    }
}
