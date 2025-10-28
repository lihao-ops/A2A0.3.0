package com.example.a2a.server.agent;

import org.springframework.stereotype.Component;

/**
 * Minimal placeholder agent used by the HarmonyOS message/stream 示例流程。业务上会被
 * 华为聚合搜索或企业自有服务替换，这里仅返回可预测的字符串，方便联调和集成测试。
 */
@Component
public class WeatherAgent {

    /**
     * Performs a deterministic search result so that 上层的流式消息管道可以被验证。真实
     * 环境应调用华为侧的天气接口或企业自建 API。
     */
    public String search(String location) {
        // Dummy implementation; replace with real weather lookup
        String normalisedLocation = normaliseLocation(location);
        if (normalisedLocation.isBlank()) {
            return "Please provide a valid location.";
        }
        return "Weather in " + normalisedLocation + ": Sunny 25°C";
    }

    private String normaliseLocation(String raw) {
        if (raw == null) {
            return "";
        }

        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return "";
        }

        String prefix = "weather in";
        String lower = trimmed.toLowerCase();
        if (lower.startsWith(prefix)) {
            trimmed = trimmed.substring(prefix.length()).stripLeading();
            if (!trimmed.isEmpty() && trimmed.charAt(0) == ':') {
                trimmed = trimmed.substring(1);
            }
        }

        // Remove leading punctuation and trailing sentence punctuation.
        trimmed = trimmed.replaceFirst("^[\\s,:-]+", "");
        trimmed = trimmed.replaceFirst("[\\s?.!]+$", "");

        return trimmed.strip();
    }
}
