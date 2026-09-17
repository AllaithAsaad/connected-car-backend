CREATE TABLE vehicles (
    id VARCHAR(32) PRIMARY KEY,
    last_seen_at TIMESTAMPTZ
);

CREATE TABLE telemetry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vehicle_id VARCHAR(32) NOT NULL REFERENCES vehicles(id),
    battery DOUBLE PRECISION NOT NULL CHECK (battery >= 0 AND battery <= 100),
    speed DOUBLE PRECISION NOT NULL CHECK (speed >= 0 AND speed <= 300),
    temperature DOUBLE PRECISION NOT NULL CHECK (temperature >= -80 AND temperature <= 100),
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude >= -90 AND latitude <= 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude >= -180 AND longitude <= 180),
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX telemetry_vehicle_recorded_idx ON telemetry (vehicle_id, recorded_at DESC, id DESC);

INSERT INTO vehicles (id) VALUES ('VOLVO-001'), ('VOLVO-002'), ('VOLVO-003');
