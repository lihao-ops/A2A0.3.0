package com.example.a2a.server.core;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按照华为 HarmonyOS 协议维护 agent-session 标识，使用内存存储并设定默认 7 天有效期。
 * 服务负责校验请求头，并在收到 {@code notifications/initialized} RPC 后标记会话已完成初始化。
 */
@Service
public class AgentSessionService {

    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final Clock clock;
    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    /**
     * 使用系统 UTC 时钟创建服务实例。
     */
    public AgentSessionService() {
        this(Clock.systemUTC());
    }

    /**
     * 指定时钟的构造函数，方便测试覆盖。
     *
     * @param clock 用于计算过期时间的时钟
     */
    AgentSessionService(Clock clock) {
        this.clock = clock;
    }

    /**
     * 生成新的会话记录并写入缓存。
     *
     * @return 新创建的会话记录
     */
    public SessionRecord createSession() {
        String id = UUID.randomUUID().toString().replaceAll("-", "");
        Instant now = clock.instant();
        SessionRecord record = new SessionRecord(id, now.plus(DEFAULT_TTL));
        sessions.put(id, record);
        return record;
    }

    /**
     * 校验并返回现有会话，若缺失或已过期则抛出异常。
     *
     * @param agentSessionId 会话标识
     * @return 有效的会话记录
     */
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

    /**
     * 标记指定会话已完成初始化流程。
     *
     * @param agentSessionId 会话标识
     */
    public void markInitialized(String agentSessionId) {
        SessionRecord record = requireSession(agentSessionId);
        record.initialized = true;
    }

    /**
     * 主动清理会话记录。
     *
     * @param agentSessionId 会话标识
     */
    public void clearSession(String agentSessionId) {
        sessions.remove(agentSessionId);
    }

    /**
     * 读取默认会话有效期（秒）。
     *
     * @return 默认有效期秒数
     */
    public long getDefaultTtlSeconds() {
        return DEFAULT_TTL.toSeconds();
    }

    /**
     * 判断会话是否已经完成初始化。
     *
     * @param agentSessionId 会话标识
     * @return {@code true} 表示已初始化
     */
    public boolean isInitialized(String agentSessionId) {
        SessionRecord record = sessions.get(agentSessionId);
        return record != null && record.initialized;
    }

    /**
     * 会话记录结构，包含标识、过期时间及初始化状态。
     */
    public static class SessionRecord {
        public final String agentSessionId;
        public final Instant expiresAt;
        public volatile boolean initialized;

        /**
         * 记录会话的基础信息。
         *
         * @param agentSessionId 会话标识
         * @param expiresAt      过期时间
         */
        SessionRecord(String agentSessionId, Instant expiresAt) {
            this.agentSessionId = agentSessionId;
            this.expiresAt = expiresAt;
        }
    }
}
