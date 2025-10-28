package com.example.a2a.server.agent;

import org.springframework.stereotype.Component;

@Component
public class WeatherAgent {

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
