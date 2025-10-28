package com.example.a2a.server.core;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains agent-session identifiers according to the Huawei HarmonyOS protocol. Sessions are
 * lightweight and stored in-memory with a default seven day time-to-live.  The service performs
 * validation on incoming headers and allows callers to mark a session as fully initialised once the
 * {@code notifications/initialized} RPC is received.
 */
@Service
public class AgentSessionService {

    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final Clock clock;
    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    public AgentSessionService() {
        this(Clock.systemUTC());
    }

    AgentSessionService(Clock clock) {
        this.clock = clock;
    }

    public SessionRecord createSession() {
        String id = UUID.randomUUID().toString().replaceAll("-", "");
        Instant now = clock.instant();
        SessionRecord record = new SessionRecord(id, now.plus(DEFAULT_TTL));
        sessions.put(id, record);
        return record;
    }

    public SessionRecord requireSession(String agentSessionId) {
        if (agentSessionId == null || agentSessionId.isBlank()) {
            throw new AgentSessionException("Missing agent-session-id header");
        }
        SessionRecord record = sessions.get(agentSessionId);
        if (record == null) {
            throw new AgentSessionException("Unknown agentSessionId");
        }
        if (record.expiresAt.isBefore(clock.instant())) {
            sessions.remove(agentSessionId);
            throw new AgentSessionException("agentSessionId expired");
        }
        return record;
    }

    public void markInitialized(String agentSessionId) {
        SessionRecord record = requireSession(agentSessionId);
        record.initialized = true;
    }

    public void clearSession(String agentSessionId) {
        sessions.remove(agentSessionId);
    }

    public long getDefaultTtlSeconds() {
        return DEFAULT_TTL.toSeconds();
    }

    public boolean isInitialized(String agentSessionId) {
        SessionRecord record = sessions.get(agentSessionId);
        return record != null && record.initialized;
    }

    public static class SessionRecord {
        public final String agentSessionId;
        public final Instant expiresAt;
        public volatile boolean initialized;

        SessionRecord(String agentSessionId, Instant expiresAt) {
            this.agentSessionId = agentSessionId;
            this.expiresAt = expiresAt;
        }
    }
}
