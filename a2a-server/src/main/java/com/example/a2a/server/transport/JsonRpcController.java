package com.example.a2a.server.transport;

import com.example.a2a.server.agent.WeatherAgent;
import com.example.a2a.server.core.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.example.a2a.server.transport.JsonRpcDtos.*;

@RestController
public class JsonRpcController {

    private final WeatherAgent weatherAgent;
    private final TaskService taskService;

    public JsonRpcController(WeatherAgent weatherAgent, TaskService taskService) {
        this.weatherAgent = weatherAgent;
        this.taskService = taskService;
    }

    @PostMapping({"/jsonrpc", "/agent/message"})
    public ResponseEntity<JsonRpcResponse<?>> handle(@RequestBody JsonRpcRequest request) {
        JsonRpcResponse<?> base = new JsonRpcResponse<>();
        base.id = request.id;

        if (request.jsonrpc == null || !"2.0".equals(request.jsonrpc)) {
            base.error = new JsonRpcError(-32600, "Invalid Request: jsonrpc must be '2.0'");
            return ResponseEntity.badRequest().body(base);
        }

        try {
            if ("weather_search".equals(request.method)) {
                JsonRpcResponse<ResponseMessage> r = handleWeatherSearch(request, base);
                return ResponseEntity.ok((JsonRpcResponse<?>) r);
            } else if ("agent_card".equals(request.method)) {
                JsonRpcResponse<AgentCardDto> r = handleAgentCard(base);
                return ResponseEntity.ok((JsonRpcResponse<?>) r);
            } else if ("task_submit".equals(request.method)) {
                JsonRpcResponse<TaskSubmitResult> r = handleTaskSubmit(request, base);
                return ResponseEntity.ok((JsonRpcResponse<?>) r);
            } else if ("task_status".equals(request.method)) {
                JsonRpcResponse<TaskStatusResult> r = handleTaskStatus(request, base);
                return ResponseEntity.ok((JsonRpcResponse<?>) r);
            } else if ("task_result".equals(request.method)) {
                JsonRpcResponse<TaskResult> r = handleTaskResult(request, base);
                return ResponseEntity.ok((JsonRpcResponse<?>) r);
            } else if ("task_cancel".equals(request.method)) {
                return handleTaskCancel(request, base);
            } else if ("message/stream".equals(request.method)) {
                JsonRpcResponse<ResponseMessage> r = handleMessageStream(request, base);
                return ResponseEntity.ok((JsonRpcResponse<?>) r);
            } else {
                base.error = new JsonRpcError(-32601, "Method not found: " + request.method);
                return ResponseEntity.ok(base);
            }
        } catch (Exception e) {
            base.error = new JsonRpcError(-32603, "Internal error: " + e.getMessage());
            return ResponseEntity.ok(base);
        }
    }

    private JsonRpcResponse<ResponseMessage> handleWeatherSearch(JsonRpcRequest request, JsonRpcResponse<?> base) {
        JsonRpcResponse<ResponseMessage> resp = new JsonRpcResponse<>();
        resp.id = base.id;

        WeatherParams params = mapWeatherParams(request.params);
        String query = params != null ? params.text : "";
        String result = weatherAgent.search(query);

        ResponseMessage message = new ResponseMessage();
        message.parts = java.util.List.of(new PartDto(result));
        resp.result = message;
        return resp;
    }

    private JsonRpcResponse<AgentCardDto> handleAgentCard(JsonRpcResponse<?> base) {
        JsonRpcResponse<AgentCardDto> resp = new JsonRpcResponse<>();
        resp.id = base.id;
        AgentCardDto card = new AgentCardDto();
        card.name = "Weather Agent";
        card.description = "Helps with weather";
        card.url = "http://localhost:10001";
        card.version = "1.0.0";
        card.defaultInputModes = java.util.List.of("text");
        card.defaultOutputModes = java.util.List.of("text");
        AgentSkillDto skill = new AgentSkillDto();
        skill.id = "weather_search";
        skill.name = "Search weather";
        skill.description = "Helps with weather in cities or states";
        skill.tags = java.util.List.of("weather");
        skill.examples = java.util.List.of("weather in LA, CA");
        card.skills = java.util.List.of(skill);
        card.protocolVersion = "0.3.0";
        resp.result = card;
        return resp;
    }

    private JsonRpcResponse<TaskSubmitResult> handleTaskSubmit(JsonRpcRequest request, JsonRpcResponse<?> base) {
        JsonRpcResponse<TaskSubmitResult> resp = new JsonRpcResponse<>();
        resp.id = base.id;
        TaskSubmitParams params = mapTaskSubmitParams(request.params);
        if (params == null || params.text == null) {
            resp.error = new JsonRpcError(-32602, "Invalid params: text required");
            return resp;
        }
        TaskService.TaskData data = taskService.submit(params.text);
        TaskSubmitResult result = new TaskSubmitResult();
        result.taskId = data.taskId;
        result.state = data.state;
        resp.result = result;
        return resp;
    }

    private JsonRpcResponse<TaskStatusResult> handleTaskStatus(JsonRpcRequest request, JsonRpcResponse<?> base) {
        JsonRpcResponse<TaskStatusResult> resp = new JsonRpcResponse<>();
        resp.id = base.id;
        TaskIdParams params = mapTaskIdParams(request.params);
        if (params == null || params.taskId == null) {
            resp.error = new JsonRpcError(-32602, "Invalid params: taskId required");
            return resp;
        }
        TaskService.TaskData data = taskService.get(params.taskId);
        if (data == null) {
            resp.error = new JsonRpcError(-32004, "Task not found");
            return resp;
        }
        TaskStatusResult result = new TaskStatusResult();
        result.taskId = data.taskId;
        result.state = data.state;
        resp.result = result;
        return resp;
    }

    private JsonRpcResponse<TaskResult> handleTaskResult(JsonRpcRequest request, JsonRpcResponse<?> base) {
        JsonRpcResponse<TaskResult> resp = new JsonRpcResponse<>();
        resp.id = base.id;
        TaskIdParams params = mapTaskIdParams(request.params);
        if (params == null || params.taskId == null) {
            resp.error = new JsonRpcError(-32602, "Invalid params: taskId required");
            return resp;
        }
        TaskService.TaskData data = taskService.get(params.taskId);
        if (data == null) {
            resp.error = new JsonRpcError(-32004, "Task not found");
            return resp;
        }
        if (!"COMPLETED".equals(data.state)) {
            resp.error = new JsonRpcError(-32000, "Task not completed");
            return resp;
        }
        TaskResult result = new TaskResult();
        ResponseMessage message = new ResponseMessage();
        message.parts = java.util.List.of(new PartDto(data.resultText));
        result.message = message;
        resp.result = result;
        return resp;
    }

    private JsonRpcResponse<ResponseMessage> handleMessageStream(JsonRpcRequest request, JsonRpcResponse<?> base) {
        JsonRpcResponse<ResponseMessage> resp = new JsonRpcResponse<>();
        resp.id = base.id;

        MessageStreamParams params = mapMessageStreamParams(request.params);
        if (params == null || params.message == null || params.message.parts == null || params.message.parts.isEmpty()) {
            resp.error = new JsonRpcError(-32602, "Invalid params: message with parts required");
            return resp;
        }

        String textQuery = params.message.parts.stream()
                .filter(part -> part != null && "text".equals(part.kind))
                .map(part -> part.text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);

        String weatherResult = weatherAgent.search(textQuery);
        String summary = buildMessageStreamSummary(params);

        ResponseMessage message = new ResponseMessage();
        message.parts = java.util.List.of(new PartDto(summary), new PartDto(weatherResult));
        resp.result = message;
        return resp;
    }

    private String buildMessageStreamSummary(MessageStreamParams params) {
        StringBuilder sb = new StringBuilder();
        sb.append("messageStream requestId=")
                .append(stringOrPlaceholder(params.id))
                .append(" sessionId=")
                .append(stringOrPlaceholder(params.sessionId))
                .append(" agentLoginSessionId=")
                .append(stringOrPlaceholder(params.agentLoginSessionId));

        if (params.message != null) {
            sb.append(" role=").append(stringOrPlaceholder(params.message.role));
            java.util.List<String> partDescriptions = new java.util.ArrayList<>();
            if (params.message.parts != null) {
                for (MessagePart part : params.message.parts) {
                    if (part == null) continue;
                    String kind = stringOrPlaceholder(part.kind);
                    if ("text".equals(kind)) {
                        partDescriptions.add("text:\"" + stringOrPlaceholder(part.text) + "\"");
                    } else if ("file".equals(kind)) {
                        String name = part.file != null ? stringOrPlaceholder(part.file.name) : "unknown";
                        String mime = part.file != null ? stringOrPlaceholder(part.file.mimeType) : "unknown";
                        partDescriptions.add("file:\"" + name + "\" (" + mime + ")");
                    } else if ("data".equals(kind)) {
                        partDescriptions.add("data:" + (part.data == null ? "{}" : part.data.toString()));
                    } else {
                        partDescriptions.add(kind);
                    }
                }
            }
            sb.append(" parts=[");
            sb.append(String.join(", ", partDescriptions));
            sb.append("]");
        }
        return sb.toString();
    }

    private String stringOrPlaceholder(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private ResponseEntity<JsonRpcResponse<?>> handleTaskCancel(JsonRpcRequest request, JsonRpcResponse<?> base) {
        JsonRpcResponse<TaskStatusResult> resp = new JsonRpcResponse<>();
        resp.id = base.id;
        TaskIdParams params = mapTaskIdParams(request.params);
        if (params == null || params.taskId == null) {
            resp.error = new JsonRpcError(-32602, "Invalid params: taskId required");
            return ResponseEntity.ok(resp);
        }
        TaskService.TaskData data = taskService.get(params.taskId);
        if (data == null) {
            resp.error = new JsonRpcError(-32004, "Task not found");
            return ResponseEntity.ok(resp);
        }
        boolean canceled = taskService.cancel(params.taskId);
        if (!canceled) {
            resp.error = new JsonRpcError(-32001, "Task not cancelable or already completed");
            return ResponseEntity.ok(resp);
        }
        TaskStatusResult result = new TaskStatusResult();
        result.taskId = data.taskId;
        result.state = data.state; // 可能稍后异步变为CANCELED
        resp.result = result;
        return ResponseEntity.ok(resp);
    }

    private WeatherParams mapWeatherParams(Object params) {
        if (params == null) return null;
        if (params instanceof WeatherParams wp) {
            return wp;
        }
        if (params instanceof java.util.Map<?,?> map) {
            Object text = map.get("text");
            WeatherParams wp = new WeatherParams();
            wp.text = text == null ? null : String.valueOf(text);
            return wp;
        }
        return null;
    }

    private TaskSubmitParams mapTaskSubmitParams(Object params) {
        if (params == null) return null;
        if (params instanceof TaskSubmitParams p) return p;
        if (params instanceof java.util.Map<?,?> map) {
            Object text = map.get("text");
            TaskSubmitParams p = new TaskSubmitParams();
            p.text = text == null ? null : String.valueOf(text);
            return p;
        }
        return null;
    }

    private TaskIdParams mapTaskIdParams(Object params) {
        if (params == null) return null;
        if (params instanceof TaskIdParams p) return p;
        if (params instanceof java.util.Map<?,?> map) {
            Object taskId = map.get("taskId");
            TaskIdParams p = new TaskIdParams();
            p.taskId = taskId == null ? null : String.valueOf(taskId);
            return p;
        }
        return null;
    }

    private MessageStreamParams mapMessageStreamParams(Object params) {
        if (params == null) return null;
        if (params instanceof MessageStreamParams p) return p;
        if (params instanceof java.util.Map<?,?> map) {
            MessageStreamParams p = new MessageStreamParams();
            p.id = valueAsString(map.get("id"));
            p.sessionId = valueAsString(map.get("sessionId"));
            p.agentLoginSessionId = valueAsString(map.get("agentLoginSessionId"));
            p.message = mapAgentMessage(map.get("message"));
            return p;
        }
        return null;
    }

    private AgentMessage mapAgentMessage(Object value) {
        if (value == null) return null;
        if (value instanceof AgentMessage message) return message;
        if (value instanceof java.util.Map<?,?> map) {
            AgentMessage message = new AgentMessage();
            message.role = valueAsString(map.get("role"));
            Object partsObj = map.get("parts");
            if (partsObj instanceof java.util.List<?> list) {
                java.util.List<MessagePart> parts = new java.util.ArrayList<>();
                for (Object item : list) {
                    MessagePart part = mapMessagePart(item);
                    if (part != null) {
                        parts.add(part);
                    }
                }
                message.parts = parts;
            }
            return message;
        }
        return null;
    }

    private MessagePart mapMessagePart(Object value) {
        if (value == null) return null;
        if (value instanceof MessagePart part) return part;
        if (value instanceof java.util.Map<?,?> map) {
            MessagePart part = new MessagePart();
            part.kind = valueAsString(map.get("kind"));
            part.text = valueAsString(map.get("text"));
            part.file = mapFilePart(map.get("file"));
            part.data = map.get("data");
            return part;
        }
        return null;
    }

    private FilePart mapFilePart(Object value) {
        if (value == null) return null;
        if (value instanceof FilePart filePart) return filePart;
        if (value instanceof java.util.Map<?,?> map) {
            FilePart file = new FilePart();
            file.name = valueAsString(map.get("name"));
            file.mimeType = valueAsString(map.get("mimeType"));
            file.bytes = valueAsString(map.get("bytes"));
            file.uri = valueAsString(map.get("uri"));
            return file;
        }
        return null;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
