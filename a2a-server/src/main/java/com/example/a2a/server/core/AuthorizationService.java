package com.example.a2a.server.core;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责 {@code authorize} 方法产生的 agent login 会话标识的生命周期管理，使用内存保存状态，
 * 满足示例和测试场景的需求。
 */
@Service
public class AuthorizationService {

    private final Map<String, AuthorizationRecord> activeLogins = new ConcurrentHashMap<>();

    /**
     * 创建新的登录会话并缓存。
     *
     * @param agentSessionId Agent 会话标识
     * @param authCode       授权码
     * @return 新的登录记录
     */
    public AuthorizationRecord createLoginSession(String agentSessionId, String authCode) {
        String loginId = UUID.randomUUID().toString().replaceAll("-", "");
        AuthorizationRecord record = new AuthorizationRecord(loginId, agentSessionId, authCode, Instant.now());
        activeLogins.put(loginId, record);
        return record;
    }

    /**
     * 根据登录会话标识获取记录，若不存在则抛出异常。
     *
     * @param agentLoginSessionId 登录会话标识
     * @return 对应的登录记录
     */
    public AuthorizationRecord requireLogin(String agentLoginSessionId) {
        AuthorizationRecord record = activeLogins.get(agentLoginSessionId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown agentLoginSessionId");
        }
        return record;
    }

    /**
     * 移除登录会话。
     *
     * @param agentLoginSessionId 登录会话标识
     * @return {@code true} 表示成功移除
     */
    public boolean revokeLogin(String agentLoginSessionId) {
        return activeLogins.remove(agentLoginSessionId) != null;
    }

    /**
     * 判断登录会话是否仍然有效。
     *
     * @param agentLoginSessionId 登录会话标识
     * @return {@code true} 表示存在有效记录
     */
    public boolean isActive(String agentLoginSessionId) {
        return activeLogins.containsKey(agentLoginSessionId);
    }

    /**
     * 登录记录结构体，保存登录会话的元数据。
     */
    public static class AuthorizationRecord {
        public final String agentLoginSessionId;
        public final String agentSessionId;
        public final String authCode;
        public final Instant createdAt;

        /**
         * 记录登录会话的关键字段。
         *
         * @param agentLoginSessionId 登录会话标识
         * @param agentSessionId      Agent 会话标识
         * @param authCode            授权码
         * @param createdAt           创建时间
         */
        public AuthorizationRecord(String agentLoginSessionId, String agentSessionId, String authCode, Instant createdAt) {
            this.agentLoginSessionId = agentLoginSessionId;
            this.agentSessionId = agentSessionId;
            this.authCode = authCode;
            this.createdAt = createdAt;
        }
    }
}
