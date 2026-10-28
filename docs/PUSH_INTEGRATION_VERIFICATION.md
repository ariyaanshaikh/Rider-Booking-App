# Push integration verification — 2026-10-28

**Status: code changes verified automatically; not end-to-end complete.** Physical-iPhone delivery and completion-screen verification have not been performed. Do not close this work or enable production push solely because CI passes.

## Existing implementation retained

The dedicated `codex/push-notification-completion` branch starts from `production-changes`. It extends the existing `ExpoPushClient`, `PushWorker`, `EventNotifications`, `NotificationController`, `MessagePreparation` and `CommittedMessages`; no duplicate delivery service or device tables are introduced. Booking still offers rides to the existing matching-service candidates. It does not introduce or change manual rider-selection semantics.

Booking persists `ride_details` and SENT `ride_request` rows. The existing private rider-topic publication passes through `MessagePreparation`, which writes `notification_inbox`/`push_jobs` within the booking transaction. PostgreSQL rollback removes both the assignment and notification intent. Provider HTTP calls happen only in the scheduled worker after commit, so provider failures cannot undo an assigned/completed ride.

## Changes

- `RideDetailsService.java` and `RideService.java`: flush persisted offer attempts before deriving their event identity, keep all existing state/payment/OTP guards, notify outstanding offer owners when the customer cancels, and send the existing PAYMENT_REQUIRED event to the rider's private topic as well as the customer/ride topics.
- `EventNotifications.java`: RIDE_REQUEST deduplication now uses the persisted offer row ID and attempt timestamp. Publishing the same offer twice produces one private inbox entry/job per device, while a real rematch can generate a new alert.
- `PushWorker.java`: before sending a ride request, also require an AVAILABLE owner, an unexpired dispatch and the exact current offer attempt. Accepted, cancelled, superseded, unavailable and expired offers are not sent. Existing leased claims, TTLs, retries, invalid-token cleanup and receipt handling remain.
- `ExpoPushClient.java`: use clear New Ride Request and Ride Completed titles. The remote payload remains minimal: event ID, type and ride ID, never OTPs, GPS, documents or secrets.
- Both frontends' `pushClient.js`/notification helpers: allow foreground remote banners when sockets are unavailable, deduplicate local/remote alerts by event ID, notify mounted screens without navigating on receipt, preserve registration/rotation/logout ownership handling, and tolerate startup-response/storage failures. Local Admin notification data is also restricted to event type/ride/event IDs.
- Admin `rideMonitorState.js`, `ride-monitor.jsx`, `_layout.jsx`: hydrate the exact monitored ride from authorized history when it disappears from active-ride; ignore other rides' socket events; refresh on app foreground/remote receipt. A tap for the already-open ride neither replaces nor pushes the monitor, and network/ownership verification failure does not redirect to the dashboard. COMPLETED remains visible on the existing monitor.
- Rider `rideNotificationState.js`, home and `_layout.jsx`: a tapped request selects that exact pending offer, not the newest unrelated ride. Missing/expired/declined offers are not substituted. Remote reception can refresh offers without a socket.
- Both `app.json` files now contain the owner's supplied public EAS IDs; `eas.json` preview profiles explicitly target physical iOS, not a simulator. APNs credentials have not been independently verified.

## Device registration and lifecycle contracts

PUT `/api/notifications/devices` remains JWT-authenticated, accepting `{app,installationId,token,platform}`. Ownership comes from the authenticated account; the app must match its ADMIN/RIDER role. Reinstall/token changes upsert/rebind an installation and cancel obsolete delivery targets through the worker ownership check. DELETE `/api/notifications/devices/{app}/{installationId}` deactivates only the current user's device. Expo DeviceNotRegistered responses deactivate the matching token, not a newer replacement. Logout cleanup is best-effort if the network is unavailable; already queued OS notifications cannot be recalled.

REQUESTED → ACCEPTED → AWAITING_PAYMENT (PAYMENT_REQUIRED event) → captured-payment STARTED → COMPLETED retains the existing rules. End ride still requires STARTED and a captured payment, and repeat completion is idempotent. Completion writes the invoice/state, queues the correct Admin push, and publishes the Admin's existing WebSocket event. No bypass for live keys, test keys, OTP or payment was added. Actual delivery remains at-least-once, not exactly-once; event-ID deduplication does not make an external provider atomic with PostgreSQL.

## Observed deployment/database state

Read-only verification on 2026-10-28 found:

- Render service `srv-d9l3u8bl550s73fhv8f0` in the owner-confirmed Ariyaan workspace still runs live deploy `dep-dbe9evfb9u1s738tpol0`, commit `c7c43a7e31fc508db15543b6024f8c52de5502d3`, not production-changes/the new review branch.
- Owner-confirmed Neon project `muddy-band-52686480`, branch render-test (`br-restless-glitter-ay2vs05j`), database neondb, public schema: `production_schema_history`, `push_devices`, `notification_inbox` and `push_jobs` all returned NULL from `to_regclass`.
- Consequently the push migration is **not verified applied to the live database**, and none of its indexes can be verified there yet. No schema or data was changed by this check.

No new migration is needed for this patch. A deployment using the prod profile must successfully apply the existing additive production V1/V2 lineage and then verify tables, unique constraints and due/owner indexes. Do not manually mix the legacy V11/V12 lineage with `production_schema_history` or edit already-applied V1/V2 files. Rehearse against a fresh isolated copy first, using a direct connection. The saved recovery branch and source baseline are documented in [the working recovery record](RENDER_WORKING_BASELINE_2026-10-27.md); they do not replace a fresh export, secret-configuration backup or restoration rehearsal.

## Render settings and safe activation order

1. Keep the working main deployment and render-test branch intact while reviewing. Use separate staging resources for a migration rehearsal; obtain approval for additional paid resources.
2. Supply existing database, JWT, mail, Maps and public-URL settings securely. Do not regenerate the working JWT secret or overwrite unrelated variables. Require the production migration to complete before accepting traffic.
3. Verify the expected code revision, registered devices, actual EAS/APNs setup and release/test account selection. Configure `EXPO_PUSH_ENABLED=true` only for the verified deployment when ready to test delivery. This task deliberately **has not changed that live variable or triggered a deploy** because these prerequisites remain unmet.
4. Set `EXPO_ACCESS_TOKEN` only if enhanced Expo push security is enabled on the selected projects. That setting has not been verified; no token was invented, fetched, exposed or committed.
5. `APP_SHARED_MESSAGING_ENABLED=true` is already the production default. PostgreSQL shared fanout and the workers require a running backend. The previously recorded Render free instance can sleep; approve an appropriate always-on plan before a production reliability claim. No plan was changed.
6. Keep Razorpay/Route settings unchanged. The paid completion test requires a matching test-mode checkout and captured payment; lack of keys is not permission to skip this guard.

## Expo identity and account verification

| App | Public EAS project ID | Existing iOS bundle identifier |
| --- | --- | --- |
| Rider | 5aecd6b2-417b-4a0e-907e-845cbe92174a | com.bikerider.rider |
| Admin | 699cf8b1-4fce-457c-be4d-de32efc4553f | com.bikerider.admin |

Owner: maaz.kh10. Both apps already have expo-notifications config plugins. An environment variable EXPO_PUBLIC_EAS_PROJECT_ID overrides the app config; ensure build environments do not override it with another project. EXPO_PUBLIC_API_URL must point at the verified matching staging/backend origin, not an older server.

The Expo project page redirected to sign-in, so project ownership, APNs key/certificate association, push access-token policy, iPhone provisioning and existing build installation remain unverified. Use an authorized EAS internal preview/device build (not Expo Go). Preview does not require expo-dev-client; the pre-existing developmentClient profile does require that library/setup before using it. No Apple/Expo login, signing change or remote build was performed.

## Automated evidence

- Backend full PostgreSQL regression suite: **74 tests passed**, no failures/errors/skips. Includes seven new real-service-to-outbox tests with no message-template mock: persisted owned offers, idempotent booking/offer deduplication, provider failure after commit, payment-gated completion/invoice/Admin push/socket event, stale dispatch, rematch versioning, busy/expired delivery suppression, cancellation recipients and outer rollback.
- Existing tests also cover token rotation/account switching/logout, invalid-token cleanup, provider retry, receipt success, concurrent workers, transaction rollback, shared-node delivery, reset/session security and a real authenticated STOMP client.
- Admin frontend: **13 tests passed**. Rider frontend: **11 tests passed**. New tests cover foreground remote display without sockets, event-ID deduplication, receipt-vs-tap navigation behavior, exact-offer selection and completion-state recovery/no redirect.
- Both apps exported iOS and Android Hermes bundles. This checks JavaScript/config compatibility, **not a signed native build or a physical-device test**.
- Audit at this check: no high/moderate/critical runtime dependency findings; 11 low findings in each app's existing Expo/deepmerge dependency chain. No forced SDK upgrade was attempted as part of this feature.
- Test push uses a mock/local HTTP provider. No remote push was sent to an actual device, no customer account was used, and no live/test payment was triggered by the automated suite.

## Required iPhone acceptance evidence — all still pending

Record build ID/version, bundle ID, user/installation (without exposing the Expo token), ride ID, outbox event ID, Expo ticket/receipt IDs, device outcome and timestamp:

1. Install the new Rider/Admin preview builds with correct APNs credentials, sign in to approved test accounts, grant permission and confirm authenticated iOS registration on the matching backend.
2. Create a ride; confirm only persisted offer recipients have jobs. Verify foreground delivery with sockets disconnected, then background and normally closed Rider delivery. Distinguish network/permission/OS restrictions from application failures.
3. Tap a valid offer while another newer offer exists; confirm the correct ride opens. Tap accepted/expired/unauthorized offers; no wrong actionable request should appear.
4. Accept, verify OTP, capture a legitimate matching test-mode payment and confirm STARTED. Complete as the assigned rider. Confirm persisted COMPLETED and one invoice, one Admin completion job, successful provider receipt, and the current Admin monitor still visible with Completed (no dashboard reset/new monitor).
5. Repeat with Admin background/closed, then tap while that ride monitor is already present. Check current server state wins over any delayed event.
6. Inject duplicate event publication, expired/superseded offers, invalid tokens and transient provider failures in isolated staging only. Confirm no duplicate business transitions, no cross-account notifications, bounded retries/cleanup, and no assignment rollback from HTTP provider failure.

Until these entries have actual evidence, the feature is **not end-to-end complete**.
