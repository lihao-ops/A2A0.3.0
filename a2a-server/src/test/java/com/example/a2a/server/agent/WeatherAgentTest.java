package com.example.a2a.server.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherAgentTest {

    private final WeatherAgent agent = new WeatherAgent();

    @Test
    void returnsPromptWhenLocationMissing() {
        assertThat(agent.search(null)).isEqualTo("Please provide a valid location.");
        assertThat(agent.search("   ")).isEqualTo("Please provide a valid location.");
    }

    @Test
    void trimsWeatherPrefixFromQuery() {
        assertThat(agent.search("weather in London")).isEqualTo("Weather in London: Sunny 25°C");
        assertThat(agent.search("Weather in LA, CA? ")).isEqualTo("Weather in LA, CA: Sunny 25°C");
    }

    @Test
    void leavesNonPrefixedQueriesUntouched() {
        assertThat(agent.search("New York, NY")).isEqualTo("Weather in New York, NY: Sunny 25°C");
    }
}
