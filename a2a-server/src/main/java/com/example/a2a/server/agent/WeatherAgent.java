package com.example.a2a.server.agent;

import org.springframework.stereotype.Component;

@Component
public class WeatherAgent {

    public String search(String location) {
        // Dummy implementation; replace with real weather lookup
        if (location == null || location.isBlank()) {
            return "Please provide a valid location.";
        }
        return "Weather in " + location + ": Sunny 25°C";
    }
}