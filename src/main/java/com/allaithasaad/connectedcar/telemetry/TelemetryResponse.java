package com.allaithasaad.connectedcar.telemetry;

import java.time.Instant;

public record TelemetryResponse(Long id, String vehicleId, double battery, double speed,
        double temperature, double latitude, double longitude, Instant recordedAt, Instant receivedAt) {
    public static TelemetryResponse from(Telemetry t) {
        return new TelemetryResponse(t.getId(), t.getVehicleId(), t.getBattery(), t.getSpeed(),
                t.getTemperature(), t.getLatitude(), t.getLongitude(), t.getRecordedAt(), t.getReceivedAt());
    }
}
