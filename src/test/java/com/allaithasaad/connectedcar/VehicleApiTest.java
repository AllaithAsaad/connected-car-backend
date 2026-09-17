package com.allaithasaad.connectedcar;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VehicleApiTest {
    private static final EmbeddedPostgres POSTGRES = startPostgres();
    private final HttpClient client = HttpClient.newHttpClient();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder().setPort(0).start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM telemetry");
        jdbc.update("UPDATE vehicles SET last_seen_at = NULL");
    }

    @Test
    void seededFleetStartsOfflineWithEmptyHistory() throws Exception {
        var response = get("/api/vehicles");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(response.body(), "$[*].id"))
                .containsExactly("VOLVO-001", "VOLVO-002", "VOLVO-003");
        assertThat(JsonPath.<List<String>>read(response.body(), "$[*].status"))
                .containsOnly("OFFLINE");
        assertThat(JsonPath.<Object>read(get("/api/vehicles/VOLVO-001").body(), "$.latestTelemetry")).isNull();
        assertThat(JsonPath.<Integer>read(get("/api/vehicles/VOLVO-001/telemetry").body(), "$.totalElements"))
                .isZero();
    }

    @Test
    void ingestionPersistsAndUpdatesFleetAndHistory() throws Exception {
        var response = post("VOLVO-001", sample(Instant.now().minusSeconds(1)));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<Double>read(response.body(), "$.battery")).isEqualTo(72);
        var vehicle = get("/api/vehicles/VOLVO-001");
        assertThat(JsonPath.<String>read(vehicle.body(), "$.status")).isEqualTo("ONLINE");
        assertThat(JsonPath.<Double>read(vehicle.body(), "$.latestTelemetry.speed")).isEqualTo(84);
        assertThat(JsonPath.<Double>read(get("/api/vehicles").body(), "$[0].latestTelemetry.battery")).isEqualTo(72);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM telemetry", Integer.class)).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(get("/api/vehicles/VOLVO-001/telemetry").body(), "$.totalElements"))
                .isEqualTo(1);
    }

    @Test
    void delayedSamplesDoNotReplaceTheNewestMeasurement() throws Exception {
        Instant newer = Instant.now().minusSeconds(30);
        post("VOLVO-001", sample(newer));
        post("VOLVO-001", sample(newer.minusSeconds(60)).replace("72", "40"));
        assertThat(JsonPath.<Double>read(get("/api/vehicles/VOLVO-001").body(), "$.latestTelemetry.battery")).isEqualTo(72);
        assertThat(JsonPath.<Double>read(get("/api/vehicles").body(), "$[0].latestTelemetry.battery")).isEqualTo(72);
        var first = get("/api/vehicles/VOLVO-001/telemetry?size=1&page=0");
        assertThat(JsonPath.<Double>read(first.body(), "$.content[0].battery")).isEqualTo(72);
        assertThat(JsonPath.<Integer>read(first.body(), "$.totalPages")).isEqualTo(2);
        var second = get("/api/vehicles/VOLVO-001/telemetry?size=1&page=1");
        assertThat(JsonPath.<Double>read(second.body(), "$.content[0].battery")).isEqualTo(40);
    }

    @Test
    void historyIsIsolatedByVehicleAndBreaksTimestampTiesDeterministically() throws Exception {
        Instant time = Instant.now().minusSeconds(1);
        post("VOLVO-001", sample(time));
        post("VOLVO-001", sample(time).replace("72", "71"));
        post("VOLVO-002", sample(time).replace("72", "50"));
        var history = get("/api/vehicles/VOLVO-001/telemetry");
        assertThat(JsonPath.<List<Double>>read(history.body(), "$.content[*].battery")).containsExactly(71.0, 72.0);
        assertThat(JsonPath.<Double>read(get("/api/vehicles/VOLVO-001").body(), "$.latestTelemetry.battery")).isEqualTo(71);
    }

    @Test
    void staleVehiclesGoOfflineWithoutDeletingTheirLastReading() throws Exception {
        post("VOLVO-001", sample(Instant.now().minusSeconds(1)));
        jdbc.update("UPDATE vehicles SET last_seen_at = now() - interval '21 seconds' WHERE id = 'VOLVO-001'");
        var vehicle = get("/api/vehicles/VOLVO-001");
        assertThat(JsonPath.<String>read(vehicle.body(), "$.status")).isEqualTo("OFFLINE");
        assertThat(JsonPath.<Double>read(vehicle.body(), "$.latestTelemetry.battery")).isEqualTo(72);
        post("VOLVO-001", sample(Instant.now().minusSeconds(1)));
        assertThat(JsonPath.<String>read(get("/api/vehicles/VOLVO-001").body(), "$.status")).isEqualTo("ONLINE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"battery\":101", "\"battery\":-1", "\"battery\":null", "\"speed\":-1",
            "\"speed\":301", "\"temperature\":101", "\"temperature\":-81", "\"latitude\":91", "\"longitude\":181"})
    void rejectsInvalidMeasurementsWithoutWriting(String field) throws Exception {
        String name = field.substring(0, field.indexOf(':'));
        String payload = sample(Instant.now().minusSeconds(1)).replaceAll(name + ":[^,}]+", field);
        var response = post("VOLVO-001", payload);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
        assertThat(JsonPath.<Object>read(response.body(), "$.errors")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM telemetry", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_seen_at FROM vehicles WHERE id = 'VOLVO-001'", Instant.class)).isNull();
    }

    @Test
    void rejectsMissingFieldsFutureTimestampsAndMalformedJson() throws Exception {
        assertThat(post("VOLVO-001", "{}").statusCode()).isEqualTo(400);
        assertThat(post("VOLVO-001", sample(Instant.now().plusSeconds(3600))).statusCode()).isEqualTo(400);
        assertThat(post("VOLVO-001", "not-json").statusCode()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "page=1000001"})
    void rejectsInvalidPagination(String query) throws Exception {
        assertThat(get("/api/vehicles/VOLVO-001/telemetry?" + query).statusCode()).isEqualTo(400);
    }

    @Test
    void unknownVehiclesReturn404() throws Exception {
        assertThat(get("/api/vehicles/MISSING").statusCode()).isEqualTo(404);
        assertThat(get("/api/vehicles/MISSING/telemetry").statusCode()).isEqualTo(404);
        assertThat(post("MISSING", sample(Instant.now().minusSeconds(1))).statusCode()).isEqualTo(404);
    }

    @Test
    void simultaneousUpdatesAreAllPersisted() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Integer>> requests = IntStream.range(0, 10)
                    .<Callable<Integer>>mapToObj(i -> () -> post("VOLVO-001", sample(Instant.now().minusSeconds(1))).statusCode())
                    .toList();
            for (var future : executor.invokeAll(requests)) assertThat(future.get()).isEqualTo(201);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM telemetry", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT last_seen_at = (SELECT max(received_at) FROM telemetry) FROM vehicles WHERE id = 'VOLVO-001'", Boolean.class)).isTrue();
    }

    @Test
    void healthEndpointReportsDatabaseAvailability() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    void realSimulatorSendsTwoSamplesForEveryCar() throws Exception {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var builder = new ProcessBuilder(javaExecutable, "--source", "21", "simulator/VehicleSimulator.java")
                .redirectErrorStream(true);
        builder.environment().put("API_BASE_URL", "http://localhost:" + port);
        builder.environment().put("SIMULATOR_INTERVAL_SECONDS", "1");
        builder.environment().put("SIMULATOR_MAX_TICKS", "2");
        var process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("Simulator must finish").isTrue();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).doesNotContain("rejected", "unavailable");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM telemetry", Integer.class)).isEqualTo(6);
            assertThat(JsonPath.<List<String>>read(get("/api/vehicles").body(), "$[*].status")).containsOnly("ONLINE");
        } finally {
            process.destroyForcibly();
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String vehicle, String payload) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/vehicles/" + vehicle + "/telemetry"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String sample(Instant time) {
        return "{\"battery\":72,\"speed\":84,\"temperature\":18,\"latitude\":57.7089,\"longitude\":11.9746,\"recordedAt\":\"" + time + "\"}";
    }
}
