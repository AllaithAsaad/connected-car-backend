package com.allaithasaad.connectedcar.vehicle;

import com.allaithasaad.connectedcar.telemetry.TelemetryResponse;
import java.time.Duration;
import java.time.Instant;

public record VehicleResponse(String id, Status status, Instant lastSeenAt, TelemetryResponse latestTelemetry) {
    public enum Status { ONLINE, OFFLINE }

    public static VehicleResponse from(Vehicle vehicle, TelemetryResponse latest, Instant now, Duration timeout) {
        Instant lastSeen = vehicle.getLastSeenAt();
        Status status = lastSeen != null && lastSeen.isAfter(now.minus(timeout)) ? Status.ONLINE : Status.OFFLINE;
        return new VehicleResponse(vehicle.getId(), status, lastSeen, latest);
    }
}
