package com.example.a2a.server.agent;

import org.springframework.stereotype.Component;

/**
 * HarmonyOS message/stream 示例流程中的极简占位 Agent，实现固定返回值以便联调和测试。
 * 真实项目可替换为对接华为聚合搜索或企业自有服务的具体实现。
 */
@Component
public class WeatherAgent {

    /**
     * 根据输入位置返回可预测的天气结果，帮助验证上层的流式消息处理流程。
     *
     * @param location 用户输入的地理位置描述
     * @return 构造好的天气说明文本
     */
    public String search(String location) {
        // 占位实现：可在真实环境中替换为调用天气服务的逻辑
        String normalisedLocation = normaliseLocation(location);
        if (normalisedLocation.isBlank()) {
            return "Please provide a valid location.";
        }
        return "Weather in " + normalisedLocation + ": Sunny 25°C";
    }

    /**
     * 规范化用户输入，去除多余前缀和标点，方便后续处理。
     *
     * @param raw 原始输入文本
     * @return 清洗后的地理位置
     */
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

        // 去除前导标点以及句尾的标点符号。
        trimmed = trimmed.replaceFirst("^[\\s,:-]+", "");
        trimmed = trimmed.replaceFirst("[\\s?.!]+$", "");

        return trimmed.strip();
    }
}
