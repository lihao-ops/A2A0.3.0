package com.example.a2a.server.core;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory conversation store that allows the server to keep short-term multi-turn
 * context per {@code sessionId}.  The implementation is intentionally simple but gives the
 * controller something concrete to clear when the {@code clearContext} RPC is invoked.
 */
@Service
public class ConversationContextService {

    private final Map<String, Map<String, ConversationContext>> sessionContexts = new ConcurrentHashMap<>();

    public void append(String agentSessionId, String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionContexts
                .computeIfAbsent(agentSessionId, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(sessionId, key -> new ConversationContext())
                .messages.add(message);
    }

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

    public ConversationContext getContext(String agentSessionId, String sessionId) {
        Map<String, ConversationContext> contexts = sessionContexts.get(agentSessionId);
        if (contexts == null) {
            return null;
        }
        return contexts.get(sessionId);
    }

    public static class ConversationContext {
        public final List<String> messages = new ArrayList<>();
    }
}
