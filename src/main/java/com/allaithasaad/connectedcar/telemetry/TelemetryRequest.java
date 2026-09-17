package com.allaithasaad.connectedcar.telemetry;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.Instant;

public record TelemetryRequest(
        @NotNull @DecimalMin("0") @DecimalMax("100") Double battery,
        @NotNull @DecimalMin("0") @DecimalMax("300") Double speed,
        @NotNull @DecimalMin("-80") @DecimalMax("100") Double temperature,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @PastOrPresent Instant recordedAt) {}
