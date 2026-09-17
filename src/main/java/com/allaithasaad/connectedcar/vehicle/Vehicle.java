package com.allaithasaad.connectedcar.vehicle;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id
    private String id;
    private Instant lastSeenAt;

    protected Vehicle() {}

    public String getId() { return id; }
    public Instant getLastSeenAt() { return lastSeenAt; }

    public void seenAt(Instant time) {
        if (lastSeenAt == null || time.isAfter(lastSeenAt)) {
            lastSeenAt = time;
        }
    }
}
