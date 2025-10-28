package com.example.a2a.server.transport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JsonRpcControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldHandleMessageStreamRequest() throws Exception {
        String payload = """
            {
              \"jsonrpc\": \"2.0\",
              \"id\": \"rpc-1\",
              \"method\": \"message/stream\",
              \"params\": {
                \"id\": \"task-123\",
                \"sessionId\": \"session-1\",
                \"agentLoginSessionId\": \"agent-login-42\",
                \"message\": {
                  \"role\": \"user\",
                  \"parts\": [
                    {
                      \"kind\": \"text\",
                      \"text\": \"weather in LA\"
                    },
                    {
                      \"kind\": \"file\",
                      \"file\": {
                        \"name\": \"spec.pdf\",
                        \"mimeType\": \"application/pdf\",
                        \"uri\": \"https://example.com/spec.pdf\"
                      }
                    },
                    {
                      \"kind\": \"data\",
                      \"data\": { \"foo\": \"bar\" }
                    }
                  ]
                }
              }
            }
            """;

        mockMvc.perform(post("/agent/message")
                .contentType(MediaType.APPLICATION_JSON)
                .header("agent-session-id", "8f01f3d172cd4396a0e535ae8aec6687")
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jsonrpc").value("2.0"))
            .andExpect(jsonPath("$.id").value("rpc-1"))
            .andExpect(jsonPath("$.result.parts[0].kind").value("text"))
            .andExpect(jsonPath("$.result.parts[0].text", containsString("sessionId: session-1")))
            .andExpect(jsonPath("$.result.parts[0].text", containsString("file name: spec.pdf")))
            .andExpect(jsonPath("$.result.parts[0].text", containsString("data payload: {foo=bar}")));
    }
}
