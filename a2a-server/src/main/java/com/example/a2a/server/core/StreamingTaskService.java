package com.example.a2a.server.core;

import com.example.a2a.server.agent.WeatherAgent;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.CancelResult;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.MessageStreamParams;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskArtifact;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskArtifactPart;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskArtifactUpdateEvent;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskMessage;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskMessagePart;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskStatus;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskStatusEnvelope;
import com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.TaskStatusUpdateEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles the server side of the {@code message/stream} RPC by producing SSE events that match the
 * HarmonyOS agent specification.  The implementation is intentionally deterministic to keep the
 * integration tests predictable while still exercising the streaming code path.
 */
@Service
public class StreamingTaskService {

    private static final Duration STREAM_DELAY = Duration.ofMillis(150);

    private final WeatherAgent weatherAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, TaskHandle> activeTasks = new ConcurrentHashMap<>();

    public StreamingTaskService(WeatherAgent weatherAgent) {
        this.weatherAgent = weatherAgent;
    }

    public SseEmitter startStream(String requestId, MessageStreamParams params, String summary, String userQuery) {
        String taskId = params != null && params.id != null && !params.id.isBlank()
                ? params.id
                : UUID.randomUUID().toString();

        SseEmitter emitter = new SseEmitter(0L);
        TaskHandle handle = new TaskHandle(taskId, requestId, params, summary, emitter);
        activeTasks.put(taskId, handle);

        emitter.onCompletion(() -> activeTasks.remove(taskId));
        emitter.onTimeout(() -> {
            cancelInternal(handle, "failed", "流式响应超时");
            activeTasks.remove(taskId);
        });
        emitter.onError(ex -> activeTasks.remove(taskId));

        executor.submit(() -> runStream(handle, userQuery));
        return emitter;
    }

    public CancelResult cancelTask(String taskId) {
        TaskHandle handle = activeTasks.get(taskId);
        if (handle == null) {
            return null;
        }
        cancelInternal(handle, "canceled", "任务已取消");
        activeTasks.remove(taskId);

        CancelResult result = new CancelResult();
        result.id = taskId;
        result.status = new TaskStatusEnvelope();
        result.status.state = "canceled";
        return result;
    }

    public boolean hasActiveTask(String taskId) {
        return activeTasks.containsKey(taskId);
    }

    private void runStream(TaskHandle handle, String userQuery) {
        try {
            sendStatus(handle, "submitted", "任务已提交", false);
            if (handle.canceled.get()) {
                return;
            }
            sleep();

            String workingMessage = buildWorkingMessage(handle.params, userQuery);
            sendStatus(handle, "working", workingMessage, false);
            if (handle.canceled.get()) {
                return;
            }
            sleep();

            sendArtifact(handle, null, null, "正在分析请求: " + safeText(userQuery),
                    false, false, false);
            if (handle.canceled.get()) {
                return;
            }
            sleep();

            String result = weatherAgent.search(userQuery);
            sendArtifact(handle, "text", handle.summary + "\n" + result, null,
                    true, true, true);
            handle.completed.set(true);
            handle.emitter.complete();
        } catch (Exception ex) {
            cancelInternal(handle, "failed", "任务执行失败");
        }
    }

    private void cancelInternal(TaskHandle handle, String state, String message) {
        if (!handle.canceled.compareAndSet(false, true)) {
            return;
        }
        try {
            sendStatus(handle, state, message, true);
        } catch (IOException ignored) {
        }
        handle.emitter.complete();
    }

    private void sendStatus(TaskHandle handle, String state, String messageText, boolean terminal) throws IOException {
        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
        event.taskId = handle.taskId;
        event.terminal = terminal;
        event.status = new TaskStatus();
        event.status.state = state;
        event.status.message = new TaskMessage();
        event.status.message.role = "agent";
        TaskMessagePart part = new TaskMessagePart();
        part.kind = "text";
        part.text = messageText;
        event.status.message.parts = List.of(part);
        sendEvent(handle, event);
    }

    private void sendArtifact(TaskHandle handle, String textKind, String text, String reasoningText,
                              boolean append, boolean lastChunk, boolean terminal) throws IOException {
        TaskArtifactUpdateEvent event = new TaskArtifactUpdateEvent();
        event.taskId = handle.taskId;
        event.append = append;
        event.lastChunk = lastChunk;
        event.terminal = terminal;
        event.artifact = new TaskArtifact();
        event.artifact.artifactId = handle.taskId + "-artifact";
        List<TaskArtifactPart> parts = new ArrayList<>();
        if (reasoningText != null) {
            TaskArtifactPart reasoningPart = new TaskArtifactPart();
            reasoningPart.kind = "reasoningText";
            reasoningPart.reasoningText = reasoningText;
            parts.add(reasoningPart);
        }
        if (text != null) {
            TaskArtifactPart textPart = new TaskArtifactPart();
            textPart.kind = textKind == null ? "text" : textKind;
            textPart.text = text;
            parts.add(textPart);
        }
        event.artifact.parts = parts;
        sendEvent(handle, event);
    }

    private void sendEvent(TaskHandle handle, Object payload) throws IOException {
        try {
            String json = objectMapper.writeValueAsString(
                    com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos.AgentRpcResponse.success(
                            handle.requestId, payload));
            handle.emitter.send(SseEmitter.event()
                    .data(json, MediaType.APPLICATION_JSON)
                    .reconnectTime(0));
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to serialise event", e);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(STREAM_DELAY.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildWorkingMessage(MessageStreamParams params, String userQuery) {
        List<String> parts = new ArrayList<>();
        if (params != null) {
            if (params.sessionId != null && !params.sessionId.isBlank()) {
                parts.add("sessionId=" + params.sessionId);
            }
            if (params.agentLoginSessionId != null && !params.agentLoginSessionId.isBlank()) {
                parts.add("agentLoginSessionId=" + params.agentLoginSessionId);
            }
        }
        if (userQuery != null && !userQuery.isBlank()) {
            parts.add("query=\"" + userQuery + "\"");
        }
        if (parts.isEmpty()) {
            return "处理中";
        }
        return String.join(", ", parts);
    }

    private String safeText(String value) {
        return value == null ? "<空>" : value;
    }

    private static class TaskHandle {
        final String taskId;
        final String requestId;
        final MessageStreamParams params;
        final String summary;
        final SseEmitter emitter;
        final AtomicBoolean canceled = new AtomicBoolean(false);
        final AtomicBoolean completed = new AtomicBoolean(false);

        TaskHandle(String taskId, String requestId, MessageStreamParams params, String summary, SseEmitter emitter) {
            this.taskId = taskId;
            this.requestId = requestId;
            this.params = params;
            this.summary = Objects.requireNonNullElse(summary, "任务总结");
            this.emitter = emitter;
        }
    }
}
