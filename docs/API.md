# REST API

Base URL: `http://localhost:8080`. All timestamps are UTC ISO-8601. All telemetry is fictional.

## Submit telemetry

```bash
curl -i -X POST http://localhost:8080/api/vehicles/VOLVO-001/telemetry \
  -H 'Content-Type: application/json' \
  -d '{"battery":72,"speed":84,"temperature":18,"latitude":57.7089,"longitude":11.9746,"recordedAt":"2026-01-01T12:00:00Z"}'
```

For a live measurement, replace `recordedAt` with the current UTC timestamp. Older measurements are accepted and stored in their chronological position.

| Required field | Unit | Accepted range |
| --- | --- | --- |
| `battery` | percent | 0–100 |
| `speed` | km/h | 0–300 |
| `temperature` | °C | −80–100 |
| `latitude` | degrees | −90–90 |
| `longitude` | degrees | −180–180 |
| `recordedAt` | UTC timestamp | Past or present |

Successful requests return `201 Created` and the saved reading with `id`, `vehicleId`, all submitted fields and server-generated `receivedAt`. The vehicle must already exist. The API derives status from contact time; clients do not submit `ONLINE`/`OFFLINE`.

## Read the fleet

`GET /api/vehicles` returns a JSON array ordered by vehicle ID. `GET /api/vehicles/{id}` returns one object:

```json
{
  "id": "VOLVO-001",
  "status": "OFFLINE",
  "lastSeenAt": null,
  "latestTelemetry": null
}
```

This is the state before the first sample. Afterwards `latestTelemetry` contains the newest reading by `recordedAt`, with record ID breaking ties. `lastSeenAt` is server contact time. Late-arriving older readings are retained without replacing the latest measurement. A recent contact can make the vehicle ONLINE even when that contact contains an older measurement; clients should inspect `recordedAt` for measurement age.

## Read history

`GET /api/vehicles/VOLVO-001/telemetry?page=0&size=20`

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`content` contains telemetry objects, ordered by `recordedAt DESC, id DESC`. Page numbers start at zero. `size` must be 1–100 and `page` must be 0–1,000,000. A page beyond available history has an empty `content`. Offset pagination can shift while new data arrives; it is not a snapshot or a cursor stream.

## Errors

- `400 Bad Request`: invalid fields, malformed JSON, future timestamp, or invalid pagination.
- `404 Not Found`: unknown vehicle, including when submitting or reading its history.
- `405 Method Not Allowed`: unsupported HTTP method.
- `415 Unsupported Media Type`: POST body not sent as JSON.

Errors use `application/problem+json`. Example validation response:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "One or more telemetry fields are invalid",
  "instance": "/api/vehicles/VOLVO-001/telemetry",
  "errors": {"battery": "must be less than or equal to 100"}
}
```

## Health

`GET /actuator/health` returns `200` with `{"status":"UP"}` when the app and database are healthy; an unavailable database yields a non-success response. Only the health actuator endpoint is exposed, without infrastructure details.
