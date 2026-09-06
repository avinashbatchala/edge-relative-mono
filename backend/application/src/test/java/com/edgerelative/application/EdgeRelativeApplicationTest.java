package com.edgerelative.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EdgeRelativeApplicationTest {
    @LocalServerPort
    private int port;

    @Test
    void healthIsUpAndOtherActuatorEndpointsAreNotExposed() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var health = client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(JsonMapper.builder().build().readTree(health.body()).path("status").asString())
                    .isEqualTo("UP");

            var env = client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/actuator/env"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(env.statusCode()).isEqualTo(404);
        }
    }
}
