package com.allaithasaad.connectedcar.vehicle;

import com.allaithasaad.connectedcar.api.PageResponse;
import com.allaithasaad.connectedcar.telemetry.Telemetry;
import com.allaithasaad.connectedcar.telemetry.TelemetryRepository;
import com.allaithasaad.connectedcar.telemetry.TelemetryRequest;
import com.allaithasaad.connectedcar.telemetry.TelemetryResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class VehicleService {
    private final VehicleRepository vehicles;
    private final TelemetryRepository telemetry;
    private final Clock clock;
    private final Duration offlineAfter;

    public VehicleService(VehicleRepository vehicles, TelemetryRepository telemetry, Clock clock,
            @Value("${app.offline-after}") Duration offlineAfter) {
        if (offlineAfter.isNegative() || offlineAfter.isZero()) {
            throw new IllegalArgumentException("app.offline-after must be positive");
        }
        this.vehicles = vehicles;
        this.telemetry = telemetry;
        this.clock = clock;
        this.offlineAfter = offlineAfter;
    }

    public List<VehicleResponse> list() {
        Map<String, TelemetryResponse> latest = telemetry.findLatestForEachVehicle().stream()
                .map(TelemetryResponse::from)
                .collect(Collectors.toMap(TelemetryResponse::vehicleId, Function.identity()));
        Instant now = clock.instant();
        return vehicles.findAll(Sort.by("id")).stream()
                .map(v -> VehicleResponse.from(v, latest.get(v.getId()), now, offlineAfter)).toList();
    }

    public VehicleResponse get(String id) {
        Vehicle vehicle = requireVehicle(id);
        TelemetryResponse latest = telemetry.findFirstByVehicleIdOrderByRecordedAtDescIdDesc(id)
                .map(TelemetryResponse::from).orElse(null);
        return VehicleResponse.from(vehicle, latest, clock.instant(), offlineAfter);
    }

    public PageResponse<TelemetryResponse> history(String id, int page, int size) {
        requireVehicle(id);
        var paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt", "id"));
        return PageResponse.from(telemetry.findByVehicleId(id, paging).map(TelemetryResponse::from));
    }

    @Transactional
    public TelemetryResponse ingest(String id, TelemetryRequest request) {
        // Serialize writers for one vehicle so lastSeenAt cannot move backwards.
        Vehicle vehicle = vehicles.findForUpdate(id).orElseThrow(() -> notFound(id));
        Instant receivedAt = clock.instant();
        Telemetry saved = telemetry.save(new Telemetry(id, request, receivedAt));
        vehicle.seenAt(receivedAt);
        return TelemetryResponse.from(saved);
    }

    private Vehicle requireVehicle(String id) {
        return vehicles.findById(id).orElseThrow(() -> notFound(id));
    }

    private ResponseStatusException notFound(String id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle '" + id + "' was not found");
    }
}
