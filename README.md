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
- List users for admin flows
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

Admin-only endpoints:

- `GET /users/all`
- `GET /users/resolve-user-id`

Internal integration used from this service:

- `POST /notification-service/api/v1/internal/notifications/email-verification-otp`
- `POST /notification-service/api/v1/internal/notifications/password-reset-otp`
- `POST /notification-service/api/v1/admin/notifications/send-host-password`

## Role-Based Access

- `permitAll`: all `POST /users/visitors/**` endpoints
- `user` / `customer`, `admin`, `host`: profile lookup, profile update, avatar upload
- `admin` only: user listing and user ID resolution

Roles are enforced with `@PreAuthorize` plus JWT role mapping in `SecurityConfig`.

## Host Applications (Phase 1)

Host applications (Phase 1): verified, active, enabled USER/CUSTOMER accounts
can `POST /user-service/api/v1/host-applications` with `organizationName`
(required, trimmed, maximum 200 characters) and optional `reason` (trimmed,
maximum 2,000 characters). Identity comes only from the validated JWT email.
HOST/ADMIN accounts are excluded, including accounts that also have a user role.

`GET /user-service/api/v1/host-applications/me` returns the caller's application
in the standard response envelope, or 404 if none exists. Submission returns
201 with status `PENDING`; existing PENDING/APPROVED applications return 409.
A rejected application can be resubmitted using the same row; review metadata
is cleared and the application timestamp is refreshed.

The auth database owns `host_application`, with a unique `system_user_id`
foreign key to its own `system_user` table. Submission uses a short user-row
lock to serialize concurrent requests. The entity is created by the existing
Hibernate schema-update configuration; no new database is required.
Submission does not assign a Keycloak role or send emails.

## Host Application Admin Review (Phase 2)

These endpoints require the existing `admin` JWT authority. HOST, USER and
CUSTOMER callers receive 403; unauthenticated callers receive 401.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/user-service/api/v1/host-applications/admin?status=PENDING&page=0&size=20` | Paginated applications; status filter is optional |
| PATCH | `/user-service/api/v1/host-applications/admin/{applicationId}/approve` | Approve a pending application |
| PATCH | `/user-service/api/v1/host-applications/admin/{applicationId}/reject` | Reject with `{"reason":"More information required"}` |

Responses use the existing envelope. Lists return `dataList` and `dataCount`,
including applicant contact and review information without credentials.
Review responses contain the application and applicant details. Rejection
requires a nonblank reason of at most 2,000 characters. `reviewedBy` comes
from the authenticated admin's JWT subject, never the request body.

Only `PENDING -> APPROVED` and `PENDING -> REJECTED` reviews are allowed.
Invalid/repeated transitions return 409; unknown applications return 404.
A database row lock serializes competing reviews. A rejected applicant must
resubmit through Phase 1 before another review.

Approval verifies the existing Keycloak user and adds only the realm `host`
role if absent, preserving existing roles and users. HOST authority appears
on subsequent login/token issuance; already-issued JWTs are unchanged.
Rejection makes no Keycloak role changes. Review emails are published through
the Phase 5 auth notification flow below.

Keycloak and MySQL are separate transactions. A newly granted HOST role is
removed best-effort if the database transaction rolls back, including after
flush. Pre-existing HOST roles are never removed by compensation. Cleanup
failure or an unknown database completion is logged for reconciliation.
A crash, ambiguous Keycloak response, failed cleanup, or concurrent external
role changes can still leave inconsistent state requiring manual review;
approval is not globally atomic.

## Host Review Notifications (Phase 5)

After the review database transaction commits, the existing auth publisher sends
`AuthNotificationEvent` to the configured auth notification queue:

- `HOST_APPLICATION_APPROVED`: `payload` contains `email`, `firstName`, `organizationName`.
- `HOST_APPLICATION_REJECTED`: the same fields plus `rejectionReason`.

No password, user identity selection or review metadata is included. A failed
save or rolled-back transaction publishes no success event. Notification Service
sends approval/dashboard guidance or rejection/re-application guidance.

This is an after-commit publication, not a transactional outbox. A crash between
commit and publication, or a failed RabbitMQ publish, can leave a committed review
without an email. Publication failures are logged with application ID and event
type for reconciliation; they do not reverse or misreport the committed review.
The existing auth event envelope has no event ID/correlation fields, and the
publisher does not implement confirmed durable recovery. Duplicate delivery/email
remains possible; there is no exactly-once guarantee. Booking notification retry,
DLQ and idempotency behavior is unchanged. The auth consumer's existing failure
policy is NACK without requeue, not the booking retry pipeline.

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

With the `local` profile, startup seeds the two existing HOST accounts and one
application ADMIN: `eventhub.admin@local.test` / `amiru1234` (Amiru Horadagoda,
contact `0717244872`). This is separate from the `KEYCLOAK_CONFIG_NAME` management
account. The realm must already contain the `host` and `admin` roles.

Existing local users are skipped; their passwords and roles are not reset.
HOST seed password notifications remain controlled by
`eventhub.auth.seed-hosts.publish-notifications` (default `false`); ADMIN seed
passwords are never published through that flow. Seeding requires `local` and
is disabled whenever `prod` is active. Deployments must explicitly set
`SPRING_PROFILES_ACTIVE=prod`; the existing development default is `local`.

## Build

```powershell
.\mvnw.cmd clean package
```

## Notes

- This service expects Keycloak to contain the configured realm, client, and bootstrap user.
- Visitor endpoints are intentionally public; protected behavior is enforced on profile and admin paths.
