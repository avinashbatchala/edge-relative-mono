package com.edgerelative.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EdgeRelativeApplicationTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-bookworm")
            .withDatabaseName("application_test")
            .withUsername("application_test")
            .withPassword("application_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_HOST", POSTGRES::getHost);
        registry.add("POSTGRES_PORT", POSTGRES::getFirstMappedPort);
        registry.add("POSTGRES_DB", POSTGRES::getDatabaseName);
        registry.add("POSTGRES_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_PASSWORD", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DSLContext database;

    @Test
    void jooqConnectsToConfiguredPostgresWithUtcSessions() {
        var connection = database.fetchOne(
                "select current_database() as database_name, current_user as user_name, "
                        + "current_setting('TimeZone') as timezone");

        assertThat(connection).isNotNull();
        assertThat(connection.get("database_name", String.class)).isEqualTo(POSTGRES.getDatabaseName());
        assertThat(connection.get("user_name", String.class)).isEqualTo(POSTGRES.getUsername());
        assertThat(connection.get("timezone", String.class)).isEqualTo("UTC");
    }

    @Test
    void healthIsUpAndOtherActuatorEndpointsAreNotExposed() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var health = client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            var healthBody = JsonMapper.builder().build().readTree(health.body());
            assertThat(healthBody.path("status").asString()).isEqualTo("UP");
            assertThat(healthBody.has("components")).isFalse();

            var env = client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/actuator/env"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(env.statusCode()).isEqualTo(404);
        }
    }
}
