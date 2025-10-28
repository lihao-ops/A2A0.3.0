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
 * 负责实现 {@code message/stream} RPC 的服务端逻辑，通过 SSE 推送符合 HarmonyOS 规范的
 * 事件序列。实现保持确定性输出，便于集成测试覆盖流式分支且结果可预期。
 */
@Service
public class StreamingTaskService {

    private static final Duration STREAM_DELAY = Duration.ofMillis(150);

    private final WeatherAgent weatherAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, TaskHandle> activeTasks = new ConcurrentHashMap<>();

    /**
     * 构造服务并注入天气查询占位实现。
     *
     * @param weatherAgent 天气查询 Agent
     */
    public StreamingTaskService(WeatherAgent weatherAgent) {
        this.weatherAgent = weatherAgent;
    }

    /**
     * 启动流式任务：注册回调、提交异步执行并立即返回 SSE 通道。
     *
     * @param requestId JSON-RPC 请求标识
     * @param params    客户端传入的流式参数
     * @param summary   任务摘要文本
     * @param userQuery 用户原始查询内容
     * @return 可用于推送事件的 {@link SseEmitter}
     */
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

    /**
     * 取消指定任务并返回取消结果，若任务不存在返回 {@code null}。
     *
     * @param taskId 需要取消的任务标识
     * @return 取消成功时的结果对象
     */
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

    /**
     * 判断任务是否仍处于活动状态。
     *
     * @param taskId 任务标识
     * @return {@code true} 表示仍在执行或等待
     */
    public boolean hasActiveTask(String taskId) {
        return activeTasks.containsKey(taskId);
    }

    /**
     * 执行流式任务的主流程，按顺序推送状态和产物，必要时响应取消。
     *
     * @param handle    任务上下文
     * @param userQuery 用户原始查询
     */
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

    /**
     * 内部取消流程：保证只触发一次并输出终止状态。
     *
     * @param handle 任务上下文
     * @param state  取消后状态
     * @param message 状态说明
     */
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

    /**
     * 发送任务状态事件。
     *
     * @param handle      任务上下文
     * @param state       状态值
     * @param messageText 状态描述
     * @param terminal    是否终止事件
     * @throws IOException 序列化或网络异常
     */
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

    /**
     * 发送任务产物事件，支持增量/终止控制。
     *
     * @param handle       任务上下文
     * @param textKind     文本部件类型
     * @param text         产物文本
     * @param reasoningText 推理说明文本
     * @param append       是否追加
     * @param lastChunk    是否最后一块
     * @param terminal     是否终止事件
     * @throws IOException 序列化或网络异常
     */
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

    /**
     * 将事件对象写入 SSE 通道。
     *
     * @param handle  任务上下文
     * @param payload 需要下发的事件内容
     * @throws IOException 写入失败时抛出
     */
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

    /**
     * 控制流式事件之间的延迟，模拟真实耗时。
     */
    private void sleep() {
        try {
            Thread.sleep(STREAM_DELAY.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 构建任务执行中的提示文本，包含会话和查询信息。
     *
     * @param params    客户端参数
     * @param userQuery 用户查询
     * @return 拼接的状态描述
     */
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

    /**
     * 对可能为空的文本进行安全处理。
     *
     * @param value 原始文本
     * @return 非空占位或原值
     */
    private String safeText(String value) {
        return value == null ? "<空>" : value;
    }

    /**
     * 内部任务状态载体，持有 SSE 通道和取消标志。
     */
    private static class TaskHandle {
        final String taskId;
        final String requestId;
        final MessageStreamParams params;
        final String summary;
        final SseEmitter emitter;
        final AtomicBoolean canceled = new AtomicBoolean(false);
        final AtomicBoolean completed = new AtomicBoolean(false);

        /**
         * 记录任务基础信息。
         *
         * @param taskId   服务端生成的任务标识
         * @param requestId JSON-RPC 请求编号
         * @param params   客户端传入的流式参数
         * @param summary  任务摘要
         * @param emitter  SSE 通道
         */
        TaskHandle(String taskId, String requestId, MessageStreamParams params, String summary, SseEmitter emitter) {
            this.taskId = taskId;
            this.requestId = requestId;
            this.params = params;
            this.summary = Objects.requireNonNullElse(summary, "任务总结");
            this.emitter = emitter;
        }
    }
}
