package com.example.a2a.server.transport.message;

import com.example.a2a.server.agent.WeatherAgent;
import com.example.a2a.server.transport.JsonRpcDtos.JsonRpcError;
import com.example.a2a.server.transport.JsonRpcDtos.JsonRpcResponse;
import com.example.a2a.server.transport.JsonRpcDtos.PartDto;
import com.example.a2a.server.transport.JsonRpcDtos.ResponseMessage;
import com.example.a2a.server.transport.message.dto.MessageStreamDtos.AgentMessageDto;
import com.example.a2a.server.transport.message.dto.MessageStreamDtos.FilePartDto;
import com.example.a2a.server.transport.message.dto.MessageStreamDtos.MessagePartDto;
import com.example.a2a.server.transport.message.dto.MessageStreamDtos.MessageStreamParamsDto;
import com.example.a2a.server.transport.message.dto.MessageStreamDtos.MessageStreamRequestDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/agent")
public class MessageStreamController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WeatherAgent weatherAgent;

    public MessageStreamController(WeatherAgent weatherAgent) {
        this.weatherAgent = weatherAgent;
    }

    @PostMapping("/message")
    public ResponseEntity<JsonRpcResponse<ResponseMessage>> handleMessageStream(
            @RequestBody MessageStreamRequestDto request,
            @RequestHeader(value = "agent-session-id", required = false) String agentSessionId) {

        JsonRpcResponse<ResponseMessage> response = new JsonRpcResponse<>();
        response.id = request.id;

        if (request.jsonrpc == null || !"2.0".equals(request.jsonrpc)) {
            response.error = new JsonRpcError(-32600, "Invalid Request: jsonrpc must be '2.0'");
            return ResponseEntity.badRequest().body(response);
        }
        if (!"message/stream".equals(request.method)) {
            response.error = new JsonRpcError(-32601, "Method not found: " + request.method);
            return ResponseEntity.ok(response);
        }

        MessageStreamParamsDto params = request.params;
        if (params == null || params.message == null || params.message.parts == null || params.message.parts.isEmpty()) {
            response.error = new JsonRpcError(-32602, "Invalid params: message with parts required");
            return ResponseEntity.ok(response);
        }

        String textQuery = extractTextQuery(params.message);
        String weatherResult = weatherAgent.search(textQuery);
        String summary = buildSummary(params, agentSessionId);

        ResponseMessage message = new ResponseMessage();
        message.parts = List.of(new PartDto(summary), new PartDto(weatherResult));
        response.result = message;
        return ResponseEntity.ok(response);
    }

    private String extractTextQuery(AgentMessageDto message) {
        if (message.parts == null) {
            return null;
        }
        return message.parts.stream()
                .filter(part -> part != null && Objects.equals("text", part.kind))
                .map(part -> part.text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String buildSummary(MessageStreamParamsDto params, String agentSessionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("messageStream requestId=")
                .append(stringOrPlaceholder(params.id))
                .append(" sessionId=")
                .append(stringOrPlaceholder(params.sessionId))
                .append(" agentLoginSessionId=")
                .append(stringOrPlaceholder(params.agentLoginSessionId))
                .append(" agentSessionId=")
                .append(stringOrPlaceholder(agentSessionId));

        if (params.message != null) {
            sb.append(" role=").append(stringOrPlaceholder(params.message.role));
            List<String> partDescriptions = new ArrayList<>();
            if (params.message.parts != null) {
                for (MessagePartDto part : params.message.parts) {
                    if (part == null) {
                        continue;
                    }
                    String kind = stringOrPlaceholder(part.kind);
                    switch (kind) {
                        case "text" -> partDescriptions.add("text:\"" + stringOrPlaceholder(part.text) + "\"");
                        case "file" -> partDescriptions.add(describeFile(part.file));
                        case "data" -> partDescriptions.add("data:" + describeData(part.data));
                        default -> partDescriptions.add(kind);
                    }
                }
            }
            sb.append(" parts=").append("[").append(String.join(", ", partDescriptions)).append("]");
        }
        return sb.toString();
    }

    private String describeFile(FilePartDto file) {
        if (file == null) {
            return "file:\"<unknown>\"";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("file:\"")
                .append(stringOrPlaceholder(file.name))
                .append("\" (")
                .append(stringOrPlaceholder(file.mimeType))
                .append(")");
        if (file.uri != null && !file.uri.isBlank()) {
            builder.append(" uri=").append(file.uri);
        } else if (file.bytes != null && !file.bytes.isBlank()) {
            builder.append(" bytes(length=").append(file.bytes.length()).append(")");
        }
        return builder.toString();
    }

    private String describeData(JsonNode data) {
        if (data == null || data.isNull()) {
            return "{}";
        }
        if (data.isValueNode()) {
            return data.asText();
        }
        try {
            Map<String, Object> value = OBJECT_MAPPER.convertValue(data, new TypeReference<Map<String, Object>>() {});
            return value.toString();
        } catch (IllegalArgumentException ex) {
            return data.toString();
        }
    }

    private String stringOrPlaceholder(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }
}
