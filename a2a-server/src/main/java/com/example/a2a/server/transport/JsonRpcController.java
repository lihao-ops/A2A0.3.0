package com.example.a2a.server.transport;

import com.example.a2a.server.agent.WeatherAgent;
import com.example.a2a.server.core.TaskService;
import com.example.a2a.server.transport.message.dto.MessageStreamDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<JsonRpcResponse<?>> handle(@RequestBody JsonRpcRequest request,
                                                     @RequestHeader(value = "agent-session-id", required = false)
                                                     String agentSessionId) {
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
            } else if ("message/stream".equals(request.method)) {
                JsonRpcResponse<ResponseMessage> r = handleMessageStream(request, base, agentSessionId);
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
                base.error = new JsonRpcError(-32601, "Method message/stream must be invoked via /agent/message");
                return ResponseEntity.ok(base);
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

    private JsonRpcResponse<ResponseMessage> handleMessageStream(JsonRpcRequest request, JsonRpcResponse<?> base,
                                                                 String agentSessionId) {
        JsonRpcResponse<ResponseMessage> resp = new JsonRpcResponse<>();
        resp.id = base.id;

        MessageStreamDtos.MessageStreamParamsDto params = MessageStreamParams(request.params);
        if (params == null || params.message == null) {
            resp.error = new JsonRpcError(-32602, "Invalid params: message required");
            return resp;
        }

        String query = null;
        MessageStreamDtos.MessagePartDto filePart = null;
        Object dataPayload = null;
        if (params.message.parts != null) {
            for (MessageStreamDtos.MessagePartDto part : params.message.parts) {
                if (part == null) continue;
                if (query == null && "text".equals(part.kind)) {
                    query = part.text;
                } else if (filePart == null && "file".equals(part.kind) && part.file != null) {
                    filePart = part;
                } else if (dataPayload == null && "data".equals(part.kind)) {
                    dataPayload = part.data;
                }
            }
        }

        if (query == null) {
            query = "";
        }

        String weatherResult = weatherAgent.search(query);
        StringBuilder builder = new StringBuilder();
        builder.append("message/stream handled for request ")
                .append(params.id == null ? "<unknown>" : params.id)
                .append('\n');
        if (params.sessionId != null) {
            builder.append("sessionId: ").append(params.sessionId).append('\n');
        }
        if (params.agentLoginSessionId != null) {
            builder.append("agentLoginSessionId: ").append(params.agentLoginSessionId).append('\n');
        }
        if (agentSessionId != null) {
            builder.append("agent-session-id(header): ").append(agentSessionId).append('\n');
        }
        builder.append("weather: ").append(weatherResult);
        if (filePart != null && filePart.file != null) {
            builder.append('\n').append("file name: ")
                    .append(filePart.file.name == null ? "<unknown>" : filePart.file.name);
            if (filePart.file.mimeType != null) {
                builder.append(" (mimeType: ").append(filePart.file.mimeType).append(')');
            }
            if (filePart.file.uri != null) {
                builder.append(" uri=").append(filePart.file.uri);
            }
            if (filePart.file.bytes != null) {
                builder.append(" bytes(length)=").append(filePart.file.bytes.length());
            }
        }
        if (dataPayload != null) {
            builder.append('\n').append("data payload: ").append(String.valueOf(dataPayload));
        }

        ResponseMessage message = new ResponseMessage();
        message.parts = List.of(new PartDto(builder.toString()));
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
}
