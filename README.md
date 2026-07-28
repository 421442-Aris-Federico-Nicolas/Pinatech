# Computer Store

MVP local de una tienda de productos informaticos y servicio tecnico. El proyecto demuestra una arquitectura de monolito modular con Angular, Spring Boot y PostgreSQL.

## Estado

El MVP funcional esta implementado: autenticacion con roles y refresh tokens rotativos, catalogo, inventario, ordenes y tickets de servicio tecnico. Incluye pruebas unitarias para la logica de autenticacion, stock, transiciones de orden y guards de Angular.

## Stack

| Technology | Version |
|---|---:|
| Java | 21 LTS |
| Spring Boot | 3.5.16 |
| Angular | 21.2.18 |
| Node.js | 24.15.0 |
| PostgreSQL | 17.10 |

## Architecture

```mermaid
flowchart LR
    Angular[Angular :4200] -->|HTTP| API[Spring Boot :8080]
    API --> DB[(PostgreSQL :5432)]
```

See [docs/architecture.md](docs/architecture.md) for the initial architecture decisions.

## Repository structure

```text
computer-store/
├── frontend/       # Angular standalone application
├── backend/        # Spring Boot API
├── docs/           # Technical documentation
├── docker-compose.yml
├── .env.example
├── README.md
└── CONTRIBUTING.md
```

## Prerequisites

- Docker Desktop with Docker Compose.
- Java 21 LTS.
- Node.js 24.15.0 and npm 11.12.1.

Windows Java installation:

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e
java -version
```

Linux (Debian/Ubuntu):

```bash
sudo apt update
sudo apt install openjdk-21-jdk
java -version
```

## Production startup with Docker

Docker Compose builds the Angular storefront, starts PostgreSQL and runs the backend with the `prod` profile. Copy `.env.example` to `.env`, set a strong database password and JWT secret, and set the real HTTPS storefront origin. Startup fails if any required value is absent. PostgreSQL is bound only to loopback and the API is exposed through the frontend reverse proxy.

```bash
docker compose up -d --build
docker compose ps
```

```powershell
docker compose up -d --build
docker compose ps
```

The storefront is available at `http://localhost:${FRONTEND_PORT:-80}` and proxies `/api` to the backend container. The API is also bound to `127.0.0.1:${BACKEND_PORT:-8080}` so the Angular development server can use it without exposing it to the network. Seeder accounts, OpenAPI documents and Swagger UI are disabled in `prod`. Terminate TLS in front of the storefront for a public deployment.

## Manual startup

1. Export the datasource variables shown below, or run PostgreSQL with local credentials of your choice.
2. Start PostgreSQL only:

```bash
docker compose up -d postgres
```

```powershell
docker compose up -d postgres
```

3. Start the backend with the development profile and local datasource variables:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/computer_store SPRING_DATASOURCE_USERNAME=computer_store SPRING_DATASOURCE_PASSWORD=computer_store ./mvnw spring-boot:run
```

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="dev"; $env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/computer_store"; $env:SPRING_DATASOURCE_USERNAME="computer_store"; $env:SPRING_DATASOURCE_PASSWORD="computer_store"; .\mvnw.cmd spring-boot:run
```

4. Start Angular in a second terminal:

```bash
cd frontend
npm install
npm start
```

```powershell
cd frontend
npm install
npm start
```

Open `http://localhost:4200`. The home page checks `GET /api/health` through the real API.

## Database and Flyway

Flyway executes `backend/src/main/resources/db/migration/V1__create_initial_schema.sql` automatically from an empty database. Hibernate is configured with `ddl-auto: validate`; it never creates or changes the schema.

## Order reservations

`POST /api/orders` accepts an optional `Idempotency-Key` header (1-100 characters). The storefront creates and persists this key while a checkout attempt is pending. Repeating the same key for the same user and payload returns the original order; reusing it with another payload returns `409`. Omitting the header preserves compatibility for other API clients.

Stock is reserved for `ORDER_RESERVATION_TTL` (15 minutes by default) while an order is `PENDING_PAYMENT`. Expired reservations are cancelled and released automatically. A manual `PAID` state stops expiry; cancellation from `PENDING_PAYMENT` or `PAID` releases stock, and transition to `PREPARING` consumes it. Every operation writes an inventory movement.

To reset local data, stop and remove the PostgreSQL volume:

```bash
docker compose down -v
docker compose up -d --build
```

```powershell
docker compose down -v
docker compose up -d --build
```

## Verification

Backend:

```bash
cd backend
./mvnw clean verify
```

Windows:

```powershell
cd backend
.\mvnw.cmd clean verify
```

Frontend:

```bash
cd frontend
npm install
npm test -- --watch=false
npm run build
```

## Security baseline

- No real credentials or secrets are versioned.
- CORS permits only `http://localhost:4200` by default.
- Catalog, health y autenticacion son publicos; los demas endpoints requieren autenticacion y el stock exacto solo esta disponible para administradores y tecnicos.
- Error responses use RFC 9457 `ProblemDetail` without stack traces.
- Access tokens de corta duracion se mantienen solo en memoria; el frontend renueva la sesion una vez ante un 401 usando refresh tokens rotativos en cookies `HttpOnly`.
- Login y refresh tienen limites de intentos en memoria configurables mediante `AUTH_MAX_LOGIN_ATTEMPTS`, `AUTH_MAX_REFRESH_ATTEMPTS` y `AUTH_RATE_LIMIT_WINDOW_MS`.

## Development users

These accounts are seeded only in the `dev` profile. Do not use their passwords outside local development.

| Role | Email | Password |
|---|---|---|
| Administrator | `admin@computerstore.local` | `Admin123!` |
| Technician | `technician@computerstore.local` | `Technician123!` |
| Customer | `customer@computerstore.local` | `Customer123!` |

## Architecture decisions

- A modular monolith reduces operational complexity for the MVP.
- Backend code is organized by feature.
- PostgreSQL fits the relational domain.
- Flyway owns schema changes.
- Los precios se recalculan siempre en el backend.
- Las ordenes retienen snapshots de nombre y precio del producto.
- Los movimientos de inventario retienen trazabilidad.
- Spring Security protects API endpoints; Angular guards are not a security boundary.
- Las contrasenas se almacenan usando BCrypt y los refresh tokens se almacenan como hashes SHA-256.

## Roadmap

- Etapa 2: authentication, roles and JWT refresh flow. Implementada.
- Etapa 3: catalog, products, inventory and seed data. Implementada.
- Etapa 4: cart and order management. Implementada.
- Etapa 5: technical service tickets. Implementada.
- Etapa 6: ampliar pruebas de integracion, linting y pulido visual. En progreso.

## Screenshots

Pendiente de incorporar capturas actualizadas del catalogo, administracion y servicio tecnico.

## Team

- Pendiente de completar por el equipo del proyecto.
