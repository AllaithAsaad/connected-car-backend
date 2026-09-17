package com.allaithasaad.connectedcar.telemetry;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    Optional<Telemetry> findFirstByVehicleIdOrderByRecordedAtDescIdDesc(String vehicleId);

    Page<Telemetry> findByVehicleId(String vehicleId, Pageable pageable);

    // One indexed query for the entire fleet, avoiding a query per vehicle.
    @Query(value = "SELECT DISTINCT ON (vehicle_id) * FROM telemetry "
            + "ORDER BY vehicle_id, recorded_at DESC, id DESC", nativeQuery = true)
    List<Telemetry> findLatestForEachVehicle();
}
