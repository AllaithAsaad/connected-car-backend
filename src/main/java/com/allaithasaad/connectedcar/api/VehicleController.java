package com.allaithasaad.connectedcar.api;

import com.allaithasaad.connectedcar.telemetry.TelemetryRequest;
import com.allaithasaad.connectedcar.telemetry.TelemetryResponse;
import com.allaithasaad.connectedcar.vehicle.VehicleResponse;
import com.allaithasaad.connectedcar.vehicle.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
    private final VehicleService service;

    public VehicleController(VehicleService service) { this.service = service; }

    @GetMapping
    public List<VehicleResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable String id) { return service.get(id); }

    @GetMapping("/{id}/telemetry")
    public PageResponse<TelemetryResponse> history(@PathVariable String id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.history(id, page, size);
    }

    @PostMapping("/{id}/telemetry")
    @ResponseStatus(HttpStatus.CREATED)
    public TelemetryResponse ingest(@PathVariable String id, @Valid @RequestBody TelemetryRequest request) {
        return service.ingest(id, request);
    }
}
