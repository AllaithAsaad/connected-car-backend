package com.allaithasaad.connectedcar.telemetry;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "telemetry")
public class Telemetry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String vehicleId;
    private double battery;
    private double speed;
    private double temperature;
    private double latitude;
    private double longitude;
    private Instant recordedAt;
    private Instant receivedAt;

    protected Telemetry() {}

    public Telemetry(String vehicleId, TelemetryRequest request, Instant receivedAt) {
        this.vehicleId = vehicleId;
        this.battery = request.battery();
        this.speed = request.speed();
        this.temperature = request.temperature();
        this.latitude = request.latitude();
        this.longitude = request.longitude();
        this.recordedAt = request.recordedAt();
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getVehicleId() { return vehicleId; }
    public double getBattery() { return battery; }
    public double getSpeed() { return speed; }
    public double getTemperature() { return temperature; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
