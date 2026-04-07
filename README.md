# RouteSense

RouteSense is a full-stack web application built as a Final Year Project. It explores journey efficiency and environmental impact for public transport in Ireland (Galway focus), letting users search routes, compare CO₂ emissions against driving, and track their travel history.

## Project Structure

```text
RouteSense/
├── backend/    – Spring Boot (Java 17) REST API
└── frontend/   – React 18 SPA built with Vite
```

## Tech Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3.5.11, Maven |
| Frontend | React 18.2.0, Vite 5.0.8 |
| Maps | Leaflet 1.9.4, react-leaflet 4.2.1 |
| Transit data | GTFS flat files (loaded in-memory at startup) |
| Distance API | OpenRouteService (optional, falls back to haversine) |

## Architecture

The backend follows Clean Architecture with four layers:

- **`domain/`** — core entities: `Stop`, `TransportStop`, `TransportMode`, `StopEdge`, `JourneyOption`, `JourneyLeg`, `Departure`, `JourneyRecord`
- **`application/`** — use cases: `SearchJourneyUseCase`, `GetMapStopsUseCase`, `GetStopDeparturesUseCase`, `SearchStopsUseCase`, `SaveJourneyUseCase`, `GetJourneyHistoryUseCase`; ports: `StopGraphRepository`, `MapDataSource`, `JourneyHistoryRepository`
- **`infrastructure/`** — `GtfsGraphLoader`, `InMemoryStopGraphRepository`, `InMemoryMapDataSource`, `JpaJourneyHistoryAdapter`, `OpenRouteServiceClient`, `EmissionsCalculator`
- **`web/`** — REST controllers, DTOs, CORS config

## API Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/health` | Health check |
| `POST` | `/api/journeys/search` | Journey search (Dijkstra routing) |
| `POST` | `/api/journeys/save` | Save a completed journey to history |
| `GET` | `/api/journeys/history` | Retrieve saved journey history |
| `GET` | `/api/map/stops` | Map stops filtered by location/mode/live |
| `GET` | `/api/map/stops/{id}/departures` | Departures for a specific stop |
| `GET` | `/api/stops/search` | Stop autocomplete by name |

## Routing Algorithm

Journey search uses Dijkstra pathfinding on an in-memory graph built from GTFS data. Each option is scored using a weighted formula:

```text
Score = TIME (55%) + TRANSFERS (25%) + CO₂ (20%)
```

Multiple options are returned with a recommended option and reasoning.

## CO₂ Emissions Reference

| Mode | g CO₂ / km |
| --- | --- |
| Walk | 0 |
| Bike | 8 |
| Tram / LUAS | 35 |
| Train | 45 |
| Bus | 105 |
| Car (baseline) | 170 |

## Frontend Pages

| Page | Description |
| --- | --- |
| **Login** | Client-side authentication stored in `localStorage` |
| **Home** | KPI cards showing CO₂ saved, journey duration, and mode for the last selected journey |
| **Map** | Interactive Leaflet map with stop markers, mode filtering, and live/static toggle |
| **Routes** | Stop search, journey comparison table with CO₂ savings vs driving |
| **History** | Personal travel log — bar charts for journeys per day and CO₂ saved, mode breakdown, recent journeys list |

## Running the Project

### Prerequisites

- Java 17+
- Maven 3.6+
- Node.js 18+

### Backend

```bash
cd backend
mvn spring-boot:run
```

The backend starts on **port 8080**. On first run, GTFS data is loaded into memory and an H2 database file is created at `./data/routesense`.

To use PostgreSQL instead of H2, uncomment the PostgreSQL block in [application.properties](backend/src/main/resources/application.properties) and comment out the H2 block.

An optional OpenRouteService API key can be set in `application.properties` to improve car distance estimates for CO₂ comparisons. Leave it blank to use the built-in haversine fallback.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend runs on **port 5173** (Vite default). Open [http://localhost:5173](http://localhost:5173) in your browser.

CORS is pre-configured for `localhost:5173–5176`.

### Default Login

Authentication is client-side only (localStorage). No backend account setup is required — use any credentials accepted by the login page.

## Development Approach

The project is developed iteratively using Agile principles with vertical slices spanning frontend and backend. Each slice delivers a complete, runnable feature end-to-end.
