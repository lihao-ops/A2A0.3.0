package com.example.a2a.client.jsonrpc;

import java.util.List;

public class JsonRpcDtos {
    public static class JsonRpcRequest<T> {
        public String jsonrpc = "2.0";
        public String method;
        public T params;
        public String id;
    }

    public static class WeatherParams {
        public String text;
        public WeatherParams() {}
        public WeatherParams(String text) { this.text = text; }
    }

    public static class JsonRpcResponse<T> {
        public String jsonrpc;
        public String id;
        public T result;
        public JsonRpcError error;
    }

    public static class JsonRpcError {
        public int code;
        public String message;
    }

    public static class ResponseMessage {
        public List<PartDto> parts;
    }

    public static class PartDto {
        public String type;
        public String text;
    }

    // AgentCard DTO
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

    // Task DTOs
    public static class TaskSubmitParams { public String text; }
    public static class TaskIdParams { public String taskId; }
    public static class TaskSubmitResult { public String taskId; public String state; }
    public static class TaskStatusResult { public String taskId; public String state; }
    public static class TaskResult { public ResponseMessage message; }
}