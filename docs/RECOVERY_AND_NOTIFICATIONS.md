# Recovery and reliable delivery

Pair backend `production-changes` with both frontend `ariyaan-changes` branches. Existing ride, OTP, payment and invoice contracts remain in place. No new cancellation/refund workflow is introduced.

## Password recovery

Both sign-in screens link to email → six-digit code → new password. Public JSON endpoints are POST `/auth/sendotp` `{email}`, `/auth/verifyotp` `{email,otp}`, and `/auth/resetpassword` `{email,resetToken,password}`. Sending a code returns the same response for unknown accounts. Codes expire after 15 minutes; the verified reset session expires after 10 minutes and can be used once. Request, verify and reset limits are shared across instances. New passwords require 8–72 characters and at most 72 UTF-8 bytes. Reset codes stay readable in `password_resets` and pending mail jobs, as requested. Only reset-session capabilities are hashed.

Reset mail is queued transactionally and retried up to five times. Configure existing SMTP credentials and `APP_MAIL_FROM` as a verified sender address. A successful reset increments the account credential version, revokes old JWTs and device registrations, and closes old connected sockets within approximately 10 seconds. Registration/email-verification OTP behavior is unchanged.

## Remote notifications

1. Configure `EXPO_PUBLIC_EAS_PROJECT_ID` separately in each frontend build (or the existing `extra.eas.projectId`). Keep `EXPO_PUBLIC_API_URL` pointed at the matching backend.
2. Configure each native app's FCM v1 / APNs credentials in its Expo/EAS project. Build and install fresh Android/iOS binaries; Expo Go is intentionally not used for remote registration. Native module changes require a new build, not just an OTA JavaScript update.
3. Set backend `EXPO_PUSH_ENABLED=true`. Set server-only `EXPO_ACCESS_TOKEN` if enhanced Expo push security is enabled. Never expose server credentials in `EXPO_PUBLIC_*` values.
4. Verify notification permissions on physical devices and test the checklist below. Push remains off by default until credentials are ready.

Devices register after login/startup/foreground, refresh on native token changes, and unregister before local logout/account switches. Ownership comes only from the authenticated account, not client-supplied user IDs. Reset invalidates old device registrations. Missed recent events are queued at registration. The server stores private inbox entries and push intents in the same transaction as ride events; workers use leased, concurrent-safe claims, bounded backoff and Expo ticket/receipt checks. Invalid tokens are disabled. Permanent credential failures remain visible for operators instead of being silently retried forever.

Payloads contain only event ID, event type and ride ID, never OTPs, personal documents, coordinates, bank data or JWTs. Visible background alerts tell the user to open the app. Taps fetch current authorized server state before navigation. Rider home also recovers pending offers on focus, foreground and every 15 seconds while focused. Existing foreground socket/local-notification behavior remains in place; foreground remote duplicates are suppressed. Push is at-least-once, not exactly-once; taps are deduplicated by event ID. A logout request that cannot reach the server is best-effort: generic OS alerts already queued/in flight cannot be recalled, but opening them requires the current account's authorization.

Requests expire from the push queue after five minutes; other alerts after one hour. Receipts are checked after 15 minutes and for up to 24 hours. A successful receipt means handoff to APNs/FCM, not proof a user saw an alert. Inbox entries are retained 30 days; old reset mail jobs one day after expiry. Operators allowlisted by `APP_OPERATIONS_USERNAMES` can inspect aggregate states at GET `/api/notifications/delivery-status`. Alert on FAILED jobs/credential errors and long-lived pending jobs in production monitoring.

No app can guarantee delivery with disabled permissions, missing network, OS restrictions or Android Settings → Force stop. Visible remote alerts do not require JavaScript to execute while the app is closed. Ordinary background/closed-app tests must be distinguished from force-stop tests.

## Multiple backend instances

`APP_SHARED_MESSAGING_ENABLED=true` is the production default. All replicas must use the same PostgreSQL database/schema. Committed messages are persisted once and every node claims its own deliveries before sending to its local STOMP broker. This requires no new Redis/RabbitMQ service. Database rollback does not announce an event; expired delivery leases retry after a node/worker interruption; late commits are not skipped by a numeric cursor. Event retention is one hour. Clients still refresh authoritative state after long disconnections. PostgreSQL fanout supports moderate traffic; load-test the actual replica/client count before launch.

Native WebSocket connections remain attached to one replica. If SockJS falls back to HTTP polling, configure load-balancer session affinity so all requests for that SockJS session reach the same replica. The database fanout does not replace that transport requirement. A single-instance installation may set the flag false; push/mail queues still work.

## Migration and validation

The prod profile applies `db/production/V2__account_recovery_and_delivery.sql` after V1, using `production_schema_history`. V1 is unchanged. Use the production lineage for deployments; do not mix it with the legacy lineage. V12 supplies the equivalent additive change only for deliberate legacy-lineage installations. Back up and rehearse on a restored staging database before release; do not rely on Hibernate update alone.

Automated verification uses isolated PostgreSQL schemas, fake SMTP/push transports, local mock HTTP and a real authenticated WebSocket. It covers existing rides/payments plus reset expiry/replay/concurrency, migration preservation, push claims/retry/receipts/logout/account switching, rollback, two fanout nodes and out-of-order transaction commits. Both frontends test auth/API compatibility, security adapters and notification lifecycle behavior and export Android/iOS bundles. No live email, push, checkout, deployment or production database mutation is performed by these tests.

Before release on physical Android and iOS devices, test:

- Unknown email, wrong/expired/resend code, password mismatch, successful reset, old password/JWT/socket rejected and new login accepted.
- Grant/deny notification permission; foreground, background and normally closed app; notification tap while signed in/out; token rotation, logout and account switch.
- Request arrives while rider app is closed; opening recovers the still-pending offer, while accepted/expired requests do not reappear as actionable offers.
- Booking, accept, rematch, allowed cancellation, OTP → payment → ride start, GPS reconnect, completion and invoice. Paid/started cancellation stays blocked.
- Two staging replicas: producer and subscriber on different nodes, worker/node interruption, reconnection, and transport affinity when HTTP polling is enabled.
- SMTP outage/recovery and Expo credential failures are observable. Rehearse backups, migrations, provider test-mode callbacks and monitoring before production rollout.
