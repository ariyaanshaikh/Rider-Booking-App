# Production branch compatibility and rollout

Backend branch: `production-changes`, based on `codex/razorpay-ride-payments` at `293f7af`.
Frontend branches: `ariyaan-changes`, based on the latest `feature/ride-end-otp-back-buttons` heads:
rider `e8900c5`, admin `1a0cfc9` (the corresponding test branches point at the same commits).

## Preserved contracts

- ADMIN remains the booking customer role; RIDER remains the driver role. Both can register.
- Ride IDs, field names, fare calculation, 10/90 allocation, invoice fields, and text responses remain unchanged.
- Ride OTP and email OTP remain readable in the database. Email confirmation still returns `token` and `userId`; `confirmed_at` prevents reuse without deleting the readable OTP.
- Existing `/topic/user/{id}`, `/topic/rider/{id}` and authorized ride/private queue destinations continue working. Client SEND frames and other accounts' subscriptions are rejected.
- Existing authenticated booking calls without an idempotency header still work. New admin builds supply `Idempotency-Key` and retain it after an uncertain network result. Reusing it with different booking details returns 409.
- Only the server marks payment captured or starts a paid ride. Rider UI reflects AWAITING_PAYMENT until server confirmation.
- POST `/register/confirmToken` uses `{email, otp}`; POST `/register/verifyEmail` uses `{emailAddress}`. The legacy resend GET remains temporarily compatible. Confirmation never accepts an OTP URL.

## Security and reliability changes

KYC submission and reads are limited to the rider who owns the record or an explicit trusted operator. Approval requires `APP_OPERATIONS_USERNAMES` (comma-separated, exact existing enabled usernames). The profile response exposes `operationsAccess`; the admin app hides approval navigation for other customers. Public role selection does not grant operator privileges.

The unauthenticated `/register/updateUser` password overwrite path is disabled. Neither supplied frontend calls it. Their existing forgot-password screen is a placeholder and still needs a complete verified reset workflow before it can be advertised.

Registration validates the frontend's account fields and ignores supplied IDs/enabled/confirmation fields. Passwords require 8–72 characters for new registration; existing login passwords remain accepted. Login, resend, email OTP, registration and ride OTP attempts use shared PostgreSQL counters in 15-minute windows. Limits return 429. Add edge/IP limits for distributed abuse as part of hosting setup.

Ride updates are sent after transaction commit. Failed sends are logged and mobile clients can recover state by polling. This is not a durable notification outbox. The simple WebSocket broker remains suitable for one backend instance; multiple instances require a shared broker/outbox. Expo remote push still requires the token registration and server push delivery described in the frontend repositories' existing notes.

Unassigned REQUESTED rides expire after `app.dispatch-expiry-minutes` (default 15), becoming ADMIN_CANCELLED and cancelling their rider requests. Accepted and payment-pending rides are not silently cancelled: refund and payment uncertainty require support/reconciliation. No live payments, refunds, transfers, deployments, or production database writes are performed by this development task.

Hosted checkout now starts from POST `/api/payments/rides/{rideId}/checkout-session`. Its ten-minute, ride-scoped token is in a URL fragment, removed immediately by the page and sent only in `X-Checkout-Token` headers. Account JWTs stay in SecureStore and never enter checkout URLs. Only a hash of the checkout capability is persisted; this does not change OTP storage.

## Database and release order

The prod profile now loads the Spring Boot 4 Flyway starter and `db/production` migrations, using its own `production_schema_history`. It baselines existing schemas at version 0 and applies the additive V1. Old `flyway_schema_history` entries and legacy migration files are preserved; they are not replayed (the old V2 references the obsolete RIDER_DEMO table). New installations use the same production migration. V11 is also supplied for developers who deliberately maintain the original lineage.

1. Rehearse migration against a restored staging copy. The automated tests cover both an empty database and the documented existing schema, not every possible manually edited production schema. Take a restorable database backup.
2. Configure the prod profile, database credentials, JWT secret, Google key, mail settings, `APP_PUBLIC_BASE_URL` and operator allowlist. The public URL must match the frontend API origin for checkout.
3. Release the backend branch to staging, verify `/health` (database readiness) and `/health/live` (process liveness), then release both matching frontend branches. Local env.sh/default-profile users must apply V11 or use the prod migration setup; otherwise new tables/columns will be absent.
4. Enable Razorpay only with matching test credentials and a valid webhook secret. Complete a real test-mode checkout and webhook delivery on physical devices. Keep Route disabled until approved linked accounts and finance reconciliation are operational.
5. Exercise sign-up, OTP/resend, login, dispatch, rider acceptance, cancellation/rematch, OTP, payment, location, completion, invoice and reconnect on both apps. Check rejected cross-account KYC and WebSocket access as well.

Docker excludes local secret files and runs tests when building. CI runs the backend with an isolated PostgreSQL test service; frontend CI runs service contract tests and Android bundle export. H2 context tests never require the developer's local database. PostgreSQL tests create and remove their own random schemas and run when PAYMENT_TEST_DB_URL is set.

## Verification snapshot

Backend `mvnw verify`: 50 tests passed, no failures, errors or skips when PAYMENT_TEST_DB_URL is set. Admin service contracts: 4 passed. Rider service contracts: 2 passed. Both apps exported Android and iOS Hermes bundles successfully after clean dependency installation. Each frontend's final npm audit reports 9 remaining findings (6 moderate, 3 high), with no critical findings. These checks do not exercise provider credentials or physical-device behavior.

## Remaining release gates

Real-device QA, Razorpay test-mode callbacks and Route settlement, restored production-schema rehearsal, load/soak testing, monitoring/alerts, backup restoration, dependency vulnerability review and any planned Expo SDK migration are still required. Multi-instance messaging, durable remote push, a complete password-reset flow, and automated refund/support tooling remain separate implementation work. This branch is a tested hardening increment, not a certification that those workflows already exist.
