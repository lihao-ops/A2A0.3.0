package com.example.a2a.server.transport.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class MessageStreamDtos {

    public static class MessageStreamRequestDto {
        public String jsonrpc;
        public String id;
        public String method;
        public MessageStreamParamsDto params;
    }

    public static class MessageStreamParamsDto {
        public String id;
        public String sessionId;
        public String agentLoginSessionId;
        public AgentMessageDto message;
    }

    public static class AgentMessageDto {
        public String role;
        public List<MessagePartDto> parts;
    }

    public static class MessagePartDto {
        public String kind;
        public String text;
        public FilePartDto file;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public JsonNode data;
    }

    public static class FilePartDto {
        public String name;
        public String mimeType;
        public String bytes;
        public String uri;
    }
}
