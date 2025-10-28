package com.example.a2a.server.core;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the lifecycle of agent login session identifiers produced by the {@code authorize}
 * method.  The implementation keeps state in-memory which is sufficient for reference and testing
 * purposes.
 */
@Service
public class AuthorizationService {

    private final Map<String, AuthorizationRecord> activeLogins = new ConcurrentHashMap<>();

    public AuthorizationRecord createLoginSession(String agentSessionId, String authCode) {
        String loginId = UUID.randomUUID().toString().replaceAll("-", "");
        AuthorizationRecord record = new AuthorizationRecord(loginId, agentSessionId, authCode, Instant.now());
        activeLogins.put(loginId, record);
        return record;
    }

    public AuthorizationRecord requireLogin(String agentLoginSessionId) {
        AuthorizationRecord record = activeLogins.get(agentLoginSessionId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown agentLoginSessionId");
        }
        return record;
    }

    public boolean revokeLogin(String agentLoginSessionId) {
        return activeLogins.remove(agentLoginSessionId) != null;
    }

    public boolean isActive(String agentLoginSessionId) {
        return activeLogins.containsKey(agentLoginSessionId);
    }

    public static class AuthorizationRecord {
        public final String agentLoginSessionId;
        public final String agentSessionId;
        public final String authCode;
        public final Instant createdAt;

        public AuthorizationRecord(String agentLoginSessionId, String agentSessionId, String authCode, Instant createdAt) {
            this.agentLoginSessionId = agentLoginSessionId;
            this.agentSessionId = agentSessionId;
            this.authCode = authCode;
            this.createdAt = createdAt;
        }
    }
}
