import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Three fictional vehicles near Gothenburg. Requires only Java 21+. */
public class VehicleSimulator {
    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws InterruptedException {
        String baseUrl = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080").replaceAll("/+$", "");
        int interval = Integer.parseInt(System.getenv().getOrDefault("SIMULATOR_INTERVAL_SECONDS", "5"));
        int maxTicks = Integer.parseInt(System.getenv().getOrDefault("SIMULATOR_MAX_TICKS", "0"));
        if (interval < 1 || maxTicks < 0) throw new IllegalArgumentException("Interval must be positive; max ticks must be >= 0");
        var cars = List.of(new Car("VOLVO-001", 72, 57.7089, 11.9746, 0),
                new Car("VOLVO-002", 88, 57.7120, 11.9600, 2),
                new Car("VOLVO-003", 54, 57.6990, 11.9900, 4));
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            System.out.printf("Simulating %d cars → %s, every %ds%n", cars.size(), baseUrl, interval);
            for (int tick = 0; maxTicks == 0 || tick < maxTicks; tick++) {
                long started = System.nanoTime();
                for (Car car : cars) {
                    car.advance(interval);
                    var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/vehicles/" + car.id + "/telemetry"))
                            .timeout(Duration.ofSeconds(2)).header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(car.json())).build();
                    try {
                        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 201) {
                            System.out.printf(Locale.ROOT, "%s %s battery=%.1f%% speed=%.1f km/h temp=%.1f°C%n",
                                    Instant.now(), car.id, car.battery, car.speed, car.temperature);
                        } else {
                            System.err.printf("%s rejected: HTTP %d %s%n", car.id, response.statusCode(), response.body());
                        }
                    } catch (java.io.IOException exception) {
                        System.err.printf("%s: server unavailable; next sample in %ds (%s)%n",
                                car.id, interval, exception.getClass().getSimpleName());
                    }
                }
                long remaining = interval * 1000L - (System.nanoTime() - started) / 1_000_000;
                if (remaining > 0 && (maxTicks == 0 || tick + 1 < maxTicks)) Thread.sleep(remaining);
            }
        }
    }

    private static final class Car {
        final String id;
        final double centerLat;
        final double centerLon;
        double angle;
        double battery;
        double speed = 50;
        double temperature = 18;
        double latitude;
        double longitude;

        Car(String id, double battery, double latitude, double longitude, double phase) {
            this.id = id;
            this.battery = battery;
            this.centerLat = latitude;
            this.centerLon = longitude;
            this.angle = phase;
        }

        void advance(int seconds) {
            // Synthetic circular routes; GPS points do not represent real road navigation.
            speed = Math.max(0, Math.min(110, speed + RANDOM.nextDouble(-12, 12)));
            battery = Math.max(0, battery - speed * seconds / 180000.0);
            if (battery < 15) battery = 95; // Simulated charging stop.
            temperature = Math.max(12, Math.min(25, temperature + RANDOM.nextDouble(-0.5, 0.5)));
            angle += speed / 3.6 * seconds / 1200;
            latitude = centerLat + 0.0108 * Math.sin(angle);
            longitude = centerLon + 0.0202 * Math.cos(angle);
        }

        String json() {
            return String.format(Locale.ROOT,
                    "{\"battery\":%.2f,\"speed\":%.2f,\"temperature\":%.2f,\"latitude\":%.6f,\"longitude\":%.6f,\"recordedAt\":\"%s\"}",
                    battery, speed, temperature, latitude, longitude, Instant.now());
        }
    }
}
