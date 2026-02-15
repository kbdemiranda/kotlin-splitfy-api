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
├── static/docs     # RapiDoc page
├── static/swagger-ui
└── templates/email # HTML email templates
```

## Prerequisites

- JDK 24
- PostgreSQL running locally/remotely
- Maven Wrapper (`./mvnw`, already included)

## Environment Variables

The application expects environment variables (it does not auto-load `.env` by default).

Required:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET` (use a strong secret, >= 32 bytes)
- `JWT_EXPIRATION_MS`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`

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

## Database Notes

- `spring.jpa.hibernate.ddl-auto=none`
- `spring.flyway.enabled=false`
- `spring.sql.init.mode=never`

This means schema creation/migrations are not auto-managed in the current setup. Ensure your PostgreSQL schema/tables already exist before running in non-test environments.

## API Documentation

When the app is running:

- RapiDoc: `http://localhost:8080/docs`
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
