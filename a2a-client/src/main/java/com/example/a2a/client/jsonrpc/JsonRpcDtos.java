package com.example.a2a.client.jsonrpc;

import java.util.List;

/**
 * 客户端侧 JSON-RPC DTO 定义，匹配示例服务端返回结构。
 */
public class JsonRpcDtos {
    /**
     * JSON-RPC 请求对象。
     */
    public static class JsonRpcRequest<T> {
        public String jsonrpc = "2.0";
        public String method;
        public T params;
        public String id;
    }

    /**
     * 天气查询参数。
     */
    public static class WeatherParams {
        public String text;
        public WeatherParams() {}

        /**
         * 指定查询文本的构造函数。
         */
        public WeatherParams(String text) { this.text = text; }
    }

    /**
     * JSON-RPC 通用响应。
     */
    public static class JsonRpcResponse<T> {
        public String jsonrpc;
        public String id;
        public T result;
        public JsonRpcError error;
    }

    /**
     * JSON-RPC 错误信息。
     */
    public static class JsonRpcError {
        public int code;
        public String message;
    }

    /**
     * 消息体，包含多个部件。
     */
    public static class ResponseMessage {
        public List<PartDto> parts;
    }

    /**
     * 消息部件定义。
     */
    public static class PartDto {
        public String type;
        public String text;
    }

    /**
     * AgentCard 描述。
     */
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

    /**
     * Agent 技能信息。
     */
    public static class AgentSkillDto {
        public String id;
        public String name;
        public String description;
        public List<String> tags;
        public List<String> examples;
    }

    /**
     * 任务提交参数。
     */
    public static class TaskSubmitParams { public String text; }

    /**
     * 仅包含任务 ID 的参数。
     */
    public static class TaskIdParams { public String taskId; }

    /**
     * 任务提交响应。
     */
    public static class TaskSubmitResult { public String taskId; public String state; }

    /**
     * 任务状态响应。
     */
    public static class TaskStatusResult { public String taskId; public String state; }

    /**
     * 任务结果响应。
     */
    public static class TaskResult { public ResponseMessage message; }
}