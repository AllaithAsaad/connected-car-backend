# Connected Car Backend – svensk snabbstart

Tre virtuella bilar (`VOLVO-001`, `VOLVO-002`, `VOLVO-003`) skickar batterinivå, hastighet, temperatur och position till ett Java-API var femte sekund. Alla mätningar sparas i PostgreSQL. Projektet använder Java 21, Spring Boot, Spring Data JPA, Flyway och Docker.

## Starta

Installera Docker med Compose (exempelvis Docker Desktop), öppna terminalen och kör:

```bash
git clone https://github.com/AllaithAsaad/connected-car-backend.git
cd connected-car-backend
cp .env.example .env
docker compose up --build -d --wait
```

På Windows PowerShell: byt kopieringskommandot mot `Copy-Item .env.example .env`.

Första starten hämtar beroenden och bygger containrarna. Därefter startar databasen, servern och simulatorn automatiskt.

```bash
curl http://localhost:8080/api/vehicles
curl http://localhost:8080/api/vehicles/VOLVO-001
curl 'http://localhost:8080/api/vehicles/VOLVO-001/telemetry?page=0&size=10'
```

Du kan också öppna API-adresserna direkt i webbläsaren. Svaret är JSON.

## Visa hur status fungerar

```bash
docker compose logs -f simulator
```

Avsluta loggvisningen med Ctrl+C. Kör sedan `docker compose stop simulator` och vänta 20 sekunder: bilarna blir `OFFLINE`, men deras senaste mätning och historik finns kvar. Kör `docker compose start simulator` för att återuppta sändningen.

`docker compose down` stoppar tjänsterna och behåller datan. `docker compose down -v` raderar även demodatabasen.

## Tester och vidareutveckling

Med JDK 21 eller senare: kör `./mvnw verify` (`mvnw.cmd verify` på Windows). Testerna startar en riktig PostgreSQL-process automatiskt och kräver inte Docker.

Se [README](README.md) för lokal Java-körning och konfiguration, [API-dokumentation](docs/API.md) för JSON-format och [arkitekturen](docs/ARCHITECTURE.md) för hur delarna hänger ihop. API:t saknar autentisering och är avsett att köras lokalt som ett portföljprojekt.
