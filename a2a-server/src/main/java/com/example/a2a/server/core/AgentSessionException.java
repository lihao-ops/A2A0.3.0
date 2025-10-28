package com.example.a2a.server.core;

/**
 * Runtime exception thrown when an agent-session-id header is missing or invalid.  The controller
 * maps this to the JSON-RPC error code range reserved for transport-level issues.
 */
public class AgentSessionException extends RuntimeException {

    public AgentSessionException(String message) {
        super(message);
    }
}
