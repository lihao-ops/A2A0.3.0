package com.example.a2a.server.transport.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class MessageStreamDtos {

    public static class MessageStreamRequest {
        public String jsonrpc;
        public String id;
        public String method;
        public MessageStreamParams params;
    }

    public static class MessageStreamParams {
        public String id;
        public String sessionId;
        public String agentLoginSessionId;
        public AgentMessage message;
    }

    public static class AgentMessage {
        public String role;
        public List<MessagePart> parts;
    }

    public static class MessagePart {
        public String kind;
        public String text;
        public FilePart file;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public JsonNode data;
    }

    public static class FilePart {
        public String name;
        public String mimeType;
        public String bytes;
        public String uri;
    }
}
