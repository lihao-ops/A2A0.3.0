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
class JsonRpcControllerMessageStreamTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void messageStreamRequestIsHandledAndSummarised() throws Exception {
        String payload = """
                {
                  \"jsonrpc\": \"2.0\",
                  \"id\": \"rpc-1\",
                  \"method\": \"message/stream\",
                  \"params\": {
                    \"id\": \"task-123\",
                    \"sessionId\": \"session-abc\",
                    \"agentLoginSessionId\": \"login-xyz\",
                    \"message\": {
                      \"role\": \"user\",
                      \"parts\": [
                        {\"kind\": \"text\", \"text\": \"weather in LA, CA\"},
                        {\"kind\": \"file\", \"file\": {\"name\": \"conditions.txt\", \"mimeType\": \"text/plain\", \"uri\": \"https://cdn/files/1\"}},
                        {\"kind\": \"data\", \"data\": {\"units\": \"metric\"}}
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
                .andExpect(jsonPath("$.result.parts[0].text", containsString("sessionId=session-abc")))
                .andExpect(jsonPath("$.result.parts[0].text", containsString("agentSessionId=8f01f3d172cd4396a0e535ae8aec6687")))
                .andExpect(jsonPath("$.result.parts[0].text", containsString("file:\"conditions.txt\" (text/plain) uri=https://cdn/files/1")))
                .andExpect(jsonPath("$.result.parts[0].text", containsString("data:{units=metric}")))
                .andExpect(jsonPath("$.result.parts[1].text").value("Weather in LA, CA: Sunny 25°C"));
    }
}
