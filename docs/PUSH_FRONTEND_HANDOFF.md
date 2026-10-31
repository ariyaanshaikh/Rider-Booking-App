# Android and iOS push: backend handoff

Updated 31 October 2026. Implementation branch: `production-changes`.

This branch now includes the existing persisted Expo push implementation plus the offer fixes below. It is not automatically deployed to Render. Production push stays disabled until a verified backup, migration rehearsal, credentials, deployment approval and physical-device tests are complete.

## Shared mobile contract

Both native apps use Expo tokens and the same JWT-authenticated backend. Android uses platform `android`; iPhone uses `ios`. One user may register multiple installations across both platforms. FCM/APNs provider credentials belong in EAS, not the mobile bundle or this repository. Optional EXPO_ACCESS_TOKEN is server-only.

- `PUT /api/notifications/devices`: `{ "app":"rider", "installationId":"<installation UUID>", "token":"<Expo push token>", "platform":"android" }`. For iPhone, platform is `ios`. Returns 200 `{ "registered":true }`.
- `DELETE /api/notifications/devices/rider/{installationId}` before clearing JWT: returns 200 `{ "unregistered":true }`.
- `GET /api/notifications`: private historical inbox, maximum 50. It is not a list of currently actionable offers.
- `GET /api/notifications/ride-requests`: existing pending-offer list, maximum ten. Existing response fields remain unchanged.

All require `Authorization: Bearer <login JWT>`; use `Accept: application/json`. PUT also requires Content-Type application/json. Never supply a userId; device and offer ownership comes from the JWT. Permission denial, offline logout and invalid-token cleanup must not break booking.

## New exact-offer lookup for notification taps

```http
GET /api/notifications/ride-requests/{rideId}
Authorization: Bearer <Rider JWT>
Accept: application/json
```

200 returns one object, not an array:

```json
{
  "rideId":123,
  "pickupLatitude":19.0,
  "pickupLongitude":72.0,
  "dropLatitude":20.0,
  "dropLongitude":73.0,
  "fare":361.00,
  "vehicleName":"bike",
  "chassisNo":"bike-id"
}
```

This is independent of the list's ten-offer limit. A missing, expired, declined, withdrawn, accepted or other-account offer returns 404 (`REQUEST_FAILED`, message `Offer not available`). A non-Rider receives 403. Security-filter errors need not have the application JSON envelope.

On notification tap, use the exact rideId lookup and open that offer. On 404 display unavailable; never substitute another offer or use the active-ride endpoint for an unaccepted request. The existing list is still available for normal discovery.

## New persistent decline action

```http
POST /api/notifications/ride-requests/{rideId}/decline
Authorization: Bearer <Rider JWT>
Accept: application/json
```

No request body. 200:

```json
{"declined":true,"rideId":123}
```

The authenticated Rider's pending offer becomes CANCELLED. Other Riders' offers, the ride, availability, payment and OTP are untouched. Repeated decline/withdrawn-offer dismissal is idempotent. Missing/not-owned offer returns 404; trying to decline an accepted assignment returns 409 (`REQUEST_FAILED`, message `Offer is no longer pending`). Non-Rider receives 403.

Disable repeated button taps during the request. Only dismiss as server-confirmed after success; handle network failure/retry visibly. Do not call `/ride/rider-cancel` to decline a pending offer. That endpoint remains for cancellation of an accepted assignment. A future genuine rematch is a new offer attempt and may notify again.

Acceptance remains `POST /ride/accept?rideId=123&riderId=<own profile ID>`, with no JSON body. Success is plain text `Ride accepted`; an unavailable acceptance remains HTTP 400 plain text `Ride already taken`.

## Freshness, ownership and delivery

Pending lookup, acceptance and push dispatch now require both the ride and its persisted offer attempt to be within the configured dispatch window (default 15 minutes). This prevents historical offers from becoming actionable merely because V1 added a fresh ride.created_at timestamp. No historical data rewrite or new migration is required for this guard. Existing V1/V2 migration files are unchanged.

Decline and accept serialize using the ride lock, so a decline cannot undo a winning assignment. The worker checks current offer state/attempt and device ownership immediately before sending. A notification already handed off to Expo may still arrive after a state change, so mobile state revalidation is always required.

Remote data stays `{ "eventId":"<UUID>", "type":"RIDE_REQUEST", "rideId":123, "remote":true }`. Maintain eventId deduplication, foreground display, background/cold-start tap handling, logout/account privacy and authorized current-state fetching. Inbox/job persistence, bounded retries, receipt handling, token rotation and completion notifications are retained.

OTP → AWAITING_PAYMENT → verified captured payment → STARTED → COMPLETED remains enforced. No bypass or manual-payout mode was added.

## Release gates still open

Verification on 31 October: clean production-changes source plus these fixes passed Maven verify with **79 tests, zero failures/errors/skips**, and all **four preflight tests** passed. New checks cover exact-offer lookup beyond ten entries, cross-account/role rejection, stale legacy offers, persistent/idempotent decline, acceptance races and independent Android/iOS token cleanup. Tests used a local-only PostgreSQL database and mock push providers.

The original workspace has an unrelated uncommitted EmailService edit removing sendReset; it was preserved and excluded from the tested clean-source copy. That local edit still breaks compilation of the dirty workspace and is not part of this release change.

- Backend tests use local PostgreSQL and mocked push providers, not real FCM/APNs.
- Verify a fresh live backup and rehearse the existing production migrations on a restored staging copy before deployment. Do not switch Render's database from render-test to the empty branch named production.
- Supply the new branch's required JWT/Maps and other production configuration securely. Keep the working signing identity; do not rotate it casually.
- Configure Android FCM V1 plus matching native Firebase client config, and iOS APNs/signing/provisioning in the existing EAS projects.
- Install native Android and iPhone builds; Expo Go is not the remote-push acceptance environment.
- With an approved staging deployment, verify actual delivery, tap, ownership, decline, acceptance and payment-gated completion on both physical devices. Check that Admin remains on its monitored ride when completion arrives.
- Enable production EXPO_PUSH_ENABLED only after the above gates and explicit rollout approval. No Render/Neon setting or live data was changed by this implementation.
