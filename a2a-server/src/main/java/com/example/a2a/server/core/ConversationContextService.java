package com.example.a2a.server.core;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存中的会话上下文存储，实现按 {@code sessionId} 保留短期多轮消息，供控制器在收到
 * {@code clearContext} RPC 时清理。结构简洁，便于在示例项目中快速替换。
 */
@Service
public class ConversationContextService {

    private final Map<String, Map<String, ConversationContext>> sessionContexts = new ConcurrentHashMap<>();

    /**
     * 向指定会话追加一条消息，缺失 sessionId 时自动忽略。
     *
     * @param agentSessionId Agent 会话标识
     * @param sessionId      业务会话标识
     * @param message        需要追加的消息文本
     */
    public void append(String agentSessionId, String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionContexts
                .computeIfAbsent(agentSessionId, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(sessionId, key -> new ConversationContext())
                .messages.add(message);
    }

    /**
     * 清理某个会话或整个 Agent 下的所有上下文。
     *
     * @param agentSessionId Agent 会话标识
     * @param sessionId      指定会话标识，为 {@code null} 时清空全部
     */
    public void clear(String agentSessionId, String sessionId) {
        Map<String, ConversationContext> contexts = sessionContexts.get(agentSessionId);
        if (contexts != null) {
            if (sessionId == null) {
                contexts.clear();
            } else {
                contexts.remove(sessionId);
            }
        }
    }

    /**
     * 获取指定会话的上下文。
     *
     * @param agentSessionId Agent 会话标识
     * @param sessionId      业务会话标识
     * @return 对应上下文，未找到返回 {@code null}
     */
    public ConversationContext getContext(String agentSessionId, String sessionId) {
        Map<String, ConversationContext> contexts = sessionContexts.get(agentSessionId);
        if (contexts == null) {
            return null;
        }
        return contexts.get(sessionId);
    }

    /**
     * 简单的会话载体，仅保存消息列表。
     */
    public static class ConversationContext {
        public final List<String> messages = new ArrayList<>();
    }
}
