# Spring Car Rental API

Backend REST API for a car rental platform: vehicle catalogue, reservations, rentals,
invoicing, discounts, reviews, notifications, and role-based administration.

- **Application name:** `spring_car_rental_system` (`spring.application.name`)
- **Base URL (local):** `http://localhost:8080`
- **Group / Artifact:** `com.example:spring_boot_project_api:0.0.1-SNAPSHOT`

---

## Table of contents

1. [Features](#features)
2. [Technology stack](#technology-stack)
3. [Architecture](#architecture)
4. [Project structure](#project-structure)
5. [Prerequisites](#prerequisites)
6. [Installation](#installation)
7. [Environment variables](#environment-variables)
8. [Database configuration](#database-configuration)
9. [Running the application](#running-the-application)
10. [Authentication & authorization](#authentication--authorization)
11. [API reference](#api-reference)
12. [Core business logic](#core-business-logic)
13. [Server-side integrations](#server-side-integrations)
14. [Validation & error handling](#validation--error-handling)
15. [Audit logging & login history](#audit-logging--login-history)
16. [Swagger / OpenAPI](#swagger--openapi)
17. [Testing](#testing)
18. [Troubleshooting](#troubleshooting)
19. [Contributing](#contributing)
20. [License](#license)

---

## Features

**Identity & access**

- Email/password registration and login returning a JWT
- Stateless JWT authentication (Bearer header), HS-signed, 24 h default expiry
- BCrypt password hashing
- OAuth2 login with **Google** and **Facebook** (JWT returned via redirect)
- **Telegram Login Widget** verification (HMAC-SHA256, replay-protected)
- Password reset flow (15-minute single-use token delivered by email)
- Profile read/update, password change, personal login history
- Four roles: `ADMIN`, `MANAGER`, `STAFF`, `CUSTOMER` enforced with `@PreAuthorize`
- Account activation/deactivation and role assignment by administrators

**Catalogue & booking**

- Vehicles with brand, specs, pricing, status; dynamic filtering
  (`brandId`, `type`, `transmission`, `fuelType`, `minPrice`, `maxPrice`, `seats`) + pagination
- Vehicle images, brands, rental locations, add-on services, site settings
- Reservations with overlap detection, server-side price calculation, promo codes
- Automatic `Rental` + `Invoice` creation for customer bookings
- Rental lifecycle (`PENDING → CONFIRMED/PICKED_UP → ACTIVE → RETURNED → COMPLETED`)
- Vehicle inspections at pick-up/return, maintenance records, favourites
- Reviews with rating counts and staff-controlled visibility

**Commerce**

- Discounts (`PERCENTAGE`, `FIXED_AMOUNT`) with validity window and usage cap
- Discount usage tracking per customer
- Invoices with subtotal/discount/tax/late-fee/add-ons breakdown
- Invoice PDF generation (OpenPDF, A4)
- Bakong **KHQR** payment QR generation, PNG rendering, and transaction verification

**Platform**

- Push notifications via **Firebase Cloud Messaging** + in-app notification inbox
- Image hosting via **Cloudinary**; local multipart uploads to `uploads/`
- SMTP password-reset emails
- AOP audit trail of all mutating REST calls (CREATE/UPDATE/DELETE)
- Login/logout history with IP and user agent
- CORS restricted to configured origins
- Optional ngrok tunnel for local development
- Data seeder that inserts four default add-on services on first boot

---

## Technology stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot **4.0.8-SNAPSHOT** (parent POM) |
| Web | `spring-boot-starter-web` (Servlet stack) |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate) + PostgreSQL driver |
| Database | PostgreSQL (dialect `PostgreSQLDialect`; MySQL driver also on the classpath, see [Troubleshooting](#troubleshooting)) |
| Security | `spring-boot-starter-security`, `spring-security-crypto` (BCrypt), JJWT 0.12.6 |
| OAuth2 | `spring-boot-starter-oauth2-client` (Google, Facebook) |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| API docs | `springdoc-openapi-starter-webmvc-ui` 3.0.2 (Swagger UI) |
| Push notifications | `firebase-admin` 9.7.0 |
| Payments | `kh.gov.nbc.bakong_khqr:sdk-java` 1.0.0.16 + ZXing 3.5.3 (PNG rendering) |
| File hosting | `cloudinary-http44` 1.39.0 |
| PDF | `openpdf` 1.3.42 |
| Email | `spring-boot-starter-mail` (Gmail SMTP, port 587, STARTTLS) |
| AOP | `spring-aop` + `aspectjweaver` |
| Config | `java-dotenv` 5.2.2 + `spring.config.import=optional:file:.env[.properties]` |
| Dev tooling | `spring-boot-devtools`, `java-ngrok` 3.1.2 |
| Build | Maven Wrapper (`mvnw` / `mvnw.cmd`) |
| Tooling | Lombok |
| Container | Docker (multi-stage Maven → Temurin 21 JRE Alpine) |

---

## Architecture

The API follows a classic layered architecture with method-level security applied at
the controller boundary and cross-cutting concerns implemented as AOP aspects and
servlet filters.

```mermaid
flowchart TB
    Client["Client (SPA / mobile)"]

    subgraph Security["Security layer"]
        CORS["CORS configuration"]
        JWTF["JwtAuthFilter (Bearer token)"]
        OAuth["OAuth2 login handlers"]
        MS["@EnableMethodSecurity / @PreAuthorize"]
    end

    subgraph API["REST layer"]
        C1["Controllers<br/>(29 @RestController)"]
    end

    subgraph Business["Service layer"]
        S1["Service interfaces"]
        S2["ServiceImpl<br/>(business rules, transactions)"]
    end

    subgraph Data["Persistence layer"]
        R1["Spring Data repositories"]
        JPA["Hibernate / JPA"]
        DB[("PostgreSQL")]
    end

    subgraph Cross["Cross-cutting concerns"]
        AOP["AuditLoggingAspect"]
        EH["GlobalExceptionHandler"]
        EV["AuthenticationEventListener<br/>(login history)"]
    end

    subgraph Ext["External services"]
        FCM["Firebase Cloud Messaging"]
        BAK["Bakong KHQR API"]
        CLOUD["Cloudinary"]
        SMTP["Gmail SMTP"]
        OAUTH["Google / Facebook / Telegram"]
    end

    Client --> CORS --> JWTF --> C1
    JWTF --> MS
    OAuth --> C1
    C1 --> S1 --> S2 --> R1 --> JPA --> DB
    C1 -.-> AOP
    C1 -.-> EH
    JWTF -.-> EV
    S2 --> FCM
    S2 --> BAK
    S2 --> CLOUD
    S2 --> SMTP
```

**Conventions**

| Concern | Where it lives |
|---|---|
| URL routing + authorization | `controller/*Controller.java` (`@RequestMapping`, `@PreAuthorize`) |
| Business rules, transactions | `service/*Service.java` interfaces → `service/impl/*ServiceImpl.java` |
| Queries | `repository/*Repository.java` (Spring Data JPA, `JpaSpecificationExecutor` for vehicle search) |
| Persistence model | `model/*.java` (`@Entity`, `tb_*` tables) |
| API contract | `dto/request/**`, `dto/response/**` |
| Object mapping | `mapper/BrandMapper.java` (manual mapping elsewhere) |
| Enums | `enums/*.java` (stored as `STRING`) |
| Security setup | `config/SecurityConfig.java`, `config/JwtAuthFilter.java` |
| OAuth2/Telegram | `config/CustomOAuth2UserService.java`, `config/OAuth2Authentication*.java`, `service/TelegramAuthService.java` |
| Bootstrap/config beans | `config/*.java` (`FirebaseConfig`, `CloudinaryConfig`, `OpenApiConfig`, `DataSeeder`, `NgrokTunnelRunner`) |
| Exception handling | `exception/GlobalExceptionHandler.java` |
| Utilities | `util/JwtUtil.java`, `util/AuditLogContext.java`, `util/ClientInfoUtil.java` |
| Specifications | `specification/VehicleSpecification.java` |

---

## Project structure

```text
spring_backend/
├── pom.xml                      # Maven build, dependencies, Java 21
├── mvnw / mvnw.cmd              # Maven wrapper
├── Dockerfile                   # Multi-stage build → non-root JRE image
├── deploy/
│   ├── docker-compose.yml       # backend + frontend + database stack
│   ├── .env.example             # template for deploy/.env
│   └── DEPLOY_CHECKLIST.md
├── .env.example                 # template for local .env (copy → .env)
├── uploads/                     # local multipart upload target (file.upload-dir)
└── src/
    ├── main/
    │   ├── java/com/example/spring_boot_project_api/
    │   │   ├── SpringBootProjectApiApplication.java
    │   │   ├── config/          # Security, CORS, OAuth2, Firebase, Cloudinary, OpenAPI, seeder, ngrok
    │   │   ├── controller/      # 29 REST controllers
    │   │   ├── service/         # service interfaces
    │   │   │   └── impl/        # implementations + CustomUserDetails
    │   │   ├── repository/      # Spring Data JPA repositories (26)
    │   │   ├── model/           # JPA entities (26)
    │   │   ├── dto/request/     # inbound DTOs with Bean Validation
    │   │   ├── dto/response/    # outbound DTOs
    │   │   ├── enums/           # 19 enums
    │   │   ├── specification/   # JPA Specifications (vehicle search)
    │   │   ├── mapper/          # object mappers
    │   │   ├── exception/       # @RestControllerAdvice
    │   │   └── util/            # JwtUtil, AuditLogContext, ClientInfoUtil
    │   └── resources/
    │       └── application.properties
    └── test/java/               # test source root (currently empty — see [Testing](#testing))
```

---

## Prerequisites

| Requirement | Version / notes |
|---|---|
| JDK | **21** (`java.version` in `pom.xml`) |
| Maven | Not required — use the bundled wrapper (`./mvnw`) |
| PostgreSQL | Any instance reachable from the process (local or hosted, e.g. Neon) |
| Node/other frontend tooling | **Not required** for this backend |
| Accounts (optional but needed for full functionality) | Cloudinary, Gmail app password, Google/Facebook OAuth2 apps, Firebase project, Bakong merchant account, Telegram bot, ngrok |

---

## Installation

```bash
git clone https://github.com/ETEC-Spring-Final/spring_backend.git
cd spring_backend
cp .env.example .env      # then fill in real values — .env is gitignored
```

`.env` is loaded automatically by:

```properties
spring.config.import=optional:file:.env[.properties]
```

> **Never commit `.env`.** It is already listed in `.gitignore`. No secrets, tokens,
> or private keys are stored in this repository.

---

## Environment variables

All secrets are supplied through environment variables (or the project-root `.env`).
The table below lists **every** variable referenced by
[`src/main/resources/application.properties`](src/main/resources/application.properties).

| Variable | Purpose | Default in `application.properties` |
|---|---|---|
| `DB_URL` | JDBC connection string, e.g. `jdbc:postgresql://host:5432/dbname` | none (required) |
| `DB_USER` | Database username | none (required) |
| `DB_PASS` | Database password | none (required) |
| `DB_HIKARI_MAX_LIFETIME` | HikariCP max lifetime (ms) | none |
| `DB_HIKARI_IDLE_TIMEOUT` | HikariCP idle timeout (ms) | none |
| `DB_HIKARI_KEEPALIVE_TIME` | HikariCP keepalive (ms) | none |
| `DB_HIKARI_CONNECTION_TIMEOUT` | HikariCP connection timeout (ms) | none |
| `JWT_SECRET` | HMAC signing secret for access tokens (≥ 32 bytes) | none (required) |
| `JWT_EXPIRE` | Token lifetime in milliseconds | `86400000` (24 h) |
| `MAIL_USERNAME` | SMTP username (Gmail address) | none (required) |
| `MAIL_PASSWORD` | SMTP password (Gmail **app password**) | none (required) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 login | none (required) |
| `FACEBOOK_CLIENT_ID` / `FACEBOOK_CLIENT_SECRET` | Facebook OAuth2 login | none (required) |
| `OAUTH2_REDIRECT_URI` | Frontend URL that receives `?token=` after OAuth2 success | `http://localhost:5173/oauth2/redirect` (fallback inside the handler) |
| `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | Image hosting | none (required) |
| `BAKONG_ACCOUNT_ID` | Bakong merchant account id used for KHQR | none (required) |
| `BAKONG_BASE_URL` | Bakong API base URL (e.g. SIT/UAT/production host) | none (required) |
| `EMAIL` | Sender/purpose email referenced by Bakong config | none |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated allowed browser origins | `http://localhost:5173` |
| `TELEGRAM_BOT_TOKEN` | Bot token used to verify Telegram Login Widget hashes | empty |
| `NGROK_ENABLED` | Enables the ngrok tunnel runner | `false` |
| `NGROK_AUTHTOKEN` | ngrok auth token | empty |
| `NGROK_DOMAIN` | Optional fixed ngrok domain | empty |
| `FIREBASE_PROJECT_ID` | Firebase project id | none (required at startup) |
| `FIREBASE_CLIENT_EMAIL` | Firebase service-account client email | none (required at startup) |
| `FIREBASE_PRIVATE_KEY` | Firebase service-account private key (`\n` escapes are expanded) | none (required at startup) |
| `FIREBASE_PRIVATE_KEY_ID` | Firebase private key id | none (required at startup) |
| `FIREBASE_CLIENT_ID` | Firebase service-account client id | none (required at startup) |

Non-secret, file-level settings (no env var):

| Property | Value | Meaning |
|---|---|---|
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `10MB` | Multipart upload limits |
| `file.upload-dir` | `uploads` | Local directory for attachment uploads |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema auto-updated from entities at startup |
| `ngrok.frontend-port` | `5173` | Frontend port exposed by ngrok |
| `app.invoice-pdf.company-name` / `.address` / `.phone` | Spring Boot Car Rental / Phnom Penh, Cambodia / +855 23 000 000 | Invoice PDF header (overridable) |
| `app.oauth2.redirect-uri` | `${OAUTH2_REDIRECT_URI:...}` | Post-OAuth2 redirect target |

**Generating a JWT secret**

```bash
openssl rand -base64 48
```

---

## Database configuration

The application uses **PostgreSQL**:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASS}
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

- Schema is derived from the entities (`ddl-auto=update`). There are no Flyway/Liquibase
  migrations in this project.
- Connection pooling is HikariCP, tuned through the `DB_HIKARI_*` variables.
- `postgresql` is declared with `runtime` scope in `pom.xml`. `mysql-connector-j` is also
  present for historical reasons — see [Troubleshooting](#troubleshooting).

**Example `.env` database entry**

```properties
DB_URL=jdbc:postgresql://localhost:5432/car_rental
DB_USER=postgres
DB_PASS=your-strong-db-password
```

### Entities & relationships

26 JPA entities mapped to `tb_*` tables. IDs use `GenerationType.IDENTITY`; timestamps use
`@CreationTimestamp` / `@UpdateTimestamp`.

```mermaid
erDiagram
    User ||--o{ Reservation : places
    User ||--o{ Rental : rents
    User ||--o{ Review : writes
    User ||--o{ Favorite : favorites
    User ||--o{ Notification : receives
    User ||--o{ LoginHistory : authenticates
    User ||--o{ PasswordResetToken : requests
    User ||--o{ AuditLog : performs
    User ||--o{ UserDevice : registers
    User ||--o{ DiscountUsage : redeems
    User ||--o{ Inspection : conducts
    User ||--o{ MaintenanceRecord : records

    Brand ||--o{ Vehicle : owns
    Vehicle ||--o{ Reservation : is_booked_by
    Vehicle ||--o{ Rental : is_rented_by
    Vehicle ||--o{ VehicleImage : has
    Vehicle ||--o{ Review : receives
    Vehicle ||--o{ Favorite : is_favorited_by
    Vehicle ||--o{ MaintenanceRecord : undergoes

    Location ||--o{ Reservation : books_from
    Location ||--o{ Rental : picks_up_at

    Reservation ||--|| Rental : converts_to
    Reservation ||--o{ ReservationAdditionalService : includes
    Reservation ||--o{ ReservationServices : includes
    Reservation ||--o{ DiscountUsage : applies

    AdditionalService ||--o{ ReservationAdditionalService : offers
    Services ||--o{ ReservationServices : offers

    Rental ||--|| Invoice : billed_as
    Rental ||--o{ Inspection : inspected_at
    Rental ||--o{ RentalDocument : documented_by
    Rental ||--o{ Review : rated_by

    Discount ||--o{ DiscountUsage : tracked_by
    Attachment ||--o{ RentalDocument : references
    Attachment ||--o{ VehicleImage : references
```

| Entity | Table | Key relationships |
|---|---|---|
| `User` | `tb_users` | Root of the model; holds role, `authProvider`, `providerId` |
| `Brand` | `tb_brands` | `1 —* Vehicle` |
| `Vehicle` | `tb_vehicles` | `* —1 Brand` |
| `VehicleImage` | `tb_vehicle_image` | `* —1 Vehicle`, `* —1 Attachment` |
| `Location` | `tb_locations` | Referenced by `Reservation` and `Rental` (pick-up / return) |
| `Reservation` | `tb_reservations` | `* —1 User`, `* —1 Vehicle`, `* —1 Location` ×2 |
| `Rental` | `tb_rentals` | `1 —1 Reservation`, `* —1 User`, `* —1 Vehicle`, `* —1 Location` ×2 |
| `Invoice` | `tb_invoices` | `1 —1 Rental` |
| `ReservationAdditionalService` | `tb_reservation_additional_services` | `* —1 Reservation`, `* —1 AdditionalService` |
| `ReservationServices` | `tb_reservation_services` | `* —1 Reservation`, `* —1 Services` |
| `Discount` / `DiscountUsage` | `tb_discounts` / `tb_discount_usages` | Usage links `User` + `Discount` + `Reservation` |
| `Review` | `tb_reviews` | `* —1 Rental`, `* —1 Vehicle`, `* —1 User` |
| `Inspection` | `tb_inspection` | `* —1 Rental`, `* —1 User` |
| `MaintenanceRecord` | `tb_maintenance_records` | `* —1 Vehicle`, `* —1 User` |
| `RentalDocument` | `tb_rental_documents` | `* —1 Rental`, `* —1 Attachment` |
| `Notification` | `tb_notifications` | `* —1 User` |
| `UserDevice` | `tb_user_devices` | FCM device token per user |
| `LoginHistory` | `tb_login_history` | `* —1 User`, IP + user agent |
| `PasswordResetToken` | `tb_password_reset_tokens` | `* —1 User`, 15-minute TTL, single use |
| `AuditLog` | `tb_audit_logs` | `* —1 User`, old/new values per mutation |
| `Attachment` | `tb_attachments` | URL + `DocumentTypeEnum` + display order |
| `Favorite` | `tb_favorites` | `* —1 User`, `* —1 Vehicle` |
| `SiteSettings` | `tb_site_settings` | Single-row branding/contact record |
| `AdditionalService` | `tb_additional_services` | Seeded on first boot (see below) |
| `Services` | `tb_services` | Service catalogue used by reservation add-ons |

**Seed data** — `config/DataSeeder.java` inserts four per-day add-on services only when the
table is empty: *Additional Driver* ($10), *GPS Navigation* ($5), *Child Seat* ($8),
*Full Insurance* ($15), with English and Khmer names.

---

## Running the application

**Local (development)**

```bash
# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

**Packaged JAR**

```bash
./mvnw clean package -DskipTests
java -jar target/spring_boot_project_api-0.0.1-SNAPSHOT.jar
```

**Docker (image only)**

```bash
docker build -t spring-car-rental-api .
docker run -p 8080:8080 --env-file .env spring-car-rental-api
```

The image builds with Maven on `maven:3.9-eclipse-temurin-21`, runs on
`eclipse-temurin:21-jre-alpine` as a non-root `appuser`, and exposes **8080**.

**Compose stack** (see [`deploy/docker-compose.yml`](deploy/docker-compose.yml))

```bash
cp deploy/.env.example deploy/.env   # fill in real values
docker compose -f deploy/docker-compose.yml up -d --build
```

> The compose file still provisions a MySQL service and sets `DB_HOST`. The current
> `application.properties` reads `DB_URL` and selects the PostgreSQL dialect — see
> [Troubleshooting](#troubleshooting).

Endpoints available after startup:

| URL | Purpose |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8080/api/test/any` | Minimal authenticated probe |

---

## Authentication & authorization

### JWT access tokens

- **Library:** JJWT 0.12.6, HMAC-SHA signing via `Keys.hmacShaKeyFor(...)` (UTF-8 secret).
- **Issued by:** `util/JwtUtil.generateToken(User)`.
- **Claims:** `sub` = user email, `id` = user id, `role` = `RoleEnum` name,
  `iat`, `exp` (`jwt.expiration`, default 24 h).
- **Missing `JWT_SECRET` fails startup on purpose** — there is no fallback secret.
- **Transport:** `Authorization: Bearer <token>` header.
- **Filter:** `config/JwtAuthFilter.java` (a `OncePerRequestFilter` registered before
  `UsernamePasswordAuthenticationFilter`) validates the token, loads the user by email,
  and populates the `SecurityContext`.
- **Sessions:** `SessionCreationPolicy.STATELESS`; CSRF disabled.

```mermaid
sequenceDiagram
    participant C as Client
    participant API as API
    participant DB as PostgreSQL

    C->>API: POST /api/auth/login {email, password}
    API->>DB: findByEmail + BCrypt.matches
    DB-->>API: User
    API-->>C: {id, email, role, token}
    C->>API: GET /api/user-profiles/me<br/>Authorization: Bearer ...
    API->>API: JwtAuthFilter validates signature/exp
    API->>DB: load UserDetails by email
    API-->>C: 200 + profile JSON
```

### Roles

```java
public enum RoleEnum { ADMIN, MANAGER, STAFF, CUSTOMER }
```

- URL-level rules live in `config/SecurityConfig.java`.
- Role rules live in `@PreAuthorize` annotations (`@EnableMethodSecurity` is on).
- Passwords are hashed with `BCryptPasswordEncoder` (bean defined in `SecurityConfig`).

### Public (no token) endpoints

| Method | Path |
|---|---|
| `OPTIONS` | `/**` (CORS preflight) |
| `POST` | `/api/auth/**` (register, login, logout, forgot/reset password, telegram) |
| `GET` | `/api/vehicles/**`, `/api/locations/**`, `/api/brands/**`, `/api/services/**`, `/api/vehicle-images/**`, `/api/settings` |
| `GET` | `/api/reviews/vehicle/**`, `/api/reviews/*` |
| any | `/api/v1/bakong/**` |
| any | `/oauth2/**`, `/login/oauth2/**` |
| any | `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` |

Everything else requires an authenticated JWT; role restrictions are layered on top with
`@PreAuthorize`. `GET /api/reviews/my-reviews` is explicitly `.authenticated()` and is
matched **before** the public `/api/reviews/*` rule.

### OAuth2 (Google / Facebook)

1. Browser is sent to `/oauth2/authorization/{provider}`.
2. The authorization request "state" is stored in a short-lived **HttpOnly cookie**
   (`CookieOAuth2AuthorizationRequestRepository`) instead of the session.
3. `CustomOAuth2UserService` finds or creates the `User` row
   (`authProvider = GOOGLE | FACEBOOK`, `providerId` = provider subject).
4. `OAuth2AuthenticationSuccessHandler` mints the same JWT and redirects to
   `OAUTH2_REDIRECT_URI` as `...?token=<jwt>`; the temporary cookie is cleared.
5. Failures are routed to `OAuth2AuthenticationFailureHandler`.

### Telegram Login

`POST /api/auth/telegram` — `TelegramAuthService` recomputes
`HMAC-SHA256(data_check_string, SHA256(bot_token))`, compares in constant time, and
rejects payloads older than 24 h. Telegram users get a synthetic
`telegram_<id>@telegram.local` email and `CUSTOMER` role.

### Password reset

1. `POST /api/auth/forgot-password` → creates a `PasswordResetToken` with a **15-minute**
   expiry and emails `http://localhost:5173/reset-password?token=<uuid>`
   (link base URL is currently hardcoded in `EmailServiceImpl`).
2. `POST /api/auth/reset-password` → validates token presence, non-use, and expiry,
   re-encodes the new password with BCrypt, and marks the token used.

---

## API reference

Legend for the **Auth** column:

| Value | Meaning |
|---|---|
| `Public` | No JWT required |
| `Auth` | Any valid JWT (any role) |
| `ADMIN` / `ADMIN/MANAGER` / `ADMIN/MANAGER/STAFF` | Role required via `@PreAuthorize` |

All request/response bodies are JSON unless stated otherwise. Paginated endpoints accept
standard Spring Data query params (`page`, `size`, `sort`) and return a Spring `Page`
envelope (`content`, `totalElements`, `totalPages`, …).

### Auth — `/api/auth`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create account, returns JWT |
| POST | `/api/auth/login` | Public | Email + password login, returns JWT |
| POST | `/api/auth/logout` | Auth* | Records logout in login history (`Principal` required) |
| POST | `/api/auth/forgot-password` | Public | Send password-reset email |
| POST | `/api/auth/reset-password` | Public | Consume reset token, set new password |
| POST | `/api/auth/telegram` | Public | Telegram Login Widget verification |
| GET | `/api/auth/users` | ADMIN/MANAGER | List users |
| GET | `/api/auth/users/{id}` | ADMIN/MANAGER | Get user |
| PATCH | `/api/auth/users/{id}/role?role=` | ADMIN | Change role |
| PATCH | `/api/auth/users/{id}/active?active=` | ADMIN/MANAGER | Activate/deactivate |
| DELETE | `/api/auth/users/{id}` | ADMIN | Delete user |

\* `/api/auth/**` is permitted at URL level; `logout` still requires a principal, so send
the Bearer token.

### Users (administration) — `/api/users`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/users` | ADMIN | List users |
| GET | `/api/users/{id}` | ADMIN | Get user |
| PATCH | `/api/users/{id}/role?role=` | ADMIN | Change role |
| PATCH | `/api/users/{id}/active?active=` | ADMIN | Activate/deactivate |
| DELETE | `/api/users/{id}` | ADMIN | Delete user |

### Profile — `/api/user-profiles`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/user-profiles/me` | Auth | Current user profile |
| PUT | `/api/user-profiles/me` | Auth | Update own profile |
| POST | `/api/user-profiles/me/change-password` | Auth | Change own password |
| GET | `/api/user-profiles/me/login-history` | Auth | Own login history (paged, size 8, sorted `loggedInAt` desc) |

### Vehicles — `/api/vehicles`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/vehicles` | Public | Search/list vehicles (paged) |
| GET | `/api/vehicles/{id}` | Public | Vehicle detail |
| GET | `/api/vehicles/{id}/booked-dates` | Public | Reserved date windows |
| POST | `/api/vehicles` | ADMIN | Create vehicle |
| PUT | `/api/vehicles/{id}` | ADMIN | Update vehicle |
| DELETE | `/api/vehicles/{id}` | ADMIN | Delete vehicle |

**`GET /api/vehicles` query parameters** (all optional):

| Param | Type |
|---|---|
| `brandId` | `Long` |
| `type` | `CarTypeEnum` — `SEDAN, SUV, PICKUP, HATCHBACK, COUPE, TRUCK, VAN, LUXURY, ELECTRIC` |
| `transmission` | `TransmissionEnum` — `AUTOMATIC, MANUAL, CVT` |
| `fuelType` | `FuelTypeEnum` — `PETROL, DIESEL, ELECTRIC, HYBRID` |
| `minPrice` / `maxPrice` | `BigDecimal` |
| `seats` | `Integer` |
| `page`, `size`, `sort` | Spring `Pageable` (default `size=10`) |

### Brands — `/api/brands`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/brands` | Public | List brands (paged) |
| GET | `/api/brands/{id}` | Public | Brand detail |
| POST | `/api/brands` | ADMIN/MANAGER/STAFF | Create brand |
| PUT | `/api/brands/{id}` | ADMIN/MANAGER/STAFF | Update brand |
| POST | `/api/brands/{id}/upload` | ADMIN/MANAGER/STAFF | Upload brand image (multipart) |
| DELETE | `/api/brands/{id}` | ADMIN/MANAGER/STAFF | Delete brand |

### Vehicle images — `/api/vehicle-images`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/vehicle-images` | Public | List all images |
| GET | `/api/vehicle-images/{vehicleId}` | Public | Images for a vehicle |
| POST | `/api/vehicle-images` | ADMIN/MANAGER | Create image record |
| PUT | `/api/vehicle-images/{id}` | ADMIN/MANAGER | Update image record |
| DELETE | `/api/vehicle-images/{id}` | ADMIN/MANAGER | Delete image record |

### Locations — `/api/locations`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/locations` | Public | List locations |
| GET | `/api/locations/{id}` | Public | Location detail |
| POST | `/api/locations` | ADMIN/MANAGER/STAFF | Create |
| PUT | `/api/locations/{id}` | ADMIN/MANAGER/STAFF | Update |
| DELETE | `/api/locations/{id}` | ADMIN/MANAGER/STAFF | Delete |

### Services — `/api/services`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/services` | Public | List service catalogue |
| GET | `/api/services/{id}` | Public | Service detail |
| POST | `/api/services` | ADMIN/MANAGER/STAFF | Create |
| PUT | `/api/services/{id}` | ADMIN/MANAGER/STAFF | Update |
| DELETE | `/api/services/{id}` | ADMIN/MANAGER | Delete |

### Additional services — `/api/additional-services`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/additional-services` | Auth | Active per-day add-ons (seeded on first boot) |

### Site settings — `/api/settings`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/settings` | Public | Branding / contact info |
| PUT | `/api/settings` | ADMIN/MANAGER | Update settings |

### Reservations — `/api/reservations`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/reservations` | Auth | Create reservation (owner = current user) |
| GET | `/api/reservations` | ADMIN/MANAGER/STAFF | List all |
| GET | `/api/reservations/{id}` | Auth | Owner or staff only |
| GET | `/api/reservations/my-reservations` | Auth | Current user's reservations |
| PATCH | `/api/reservations/{id}/status?status=` | ADMIN/MANAGER/STAFF | Change status |
| PATCH | `/api/reservations/{id}/cancel` | Auth | Cancel reservation |
| PUT | `/api/reservations/{id}` | ADMIN/MANAGER/STAFF | Update |
| DELETE | `/api/reservations/{id}` | ADMIN/MANAGER/STAFF | Delete |

**`POST /api/reservations` body**

```json
{
  "vehicleId": 1,
  "pickUpLocationId": 1,
  "returnLocationId": 2,
  "pickUpDateTime": "2026-10-15T09:00:00",
  "returnDateTime": "2026-10-18T09:00:00",
  "discountCode": "WELCOME10",
  "serviceIds": [1, 3],
  "depositAmount": 100.00,
  "discountAmount": 0,
  "additionalCharges": 0,
  "notes": "Airport pick-up"
}
```

### Reservation services — `/api/reservation-services`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/reservation-services` | Auth | Attach a service to a reservation |
| GET | `/api/reservation-services` | ADMIN/MANAGER/STAFF | List all |
| GET | `/api/reservation-services/{id}` | Auth | Detail |
| GET | `/api/reservation-services/my-reservation-services` | Auth | Current user's entries |
| PUT | `/api/reservation-services/{id}` | Auth | Update |
| DELETE | `/api/reservation-services/{id}` | Auth | Delete |

### Rentals — `/api/rentals`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/rentals` | ADMIN/MANAGER/STAFF | Create rental |
| GET | `/api/rentals` | ADMIN/MANAGER/STAFF | List all |
| GET | `/api/rentals/{id}` | Auth | Detail |
| GET | `/api/rentals/my-rentals` | Auth | Current user's rentals |
| PUT | `/api/rentals/{id}` | ADMIN/MANAGER/STAFF | Update |
| PATCH | `/api/rentals/{id}/status?status=` | ADMIN/MANAGER/STAFF | Change `RentalStatusEnum` |
| DELETE | `/api/rentals/{id}` | ADMIN/MANAGER/STAFF | Delete |

`RentalStatusEnum`: `PENDING, CONFIRMED, PICKED_UP, ACTIVE, RETURNED, COMPLETED`

### Rental documents — `/api/rental-documents`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/rental-documents` | ADMIN/MANAGER/STAFF | Create document record |
| GET | `/api/rental-documents` | ADMIN/MANAGER/STAFF | List all |
| GET | `/api/rental-documents/{id}` | ADMIN/MANAGER/STAFF | Detail |
| GET | `/api/rental-documents/rental/{rentalId}` | ADMIN/MANAGER/STAFF | Documents for a rental |
| GET | `/api/rental-documents/my-rental-document` | Auth | Current user's documents |
| PATCH | `/api/rental-documents/{id}` | ADMIN/MANAGER/STAFF | Update |
| DELETE | `/api/rental-documents/{id}` | ADMIN/MANAGER/STAFF | Delete |

`DocumentTypeEnum`: `CONTRACT, ID_CARD, DRIVERS_LICENSE, INSURANCE, CONDITION_PHOTO, VEHICLE_IMAGE, OTHER`

### Invoices — `/api/invoices`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/invoices` | ADMIN/MANAGER/STAFF | Create invoice |
| GET | `/api/invoices` | ADMIN/MANAGER/STAFF | List all |
| GET | `/api/invoices/{id}` | Auth | Detail |
| GET | `/api/invoices/my-invoices` | Auth | Current user's invoices |
| POST | `/api/invoices/{id}/confirm-payment` | Auth | Confirm a payment |
| PATCH | `/api/invoices/{id}/payment-method` | Auth | Set payment method |
| POST | `/api/invoices/{id}/mark-paid` | ADMIN/MANAGER/STAFF | Mark invoice paid |
| GET | `/api/invoices/{id}/pdf` | Auth | Download PDF (`application/pdf`) |
| PUT | `/api/invoices/{id}` | ADMIN/MANAGER/STAFF | Update |
| DELETE | `/api/invoices/{id}` | ADMIN/MANAGER/STAFF | Delete |

`InvoiceStatusEnum`: `UNPAID, PAID, CANCELLED` ·
`PaymentMethodEnum`: `VISA, ABA_PAY, KHQR, CASH`

> Endpoints marked `Auth` above are additionally guarded **inside the service**: viewing,
> paying, changing the payment method, and downloading the PDF require the invoice to
> belong to the caller (or the caller to hold a non-`CUSTOMER` role). `markPaid` is a
> no-op if already `PAID` and rejects `CANCELLED` invoices.

**`POST /api/invoices` body**

```json
{
  "rentalId": 1,
  "dueDate": "2026-10-18T09:00:00",
  "subtotal": 450.00,
  "discountAmount": 45.00,
  "taxAmount": 0,
  "lateFee": 0,
  "additionalServicesTotal": 30.00,
  "status": "UNPAID",
  "paymentMethod": "KHQR"
}
```

### Discounts — `/api/discounts`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/discounts/active` | Auth | Redeemable promo codes (validity window + usage cap applied) |
| GET | `/api/discounts` | ADMIN/MANAGER | List all |
| GET | `/api/discounts/{id}` | ADMIN/MANAGER | Detail |
| POST | `/api/discounts` | ADMIN/MANAGER | Create (code uppercased, uniqueness enforced) |
| PUT | `/api/discounts/{id}` | ADMIN/MANAGER | Update |
| DELETE | `/api/discounts/{id}` | ADMIN | Delete |

`DiscountTypeEnum`: `PERCENTAGE, FIXED_AMOUNT`

### Discount usages — `/api/discount-usages`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/discount-usages` | Auth | Record a redemption |
| GET | `/api/discount-usages` | ADMIN/MANAGER/STAFF | List all |
| GET | `/api/discount-usages/{id}` | Auth | Detail |
| GET | `/api/discount-usages/my-discount-usages` | Auth | Current user's redemptions |
| PUT | `/api/discount-usages/{id}` | ADMIN/MANAGER/STAFF | Update |
| DELETE | `/api/discount-usages/{id}` | ADMIN/MANAGER/STAFF | Delete |

### Reviews — `/api/reviews`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/reviews/vehicle/{vehicleId}` | Public | Reviews for a vehicle (paged) |
| GET | `/api/reviews/vehicle/{vehicleId}/count?rating=` | Public | Count by rating |
| GET | `/api/reviews/{id}` | Public | Review detail |
| GET | `/api/reviews/my-reviews` | Auth | Current user's reviews (paged) |
| GET | `/api/reviews` | ADMIN/MANAGER/STAFF | All reviews (paged) |
| POST | `/api/reviews` | Auth | Create review |
| PUT | `/api/reviews/{id}` | Auth | Update own review |
| DELETE | `/api/reviews/{id}` | Auth | Delete own review |
| PATCH | `/api/reviews/{id}/visibility` | ADMIN/MANAGER/STAFF | Toggle visibility |

### Favourites — `/api/favorites`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/favorites/{vehicleId}` | Auth | Add favourite |
| GET | `/api/favorites` | Auth | List own favourites |
| DELETE | `/api/favorites/{vehicleId}` | Auth | Remove favourite |

### Notifications — `/api/notifications`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/notifications/{userId}/notify` | ADMIN/MANAGER/STAFF | Create + push via FCM |
| GET | `/api/notifications` | ADMIN/MANAGER/STAFF | List all |
| GET | `/api/notifications/me/inbox` | Auth | Own inbox |
| GET | `/api/notifications/me/unread-count` | Auth | Unread badge count |
| PATCH | `/api/notifications/{id}/read` | Auth | Mark read (owner or staff) |
| PATCH | `/api/notifications/me/read-all` | Auth | Mark all read |
| DELETE | `/api/notifications/{id}` | Auth | Delete notification |
| POST | `/api/notifications/device` | Auth | Register FCM device token |
| POST | `/api/notifications/test?userId=` | Auth | Send a test push |

`NotificationTypeEnum`: `BOOKING_CONFIRMED, BOOKING_CANCELLED, RENTAL_STARTING_SOON,
RENTAL_ENDING_SOON, PAYMENT_SUCCESS, PAYMENT_FAILED, RETURN_REMINDER, LATE_RETURN, PROMOTION`

### Inspections — `/api/inspections`

Entire controller is class-level `@PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")`.

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/inspections` | All inspections (paged, size 8, sorted `inspectedAt` desc) |
| GET | `/api/inspections/{id}` | Detail |
| GET | `/api/inspections/rental/{rentalId}` | Inspections for a rental |
| GET | `/api/inspections/type?type=` | Filter by `PICK_UP` / `RETURN` (paged) |
| POST | `/api/inspections` | Create |
| PUT | `/api/inspections/{id}` | Update |
| DELETE | `/api/inspections/{id}` | Delete |

### Maintenance records — `/api/maintenance-records`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/maintenance-records` | Auth | List all |
| GET | `/api/maintenance-records/{id}` | Auth | Detail |
| POST | `/api/maintenance-records` | ADMIN/MANAGER/STAFF | Create |
| PUT | `/api/maintenance-records/{id}` | ADMIN/MANAGER/STAFF | Update |
| DELETE | `/api/maintenance-records/{id}` | ADMIN/MANAGER | Delete |

`MaintenanceTypeEnum`: `OIL_CHANGE, TIRE_REPLACEMENT, REPAIR, GENERAL_SERVICE, INSPECTION` ·
`MaintenanceStatusEnum`: `SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED`

### Attachments — `/api/attachments`

All endpoints require `ADMIN/MANAGER/STAFF`.

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/attachments` | List all |
| GET | `/api/attachments/{id}` | Detail |
| POST | `/api/attachments` | Create from a URL |
| POST | `/api/attachments/upload` | Multipart upload to local `uploads/` |
| PUT | `/api/attachments/{id}` | Update |
| DELETE | `/api/attachments/{id}` | Delete |

### File uploads (Cloudinary) — `/api/uploads`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/uploads?folder=misc` | Auth | Upload image (multipart `file`) |
| DELETE | `/api/uploads?publicId=` | Auth | Delete asset (204 No Content) |

### Audit logs — `/api/admin/audit-logs`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/audit-logs` | ADMIN/MANAGER | Paged audit trail |
| GET | `/api/admin/audit-logs/{entityName}/{entityId}` | ADMIN/MANAGER | History for one record |

### Login history (admin) — `/api/admin/login-history`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/login-history` | ADMIN/MANAGER | Paged login history |

### Bakong KHQR — `/api/v1/bakong`

All endpoints are **public** (`permitAll`).

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/bakong/generate-qr` | Generate a dynamic KHQR payload |
| POST | `/api/v1/bakong/qr-image` | Render a KHQR payload as a 300×300 PNG |
| POST | `/api/v1/bakong/check-transaction` | Verify a payment by MD5 |

### Role probes — `/api/test`

| Method | Endpoint | Auth |
|---|---|---|
| GET | `/api/test/any` | Auth |
| GET | `/api/test/customer` | CUSTOMER |
| GET | `/api/test/admin` | ADMIN |
| GET | `/api/test/manager` | MANAGER |
| GET | `/api/test/staff` | STAFF |

### Request / response examples

**Register**

```http
POST /api/auth/register
Content-Type: application/json

{
  "firstName": "Sok",
  "lastName": "Chan",
  "email": "sok@example.com",
  "password": "Str0ngPass!",
  "phone": "012345678",
  "gender": "MALE"
}
```

```json
{
  "id": 1,
  "email": "sok@example.com",
  "role": "CUSTOMER",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Login**

```http
POST /api/auth/login
Content-Type: application/json

{ "email": "sok@example.com", "password": "Str0ngPass!" }
```

**Authenticated call**

```http
GET /api/user-profiles/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Reset password**

```http
POST /api/auth/reset-password
Content-Type: application/json

{ "token": "e5b1f7c0-...", "newPassword": "Str0ngPass!" }
```

---

## Core business logic

### Reservation pricing (server-authoritative)

`ReservationServiceImpl.createReservation` recomputes every amount; client-supplied
totals are ignored:

1. Resolve the vehicle and both locations; a missing record fails the request.
2. Reject the booking when the whole-day difference between return and pick-up is
   `<= 0` (`Duration.toDays()`), i.e. at least one full day is required.
3. **Overlap check** — `reservationRepository.hasOverlappingReservation(...)` excluding
   `CANCELLED` bookings; rejects double-booking.
4. `basePrice = vehicle.pricePerDay × days`.
5. Each selected `AdditionalService` contributes `pricePerDay × days`
   (snapshot price stored as `pricePerDayAtBooking`).
6. `subtotal = basePrice + servicesTotal`.
7. `discountAmount = discountService.applyDiscount(discountCode, subtotal)` — the code is
   looked up case-insensitively, must be active and redeemable, and its `usedCount` is
   incremented. A blank/absent code yields `0`.
8. `totalPrice = (subtotal − discountAmount)` rounded to 2 decimals.
9. Persist `Reservation` + `ReservationAdditionalService` rows.

**Customer bookings additionally create, in the same transaction:**

- a `Rental` with `status = PENDING` and matching totals;
- an `Invoice` with a generated `invoiceNumber`, `status = UNPAID`,
  `paymentMethod = KHQR`, `dueDate = returnDateTime`;
- a `BOOKING_CONFIRMED` notification (persisted + pushed through FCM).

Staff-created reservations keep the manual flow (staff converts the confirmed reservation
into a rental and invoices it).

**Ownership rule:** `getReservationById` allows the owner or any non-`CUSTOMER` role.

### Discounts

- Codes are trimmed and uppercased; uniqueness is enforced case-insensitively.
- `GET /api/discounts/active` filters `isActive` records through
  `isRedeemable(...)` — validity window (`validFrom`/`validTo`) and `maxUses` cap.
- Creating/updating validates business rules (`validateBusinessRules`) —
  e.g. non-negative values and ordering of the validity window.

### Invoices

- Generated automatically for customer reservations; can also be created manually.
- `markPaid` sets `PAID` (staff only); `confirm-payment` and `payment-method` are
  available to any authenticated caller (service performs its own checks).
- **PDF download** (`GET /api/invoices/{id}/pdf`) renders an A4 document with OpenPDF:
  company header, invoice meta, vehicle/customer details, and a price-breakdown table
  (subtotal, add-ons, discount, tax, late fee, total).

### Rentals & inspections

- Status transitions use `RentalStatusEnum`; pick-up and return are documented by
  `Inspection` rows (`PICK_UP` / `RETURN`) with fuel level and condition fields.

---

## Server-side integrations

### Firebase Cloud Messaging

- `config/FirebaseConfig.java` builds a service-account JSON from the `FIREBASE_*`
  variables at `@PostConstruct` and calls `FirebaseApp.initializeApp(...)` once.
  Literal `\n` sequences in `FIREBASE_PRIVATE_KEY` are converted to real newlines.
- `NotificationServiceImpl` persists an in-app `Notification` and then sends an FCM
  `Message` to every device token registered for the user (`tb_user_devices`).
- Device registration: `POST /api/notifications/device`.

### Bakong KHQR payments

- `BakongServiceImpl` uses the official `kh.gov.nbc.bakong_khqr` SDK to build an
  **individual** KHQR payload: amount, currency (`KHR`/`USD`), expiration timestamp
  (default 15 minutes), merchant name/city, bill number, store/terminal labels.
- `POST /api/v1/bakong/qr-image` encodes the payload to a PNG via **ZXing**
  (error correction level `H`, 300×300, margin 1).
- `POST /api/v1/bakong/check-transaction` calls
  `{BAKONG_BASE_URL}/v1/check_transaction_by_md5` with a bearer token obtained from
  `BakongTokenService`, sending `{ "md5": "..." }`.

### File storage

| Path | Storage | Rules |
|---|---|---|
| `/api/uploads` | Cloudinary | ≤ 5 MB; `jpg|jpeg|png|webp`; folder must be one of `vehicle-images, brand-images, profile-pictures, site-settings, avatars, admin-profiles, misc` (default `misc`); delete validates the `publicId` folder prefix |
| `/api/attachments/upload` | Local `uploads/` | Extension allow-list from `UploadConfig` (`jpg, jpeg, png, webp`); filenames are `attachment-<millis>.<ext>` |
| multipart limits | — | `10MB` per file and per request |

### Email (SMTP)

- Gmail SMTP `smtp.gmail.com:587` with STARTTLS and auth.
- Used by `EmailServiceImpl.sendPasswordResetEmail` (HTML body, 15-minute token notice).

### ngrok (development)

Enabled with `NGROK_ENABLED=true`. `NgrokTunnelRunner` opens a tunnel to
`ngrok.frontend-port` (default 5173) on application ready and logs the public URL;
the tunnel is closed on shutdown.

---

## Validation & error handling

**Bean Validation** is applied to every request DTO through `@Valid` on controller
parameters. Constraints are declared on the DTO fields, e.g.:

```java
@NotBlank(message = "Email is required")
@Email(message = "Email must be valid")
private String email;

@Size(min = 8, max = 100, message = "Password must be between 8-100 characters")
private String password;
```

**`GlobalExceptionHandler`** (`@RestControllerAdvice`) handles:

| Exception | Response |
|---|---|
| `ResponseStatusException` | Status from the exception + reason as the body |
| `RuntimeException` | `400 Bad Request` + `ex.getMessage()` as the body |

All other validation failures fall through to Spring Boot's default error handling
(HTTP 400). Security failures are produced by `SecurityConfig`:

| Condition | Response |
|---|---|
| Missing/invalid JWT on a protected route | `401 Unauthorized` |
| Authenticated but insufficient role | `403 Forbidden` |

> Error bodies for handled business exceptions are **plain text**, not a JSON envelope.
> Clients should key off the HTTP status code.

---

## Audit logging & login history

**Audit trail** — `config/AuditLoggingAspect.java` is an `@AfterReturning` aspect on every
`@RestController` method. For `POST`/`PUT`/`PATCH`/`DELETE` it records:

- action (`CREATE` / `UPDATE` / `DELETE` from `AuditActionEnum`)
- entity name (controller name minus `Controller`) and entity id
- old value (the request DTO) and new value (the response DTO)
- acting user id and client IP (via `AuditLogContext`, honouring `X-Forwarded-For`)

`AuditLogController`, `UserController`, `UserManagementController`, and
`UserProfileController` are explicitly excluded. Read the trail through
`/api/admin/audit-logs`.

**Login history** — `config/AuthenticationEventListener.java` listens to
`AuthenticationSuccessEvent` and `AuthenticationFailureBadCredentialsEvent` and stores
username, success flag, client IP (first `X-Forwarded-For` hop), and `User-Agent`.
`POST /api/auth/logout` records the logout timestamp. Users see their own history at
`GET /api/user-profiles/me/login-history`; admins use `/api/admin/login-history`.

---

## Swagger / OpenAPI

Provided by `springdoc-openapi-starter-webmvc-ui` 3.0.2.

| Resource | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

`config/OpenApiConfig.java` registers a global `bearerAuth` (HTTP / Bearer / JWT) security
scheme and applies it as a global `SecurityRequirement`, so every operation in Swagger UI
shows an **Authorize** button. All three doc paths are `permitAll` in `SecurityConfig`.

No `springdoc.*` properties are customized — defaults apply.

---

## Testing

```bash
# Windows
mvnw.cmd test

# macOS / Linux
./mvnw test
```

> **Current state:** `src/test/java` exists but contains **no test classes**, so this
> command currently runs zero tests. There are no integration or unit test results to
> report for this repository.

Practical smoke checks against a running instance:

```bash
# Public search
curl http://localhost:8080/api/vehicles

# Login and capture the token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"your-password"}'

# Authenticated call
curl http://localhost:8080/api/user-profiles/me \
  -H "Authorization: Bearer <token>"

# Role probe
curl -i http://localhost:8080/api/test/any -H "Authorization: Bearer <token>"
```

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Startup fails with `Could not resolve placeholder 'DB_URL'` (or another `${...}`) | `.env` is missing or incomplete in the project root. Copy `.env.example` → `.env` and fill in **every** variable listed in [Environment variables](#environment-variables). |
| Startup fails on Firebase initialization | The `FIREBASE_*` variables are consumed in a `@PostConstruct` hook. Provide a complete service-account private key (with `\n` escapes) or the context will not start. |
| Startup fails with `jwt.secret` / no signing key | `JWT_SECRET` must be set (≥ 32 bytes). There is deliberately no default. |
| `Database action failed ... dialect` / driver errors | `application.properties` selects `PostgreSQLDialect` and reads `DB_URL`. `.env.example` and `deploy/docker-compose.yml` still carry legacy **MySQL** wording (`DB_HOST`, `MYSQL_*`). For PostgreSQL use a JDBC URL such as `jdbc:postgresql://host:5432/db`. |
| `deploy/docker-compose.yml` backend exits immediately | The compose file sets `DB_HOST` but not `DB_URL`, which the current properties require. Add `DB_URL` (and `DB_HIKARI_*`) to `deploy/.env` or update the compose `environment:` block. |
| `403` on a public-looking endpoint | Check `SecurityConfig`: only the listed `GET` paths are public; all writes need a JWT and often a role. |
| `401` after login | The `Authorization: Bearer <token>` header may be missing, the token may be expired (`JWT_EXPIRE`), or `JWT_SECRET` changed since the token was issued. |
| OAuth2 login ends in `401` at `/login/oauth2/code/{provider}` | The OAuth2 state cookie must not be blocked; confirm the provider redirect URI matches and `OAUTH2_REDIRECT_URI` points at your frontend. |
| Password-reset email never arrives | Verify `MAIL_USERNAME` / `MAIL_PASSWORD` (Gmail **app password**), and note the reset link is hardcoded to `http://localhost:5173/reset-password?token=...` in `EmailServiceImpl`. |
| `401` on images/brands before login | Only the `GET` variants of `/api/vehicle-images/**`, `/api/brands/**`, `/api/services/**`, `/api/vehicles/**`, `/api/locations/**` and `/api/settings` are public. |
| Port already in use | The app uses the default port **8080** (no `server.port` override). Free the port or set `SERVER_PORT`. |
| Upload rejected | Multipart limit is 10 MB; Cloudinary additionally enforces 5 MB, an image-only content type, and a folder allow-list. |
| DevTools + security filter errors | `spring.devtools.restart.enabled=false` is set deliberately; re-enable only if you also handle `SecurityFilterChain` re-registration. |
| Compile errors after pulling | Use JDK 21: `java -version`. The parent POM is a **SNAPSHOT**; a stable `repo.spring.io/snapshot` connection is required for the first build. |

---

## Contributing

1. Fork and create a feature branch from `main`.
2. Follow the existing layering: **controller → service interface → service impl → repository**.
   Keep DTOs in `dto/request` and `dto/response`, entities in `model`, enums in `enums`.
3. Declare authorization with `@PreAuthorize` on controller methods and keep
   `SecurityConfig` public-path rules in sync with any new public endpoint.
4. Use Bean Validation annotations on request DTOs; never trust client-supplied totals.
5. Keep secrets out of the codebase — read them from environment properties only.
6. Do not commit `.env`, `target/`, or the `uploads/` contents.
7. Verify your change compiles before opening a PR:

   ```bash
   ./mvnw clean package -DskipTests
   ```

8. Open a pull request describing the change and any configuration required.

---

## License

No license file is currently included in this repository. All rights are reserved by the
repository owners unless a `LICENSE` is added later.
