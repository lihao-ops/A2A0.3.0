package com.example.a2a.server.transport;

import java.util.List;

/**
 * JSON-RPC 2.0 的示例数据结构集合，模拟 A2A Message/Part 结构，供传统控制器复用。
 */
public class JsonRpcDtos {
    /**
     * 通用的 JSON-RPC 请求对象。
     */
    public static class JsonRpcRequest {
        public String jsonrpc;
        public String method;
        public Object params;
        public String id;
    }

    /**
     * 天气查询请求参数。
     */
    public static class WeatherParams {
        public String text;
    }

    /**
     * JSON-RPC 响应对象。
     */
    public static class JsonRpcResponse<T> {
        public String jsonrpc = "2.0";
        public String id;
        public T result;
        public JsonRpcError error;
    }

    /**
     * JSON-RPC 错误结构。
     */
    public static class JsonRpcError {
        public int code;
        public String message;

        /**
         * 默认构造函数，保持与序列化框架兼容。
         */
        public JsonRpcError() {}

        /**
         * 指定错误码和描述的构造函数。
         *
         * @param code    错误码
         * @param message 错误信息
         */
        public JsonRpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    /**
     * 任务结果消息，包含多个部件。
     */
    public static class ResponseMessage {
        public List<PartDto> parts;
    }

    /**
     * 消息中的部件，默认为文本类型。
     */
    public static class PartDto {
        public String kind = "text";
        public String text;
        public FilePart file;
        public Object data;

        /**
         * 默认构造函数。
         */
        public PartDto() {}

        /**
         * 指定文本内容的构造函数。
         *
         * @param text 文本内容
         */
        public PartDto(String text) {
            this.kind = "text";
            this.text = text;
        }
    }

    /**
     * 附件型部件的元信息。
     */
    public static class FilePart {
        public String name;
        public String mimeType;
        public String bytes;
        public String uri;
    }

    /**
     * AgentCard 数据结构。
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
     * Agent 技能描述。
     */
    public static class AgentSkillDto {
        public String id;
        public String name;
        public String description;
        public List<String> tags;
        public List<String> examples;
    }

    /**
     * 提交任务请求参数。
     */
    public static class TaskSubmitParams {
        public String text;
    }

    /**
     * 仅包含任务 ID 的请求参数。
     */
    public static class TaskIdParams {
        public String taskId;
    }

    /**
     * 任务提交后返回的结果。
     */
    public static class TaskSubmitResult {
        public String taskId;
        public String state; // 任务提交后的状态（默认 SUBMITTED）
    }

    /**
     * 查询任务状态时的响应。
     */
    public static class TaskStatusResult {
        public String taskId;
        public String state; // 任务状态（RUNNING、COMPLETED、FAILED、CANCELED）
    }

    /**
     * 任务最终结果，包含消息。
     */
    public static class TaskResult {
        public ResponseMessage message; // 仅在 COMPLETED 时返回
    }
}
