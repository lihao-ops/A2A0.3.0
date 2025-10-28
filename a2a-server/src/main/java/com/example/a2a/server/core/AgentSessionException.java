package com.example.a2a.server.core;

/**
 * 当请求缺失或包含非法的 agent-session-id 头时抛出的运行时异常，控制器会将其映射到
 * 传输层错误码区间，提示客户端修正请求。
 */
public class AgentSessionException extends RuntimeException {

    /**
     * 构造异常实例并保存错误描述。
     *
     * @param message 错误原因说明
     */
    public AgentSessionException(String message) {
        super(message);
    }
}
