"""Check the running Docker stack using only Python's standard library."""
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = os.getenv("API_BASE_URL", "http://localhost:8080")


def get(path):
    with urllib.request.urlopen(BASE + path, timeout=5) as response:
        return json.load(response)


def main():
    persisted = "--persisted" in sys.argv
    deadline = time.monotonic() + 60
    while True:
        fleet = get("/api/vehicles")
        if len(fleet) == 3 and all(car["latestTelemetry"] for car in fleet):
            if persisted or all(car["status"] == "ONLINE" for car in fleet):
                break
        if time.monotonic() > deadline:
            raise AssertionError(f"Simulator did not populate the fleet: {fleet}")
        time.sleep(1)

    counts = {}
    for car in fleet:
        history = get(f"/api/vehicles/{car['id']}/telemetry?size=2")
        assert history["totalElements"] >= 1, history
        assert all(row["vehicleId"] == car["id"] for row in history["content"])
        counts[car["id"]] = history["totalElements"]

    if persisted:
        # Simulator has stopped; after the contact timeout the same persisted readings remain.
        deadline = time.monotonic() + 35
        while not all(car["status"] == "OFFLINE" for car in get("/api/vehicles")):
            assert time.monotonic() < deadline, "Vehicles never went OFFLINE"
            time.sleep(1)
        print("PASS: PostgreSQL history survived restart; stopped cars are OFFLINE")
        return

    deadline = time.monotonic() + 30
    while True:
        if all(get(f"/api/vehicles/{car}/telemetry")["totalElements"] > count for car, count in counts.items()):
            break
        assert time.monotonic() < deadline, "Telemetry did not grow for all three cars"
        time.sleep(1)

    for path, status in [("/api/vehicles/MISSING", 404), ("/api/vehicles/VOLVO-001/telemetry?size=101", 400)]:
        try:
            get(path)
        except urllib.error.HTTPError as error:
            assert error.code == status
        else:
            raise AssertionError(f"Expected HTTP {status} for {path}")
    print("PASS: all three cars ONLINE, telemetry increasing, API errors correct")


if __name__ == "__main__":
    main()
