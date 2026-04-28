# Auth Service API

Authentication and user-profile service for Event Hub. This service owns signup, login, token refresh, email verification, password reset, profile updates, avatar upload, and user lookup endpoints.

## Stack

- Java 17
- Spring Boot 3
- Spring Security OAuth2 resource server
- Keycloak admin client
- Spring Cloud Config client
- Eureka client
- MySQL
- RabbitMQ
- AWS S3

## Functional Requirements

- Register visitor accounts
- Verify email with OTP
- Resend verification OTP
- Login through Keycloak-backed flow
- Refresh access tokens
- Start and complete password reset
- Return current user profile
- Update current user profile
- Upload and replace user avatars
- List users for admin or host flows
- Resolve internal user ID by email

## Non-Functional Requirements

- Stateless JWT-based authentication
- Centralized configuration through Config Server
- Service discovery registration through Eureka
- Async notification integration through RabbitMQ
- Externalized object storage for avatars through S3
- MySQL persistence for user data
- Actuator health support

## APIs

Base path: `/user-service/api/v1`

Public visitor endpoints:

- `POST /users/visitors/signup`
- `POST /users/visitors/resend`
- `POST /users/visitors/forget-password-request-code`
- `POST /users/visitors/verify-reset`
- `POST /users/visitors/reset-password`
- `POST /users/visitors/verify-email`
- `POST /users/visitors/login`
- `POST /users/visitors/refresh-token`

Authenticated endpoints:

- `GET /users/get-user-details`
- `PUT /users/update-user-details`
- `POST /avatars/user/manage-avatar`

Admin or host endpoints:

- `GET /users/all`
- `GET /users/resolve-user-id`

Internal integration used from this service:

- `POST /notification-service/api/v1/internal/notifications/email-verification-otp`
- `POST /notification-service/api/v1/internal/notifications/password-reset-otp`
- `POST /notification-service/api/v1/admin/notifications/send-host-password`

## Role-Based Access

- `permitAll`: all `POST /users/visitors/**` endpoints
- `user`, `admin`, `host`: profile lookup, profile update, avatar upload
- `admin`, `host`: user listing and user ID resolution

Roles are enforced with `@PreAuthorize` plus JWT role mapping in `SecurityConfig`.

## Runtime Dependencies

- Config Server on `8888`
- Eureka Server on `8761`
- MySQL
- Keycloak realm and client
- RabbitMQ
- Notification service
- AWS S3 credentials and bucket

## Local Setup

1. Copy `.env.example` to `.env`.
2. Fill these values:
   - `SPRING_CLOUD_CONFIG_URI`
   - `AUTH_DB_PASSWORD`
   - `KEYCLOAK_CLIENT_SECRET`
   - `KEYCLOAK_CONFIG_NAME`
   - `KEYCLOAK_CONFIG_PASSWORD`
   - `AWS_ACCESS_KEY_ID`
   - `AWS_SECRET_ACCESS_KEY`
   - `RABBITMQ_PASSWORD`
3. Start supporting services first:
   - `config-server`
   - `eureka-server`
   - MySQL
   - Keycloak
   - RabbitMQ
   - `notification-service-api`
4. Run:

```powershell
.\mvnw.cmd spring-boot:run
```

Default port: `9092`

## Build

```powershell
.\mvnw.cmd clean package
```

## Notes

- This service expects Keycloak to contain the configured realm, client, and bootstrap user.
- Visitor endpoints are intentionally public; protected behavior is enforced on profile and admin paths.
