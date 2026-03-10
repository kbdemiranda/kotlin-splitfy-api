# Splitfy API

Backend API for managing shared subscriptions, subscriber allocations, billing visibility, and payment confirmation workflows.

## Overview

Splitfy API is a Kotlin + Spring Boot service that helps teams manage:

- Subscription platforms (price, billing cycle, slots)
- Subscribers and their platform associations
- Monthly billing summaries (including currency conversion to BRL)
- Payment confirmation and admin approval flow
- User authentication/authorization with JWT and role-based access

## Main Features

- JWT authentication (`/auth/login`, `/auth/logout`)
- Password reset flow with email delivery (`/auth/forgot-password`, `/auth/reset-password`)
- Role-based authorization (`VIEWER`, `EDITOR`, `ADMIN`)
- CRUD for users, profiles, platforms, and subscribers
- Subscriber-platform association/disassociation
- Monthly billing per subscriber
- Dashboard KPIs and pending payment approvals
- OpenAPI docs available at runtime

## Tech Stack

- Kotlin 2.2.21
- Java 24
- Spring Boot 4.0.2
- Spring Web MVC
- Spring Security (JWT)
- Spring Data JPA
- PostgreSQL (runtime) / H2 (tests)
- Spring Mail + Thymeleaf (HTML email templates)
- springdoc-openapi (Swagger/OpenAPI)
- JUnit 5 + MockK + Mockito Kotlin

## Project Structure

```text
src/main/kotlin/io/github/splitfy/api
├── bootstrap       # startup seed/initialization
├── config          # OpenAPI and app config
├── domain          # entities, enums, converters
├── exception       # custom API exceptions
├── repository      # Spring Data repositories
├── security        # JWT, filters, security config
├── service         # business logic
└── web             # REST controllers + DTOs

src/main/resources
├── application.yml
├── assets             # inline email assets
└── templates/email # HTML email templates
```

## Prerequisites

- JDK 24
- PostgreSQL running locally/remotely
- Maven Wrapper (`./mvnw`, already included)

## Environment Variables

Use the example file as a starting point:

```bash
cp .env.example .env
```

The application expects environment variables (it does not auto-load `.env` by default outside Docker Compose).

Required:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET` (use a strong secret, >= 32 bytes)
- `JWT_EXPIRATION_MS`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `PIX_KEY`
- `PIX_COPY_PASTE`

Optional (recommended):

- `ADMIN_EMAIL`
- `ADMIN_PASSWORD`
- `RESET_TOKEN_EXPIRATION_MINUTES`
- `AUTH_RATE_LIMIT_LOGIN_IP_PER_MINUTE`
- `AUTH_RATE_LIMIT_LOGIN_EMAIL_PER_MINUTE`
- `AUTH_RATE_LIMIT_FORGOT_IP_PER_HOUR`
- `AUTH_RATE_LIMIT_FORGOT_EMAIL_PER_HOUR`
- `BCB_BASE_URL`
- `BCB_LOOKBACK_DAYS`
- `EXCHANGE_FALLBACK_BASE_URL`
- `EXCHANGE_TIMEOUT_CONNECT_MS`
- `EXCHANGE_TIMEOUT_READ_MS`
- `EXCHANGE_RETRY_MAX_ATTEMPTS`
- `EXCHANGE_RETRY_BACKOFF_MS`
- `EXCHANGE_CACHE_TTL_SECONDS`
- `EXCHANGE_CACHE_MAX_STALE_SECONDS`
- `MAIL_RETRY_MAX_ATTEMPTS`
- `MAIL_RETRY_BACKOFF_MS`

Redis note:

- if the API runs locally on your machine and Redis runs through this repository's `docker compose`, use `REDIS_HOST=localhost` and `REDIS_PORT=16379`
- if the API runs inside the `backend` container from `docker-compose.yml`, use `REDIS_HOST=redis` and `REDIS_PORT=6379`

Example:

```bash
export DB_URL="jdbc:postgresql://localhost:5432/splitfy"
export DB_USER="postgres"
export DB_PASSWORD="postgres"

export JWT_SECRET="replace-with-a-strong-secret-at-least-32-bytes"
export JWT_EXPIRATION_MS="3600000"

export ADMIN_EMAIL="admin@splitfy.local"
export ADMIN_PASSWORD="ChangeMe123!"

export MAIL_USERNAME="smtp-user"
export MAIL_PASSWORD="smtp-password"
export MAIL_FROM="Splitfy <no-reply@splitfy.local>"
export PIX_KEY="123.456.789-00"
export PIX_COPY_PASTE="000201010212..."
```

## Running Locally

1. Build:

```bash
./mvnw clean package
```

2. Run:

```bash
./mvnw spring-boot:run
```

3. Run with development seed data (`DataInitializer`):

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Default server port: `8080`

## Running with Docker

There are now two compose flows in this repository:

- `docker-compose.yml`: local development stack
- `docker-compose.prod.yml`: production-style stack

### Development stack

Use this when you want to run backend and frontend locally with hot reload on the Angular app.

Behavior:

- reads backend credentials from `.env`
- builds backend locally from this repository
- builds frontend locally from `../angular-splitfy-frontend` using `Dockerfile.dev`
- enables `SPRING_PROFILES_ACTIVE=dev`
- does not create or update schema automatically

Required `.env` values:

- `DB_URL`
- `JWT_SECRET`
- `JWT_EXPIRATION_MS`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `PIX_KEY`
- `PIX_COPY_PASTE`

Start:

```bash
docker compose up --build -d
```

Logs:

```bash
docker compose logs -f backend
docker compose logs -f frontend
```

Stop:

```bash
docker compose down
```

Endpoints:

- Frontend: `http://localhost:4242`
- API: `http://localhost:9090`
- Swagger: `http://localhost:9090/swagger-ui/index.html`

### Production-style stack

Use this to validate the production Docker images/layout locally, without Angular dev server behavior.

Behavior:

- builds backend with the runtime `Dockerfile`
- builds frontend with the production `Dockerfile`
- exposes backend directly on `http://localhost:8080`
- does **not** enable development schema bootstrap
- reads backend credentials from `.env`

Start:

```bash
docker compose -f docker-compose.prod.yml up --build -d
```

Logs:

```bash
docker compose -f docker-compose.prod.yml logs -f
```

Stop:

```bash
docker compose -f docker-compose.prod.yml down
```

Endpoints:

- Frontend: `http://localhost:4242`
- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`

Important:

- both compose files expect an already provisioned schema; if required tables do not exist, the application should fail on startup/runtime instead of creating them
- `docker-compose.prod.yml` expects an already provisioned schema, because the app default config keeps `spring.jpa.hibernate.ddl-auto=none`
- use `docker-compose.yml` for day-to-day development with the Angular dev server

## Building and Publishing the Image

Build the image locally:

```bash
docker build \
  --build-arg APP_VERSION=0.0.1 \
  -t YOUR_DOCKERHUB_USER/splitfy-api:0.0.1 \
  -t YOUR_DOCKERHUB_USER/splitfy-api:latest .
```

Run the image locally:

```bash
docker run --rm -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://host.docker.internal:25432/splitfy" \
  -e DB_USER="postgres" \
  -e DB_PASSWORD="postgres" \
  -e JWT_SECRET="replace-with-a-strong-secret-at-least-32-bytes" \
  -e JWT_EXPIRATION_MS="3600000" \
  -e MAIL_USERNAME="smtp-user" \
  -e MAIL_PASSWORD="smtp-password" \
  -e MAIL_FROM="Splitfy <no-reply@splitfy.local>" \
  YOUR_DOCKERHUB_USER/splitfy-api:0.0.1
```

Login and publish to Docker Hub:

```bash
docker login
docker push YOUR_DOCKERHUB_USER/splitfy-api:0.0.1
docker push YOUR_DOCKERHUB_USER/splitfy-api:latest
```

If you want to reuse the same image name in Compose, set the service image to the same Docker Hub tag before running the production-style compose.

## Database Notes

- `spring.jpa.hibernate.ddl-auto=none`
- `spring.flyway.enabled=false`
- `spring.sql.init.mode=never`

This means schema creation/migrations are not auto-managed in the default runtime setup. Ensure your PostgreSQL schema/tables already exist before running non-development environments.

## API Documentation

When the app is running:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Authentication and Authorization

### Auth flow

1. `POST /auth/login` with email/password.
2. Receive JWT token.
3. Send `Authorization: Bearer <token>` on protected endpoints.
4. `POST /auth/logout` invalidates token (in-memory blacklist).

### Roles

- `VIEWER`: read endpoints
- `EDITOR`: update/associate subscriber data and send billing emails
- `ADMIN`: full access, including deletes, profile management, and payment approval

## Key Endpoints

### General

- `GET /` - simple health greeting
- `GET /actuator/health` - Spring Boot health endpoint

### Auth

- `POST /auth/login`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`
- `POST /auth/logout`

### Users & Profiles

- `POST /users` (public user creation)
- `GET /users`, `GET /users/{id}`
- `PUT /users/{id}`, `DELETE /users/{id}`
- `PUT /users/{id}/profile/{profileId}`
- `DELETE /users/{id}/profile`
- `GET /profiles`, `GET /profiles/{id}`
- `POST /profiles`, `PUT /profiles/{id}`, `DELETE /profiles/{id}`

### Platforms

- `GET /platforms`, `GET /platforms/{id}`
- `POST /platforms`, `PUT /platforms/{id}`, `DELETE /platforms/{id}`

### Subscribers & Billing

- `GET /subscribers`, `GET /subscribers/{id}`
- `POST /subscribers`, `PUT /subscribers/{id}`, `DELETE /subscribers/{id}`
- `POST /subscribers/{id}/associate`
- `PUT /subscribers/{id}/disassociate`
- `GET /subscribers/{id}/billing?referenceMonth=YYYY-MM`
- `POST /subscribers/billing/email-summary`

### Payments & Dashboard

- `POST /subscribers/payments/confirmations`
- `POST /subscribers/payments/confirmations/{id}/approve`
- `GET /subscribers/payments/confirmations/pending?referenceMonth=YYYY-MM`
- `GET /dashboard/kpis?referenceMonth=YYYY-MM`

## Supported Domain Values

- `ProfileName`: `VIEWER`, `EDITOR`, `ADMIN`
- `Currency`: `BRL`, `USD`, `EUR`
- `BillingCycle`: `MONTHLY`, `SEMI_ANNUAL`, `ANNUAL`
- `PaymentConfirmationStatus`: `PENDING`, `CONFIRMED`

## Testing

Run all tests:

```bash
./mvnw test
```

Tests use H2 in-memory database with `src/test/resources/application.yml`.

## Operational Notes

- Password reset tokens are generated as 6-digit codes and stored as SHA-256 hashes.
- Exchange rates try Brazil Central Bank first, then a fallback provider, with retry + cache.
- Logout token blacklist is in-memory and resets on application restart.
- Actuator currently exposes `health` and `info` endpoints.

## Security Recommendations

- Do not commit real credentials to source control.
- Use a secrets manager or CI/CD secure variables for production.
- Rotate mail/JWT secrets if they were exposed.
- Put API behind HTTPS in any non-local environment.

## License

This project is licensed under a custom proprietary non-commercial license.
See `LICENSE` for details.
