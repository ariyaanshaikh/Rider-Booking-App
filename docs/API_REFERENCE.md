# Rider Booking API and Database Reference

Updated 31 October 2026. Contract baseline: production-changes commit 72f640c. This guide describes the branch implementation, not a claim that it is deployed. Use it for Rider and Admin integration and staged acceptance testing.

The last verified Render deployment still used main at c7c43a7. Its public origin is https://rider-booking-app.onrender.com. Confirm the deployed revision before testing new endpoints. There is no global /api prefix: append the exact paths below to the selected origin. Local ports depend on PORT and the selected Spring profile.

## Environment and authorization

Send `Accept: application/json`, and `Content-Type: application/json` for JSON bodies. Protected HTTP endpoints require `Authorization: Bearer <login JWT>`. Never include JWTs, push tokens, OTPs, bank details or provider credentials in URLs or logs; existing ride-OTP query parameters must also be redacted from request logging.

ADMIN is the booking customer, not an operations administrator. RIDER is the driver account. Operations access requires the backend APP_OPERATIONS_USERNAMES allowlist. User ID, Rider profile ID and ride ID are different identifiers. Ownership is checked server-side; client-supplied IDs do not grant access.

Missing authentication can return 403; invalid/expired JWTs return 401. Security-filter errors may have empty or container-generated bodies, so do not require a JSON envelope for every failure.

## Account and recovery endpoints

| Method | Path | Access and input |
| --- | --- | --- |
| GET | /register | Public registration HTML |
| POST | /register | Public registration JSON |
| POST | /register/confirmToken | Public email and otp JSON |
| GET | /register/verifyEmail | Public emailAddress query for confirmation resend |
| POST | /register/verifyEmail | Public emailAddress JSON for confirmation resend |
| POST | /login | Public username and password JSON |
| GET | /api/auth/validate | JWT validation |
| POST | /auth/sendotp | Public password-reset email JSON |
| POST | /auth/verifyotp | Public password-reset email and otp JSON |
| POST | /auth/resetpassword | Public email, resetToken and password JSON |
| GET | /health | Public database readiness; UP or 503 DOWN |
| GET | /health/live | Public process liveness |

Registration example:

```json
{
  "firstName":"Test", "lastName":"Rider", "userName":"test_rider",
  "password":"<8 to 72 character password>", "email":"rider@example.com",
  "mobileNumber":"<phone>", "role":"RIDER"
}
```

The username alias is accepted for userName. Role is ADMIN or RIDER. IDs, enabled flags and confirmation fields cannot be chosen through registration. Registration returns success and a confirmation-email message; confirm with `{ "email":"rider@example.com", "otp":"<email code>" }`. Confirmation errors use 400 `{ "success":false, "message":"<reason>" }`.

Login body is `{ "username":"test_rider", "password":"<password>" }`. Success returns `{ "token":"<JWT>" }`. Validation returns `{ "valid":true, "username":"test_rider", "message":"Welcome back!" }`. A disabled account or wrong credentials is rejected.

Password reset is separate from registration confirmation:

1. POST /auth/sendotp with `{ "email":"rider@example.com" }`. Response: `{ "success":true, "message":"If this email belongs to an active account, a reset code will be sent" }`, including unknown accounts.
2. POST /auth/verifyotp with `{ "email":"rider@example.com", "otp":"123456" }`. Response: `{ "resetToken":"<one time capability>", "expiresInSeconds":600 }`.
3. POST /auth/resetpassword with `{ "email":"rider@example.com", "resetToken":"<capability>", "password":"<new password>" }`. Response: `{ "success":true, "message":"Password changed. Sign in again on your devices." }`.

Reset OTPs expire after 15 minutes. The verified reset capability expires after ten minutes and is single use. Passwords require 8–72 characters and at most 72 UTF-8 bytes. A successful reset revokes old JWTs/device registrations and closes old socket sessions. Reset codes remain readable in the database as requested; reset capabilities are hashed. Mail delivery is queued, not proof of immediate delivery.

Legacy routes /sendJWT, /demo, /otp/** and /register/updateUser are denied by SecurityConfig. Do not use them for login, SMS verification or password changes, even if old controller methods remain in source.

## Current user profile endpoints

| Method | Path | Access and response |
| --- | --- | --- |
| GET | /api/profile/me | JWT; own account summary and Rider summary when applicable |
| GET | /api/profile/me/rides | JWT; own assigned or booked ride history array |
| GET | /api/profile/me/active-ride | JWT; activeRide object or null |
| GET | /api/profile/me/invoices | JWT; own invoice array |
| GET | /api/profile/me/rider-location | RIDER; riderId and location object or null |
| GET | /api/profile/me/verification-details | RIDER; riderId and verificationDetails or null |

The account summary contains userId, firstName, lastName, userName, email, mobileNumber, role, enabled and operationsAccess. RIDER adds rider with riderId, name, mobileNumber, status, verificationSubmitted and verified, or null if no profile exists.

Ride summaries contain rideId, pickupLatitude, pickupLongitude, dropLatitude, dropLongitude, fare, distanceMeters, riderPaise, paymentStatus, chassisNo, vehicleName, riderId, adminId and rideStatus. Legacy rides may have null distance/derived earnings. The active response is `{ "activeRide":null }` when none exists; ADMIN additionally receives rideOtp for its active booking. The Rider response never exposes the ride OTP.

A pending unaccepted offer is not an assigned active ride. Use notification offer APIs for it. After a completed ride disappears from active-ride, recover its status from the authorized ride-history entry matching rideId. Do not replace it with another ride or reset the monitor merely because activeRide is null.

## Rider setup and location endpoints

| Method | Path | Access and input |
| --- | --- | --- |
| POST | /rider | RIDER; name, mobileNumber, optional status JSON |
| POST | /rider/{riderId}/verification-details | Owner or operator; private KYC and bank JSON |
| GET | /rider/{riderId}/verification-details | Owner or operator |
| PATCH | /rider/{riderId}/verification | Operator only; verified boolean query |
| GET | /rider/{riderId}/history | Owner or operator |
| POST | /rider/save/location | RIDER; latitude and longitude JSON |
| POST | /rider/fetch/livelocation | Assigned RIDER; latitude and longitude JSON |
| PATCH | /rider/status | RIDER; status=AVAILABLE or OFFLINE query |
| GET | /rider/nearby | ADMIN; lat and lon query |

POST /rider links the profile to the authenticated RIDER and returns 201. Do not supply userId. Verification submission returns 201; fields are aadhaarCardNumber, panCardNumber, drivingLicenseNumber, bankAccountNumber, bankIfscCode and bankAccountName. Only an allowlisted operator may mark it verified; an ordinary booking ADMIN cannot approve KYC.

Both location endpoints accept `{ "latitude":19.0, "longitude":72.0 }`, not a caller-selected riderId or rideId. Save/location works before assignment and returns RIDER_LOCATION_SAVED with the derived riderId/coordinates. Fetch/livelocation requires an assigned ACCEPTED, AWAITING_PAYMENT or STARTED ride and returns RIDER_LOCATION_UPDATED with derived riderId, rideId and coordinates; it also sends the booking Admin a private location event.

Nearby returns Rider IDs. Matching requires verified, AVAILABLE riders within 10 km with GPS newer than ten minutes. It is not restricted to five candidates. ON_TRIP is lifecycle-managed; availability changes during an active assignment are rejected.

## Booking and ride lifecycle

| Method | Path | Access and input |
| --- | --- | --- |
| POST | /ride/request | ADMIN; booking JSON, optional Idempotency-Key header |
| POST | /ride/accept | RIDER; rideId and own riderId queries |
| POST | /ride/verify-otp | Assigned RIDER; rideId, riderId and otp queries |
| POST | /ride/end | Assigned RIDER; rideId and riderId queries |
| POST | /ride/rider-cancel | Assigned RIDER; rideId and riderId queries |
| POST | /ride/cancel | Booking ADMIN; rideId query |
| GET | /api/fare/calculate | JWT; sLat, sLon, dLat and dLon queries |

Booking JSON:

```json
{
  "pickUpLatitude":19.0, "pickUpLongitude":72.0,
  "dropLatitude":20.0, "dropLongitude":73.0,
  "vehicleName":"bike", "chassisNo":"bike-id"
}
```

Booking uses pickUpLatitude/pickUpLongitude; profile/offer responses use pickupLatitude/pickupLongitude. The server derives booking ownership and fare; do not rely on submitted adminId/fare. Idempotency-Key accepts 8–100 letters, numbers, underscores or hyphens. Reusing it for the same booking returns the original ride; changing that booking's input conflicts. Success is plain text `Ride Request Sent, id=123`.

Accept success is plain text `Ride accepted`; 400 `Ride already taken` also covers unavailable, expired, unverified and no-valid-offer cases. Ownership mismatch is 403 and missing ride is 404. Acceptance requires AVAILABLE, verified ownership, no conflicting active ride, an unassigned REQUESTED ride and a SENT offer. Both ride and offer attempt must be within the dispatch window, default 15 minutes. Checking the offer timestamp prevents old offers being revived by migration-added ride timestamps. The first valid accept wins; competing offers become CANCELLED and the winner becomes ON_TRIP.

OTP verification success is plain text `OTP verified. Payment required before ride starts`. It moves ACCEPTED to AWAITING_PAYMENT, not STARTED. The ride-start OTP has no independent clock expiry; assignment/state/ownership rules still apply. Only a verified captured payment permits STARTED. Completion requires STARTED plus a captured payment and returns `Ride ended successfully`; it persists COMPLETED, makes the Rider AVAILABLE and creates one PAID invoice. Repeated completion is idempotent.

Rider cancellation is allowed only for its ACCEPTED assignment before OTP/payment begins and attempts a rematch. Customer cancellation is allowed only in REQUESTED or ACCEPTED. Neither endpoint is a refund workflow or a decline action. Paid/started cancellation is not allowed through these APIs.

PUT /ride/paymentCompleted is retired and returns 410 when reached with authentication and required input. Client-supplied payment status is not accepted. Use the verified payment APIs below.

Fare quote is JSON, not a placeholder string. At 19,000 route metres the response is `{ "distanceMeters":19000, "amountPaise":36100, "businessPaise":3610, "riderPaise":32490 }`. Pricing is INR 19/km with paise rounding, 10 percent gross platform share and 90 percent Rider share. Provider fees and taxes are separate accounting concerns.

## Notification and pending offer APIs

All seven endpoints below require JWT. Registration and inbox support ADMIN and RIDER; app must match account role. The pending-offer endpoints require RIDER. No mobile provider secrets are accepted here.

| Method | Path | Result |
| --- | --- | --- |
| PUT | /api/notifications/devices | Register or update this installation |
| DELETE | /api/notifications/devices/{app}/{installationId} | Deactivate own installation |
| GET | /api/notifications | Private inbox array, newest first, limit 50 |
| GET | /api/notifications/ride-requests | Valid pending-offer array, limit ten |
| GET | /api/notifications/ride-requests/{rideId} | Exact owned pending-offer object |
| POST | /api/notifications/ride-requests/{rideId}/decline | Persist a decline of this Rider's offer |
| GET | /api/notifications/delivery-status | Operator-only aggregate queue states |

### Device registration and logout

```json
{
  "app":"rider",
  "installationId":"7d07c752-f6fb-4897-91cb-79043a602212",
  "token":"ExpoPushToken[example12345678]",
  "platform":"android"
}
```

Use platform ios for iPhone; admin for the Admin app. installationId must match [A-Za-z0-9_-]{16,64}; a persistent installation UUID is suitable. Both ExpoPushToken[...] and ExponentPushToken[...] are accepted, with 8–150 allowed characters inside brackets. Token syntax validation does not verify the EAS project or delivery credentials.

PUT returns 200 `{ "registered":true }`. It upserts by app/installationId, updates token/platform/owner and reactivates the device. Repeating registration does not add duplicate device rows. One user may register Android and iOS installations simultaneously. Token transfer/rotation invalidates obsolete delivery targets; provider DeviceNotRegistered disables only the matching token, not another device or newer replacement. Registration is limited to 60 attempts per account per 15-minute window and replays recent inbox entries where applicable.

DELETE has no body and returns 200 `{ "unregistered":true }`, including already inactive/missing/other-owner targets without modifying another account's device. Call it before removing the JWT locally. Offline logout is best-effort and cannot recall an OS notification already in flight.

### Inbox and pending offer responses

Inbox example:

```json
[
  {"eventId":"<UUID>","type":"RIDE_REQUEST","rideId":123,
   "createdAt":"2026-10-31T13:19:32.449Z"}
]
```

Empty inbox/list is []. Historical inbox entries can remain after an offer expires. The inbox survives provider failure and is retained for 30 days.

The pending list contains objects with this shape; exact lookup returns the object directly, not an array:

```json
{
  "rideId":123,
  "pickupLatitude":19.0, "pickupLongitude":72.0,
  "dropLatitude":20.0, "dropLongitude":73.0,
  "fare":361.00, "vehicleName":"bike", "chassisNo":"bike-id"
}
```

Only this Rider's SENT offers for REQUESTED rides appear, with AVAILABLE status and fresh ride/offer timestamps. List order is newest offer first. Use exact lookup for a notification tap; it works beyond the ten-item list limit. It returns 404 for invalid, expired, declined, withdrawn, accepted or other-owner offers, with REQUEST_FAILED and message `Offer not available`. Never substitute another offer. Neither response includes the ride OTP, eventId, offerId, riderId or expiresAt.

### Persistent decline

POST /api/notifications/ride-requests/123/decline has no body. Success is 200 `{ "declined":true, "rideId":123 }`. The backend derives the Rider from JWT and changes only that offer to CANCELLED. It leaves the ride, competing offers, availability, payment and OTP untouched. Repeated dismissal of a cancelled offer is idempotent. A genuine later rematch can create a new offer attempt.

No owned offer gives 404 REQUEST_FAILED with `Offer not found`. Declining an accepted assignment gives 409 REQUEST_FAILED with `Offer is no longer pending`. Non-Rider gives 403. Accept and decline serialize on the ride lock, so decline cannot undo a winning acceptance. Do not use /ride/rider-cancel to reject an unaccepted offer.

### Push payload and device behavior

```json
{
  "title":"New Ride Request",
  "body":"Open the app for the latest ride details.",
  "data":{"eventId":"<UUID>","type":"RIDE_REQUEST",
          "rideId":123,"remote":true},
  "sound":"default", "priority":"high",
  "channelId":"ride-requests", "ttl":300
}
```

This is an illustrative Expo message without the private destination token; ttl is the remaining job lifetime, not always 300. Other events use ride-status; completion title is Ride Completed. No push payload includes OTPs, coordinates, KYC, bank data or JWTs. On receipt refresh state without unexpected navigation; on tap fetch the exact authorized offer/ride. Deduplicate socket/local/remote events by eventId.

Booking and completion commit inbox/jobs with the business transaction. Provider calls happen later, outside it. Delivery checks current owner, token, account enabled state, offer attempt, age and availability; stale jobs are cancelled. Request jobs expire after five minutes and other alerts after one hour. Sending polls every five seconds with leased concurrent-safe claims and at most eight attempts. Receipts start after 15 minutes, poll on a one-minute schedule and time out after 24 hours. DELIVERED means provider handoff receipt, not proof of phone display. Delivery is at-least-once; already handed-off messages can arrive after cancellation, so current-state validation is mandatory.

## Payments and checkout

| Method | Path | Authentication |
| --- | --- | --- |
| POST | /api/payments/rides/{rideId}/checkout-session | Booking ADMIN JWT |
| POST | /api/payments/rides/{rideId}/order | Booking ADMIN JWT |
| POST | /api/payments/rides/{rideId}/verify | Booking ADMIN JWT |
| GET | /api/payments/rides/{rideId} | Authorized booking Admin or assigned Rider JWT |
| POST | /api/payments/checkout/order | X-Checkout-Token capability |
| POST | /api/payments/checkout/verify | X-Checkout-Token capability |
| GET | /api/payments/checkout/status | X-Checkout-Token capability |
| POST | /api/payments/razorpay/webhook | Raw-body provider HMAC, not JWT |

Checkout-session returns `{ "checkoutUrl":"<origin>/payment-checkout.html#session=<capability>", "expiresInSeconds":600 }`. Do not put account JWTs in browser URLs. The checkout page calls the three capability endpoints with X-Checkout-Token. Invalid/expired capability gives 401. Treat the checkout URL fragment as a short-lived secret and do not log/share it publicly.

Order/status fields are rideId, amountPaise, businessPaise, riderPaise, currency, orderId, paymentStatus, transferStatus and rideStatus. Order additionally returns the public keyId. Status requires an existing payment record; do not assume it can synthesize a pre-order record.

Verification JSON:

```json
{
  "razorpay_order_id":"order_example",
  "razorpay_payment_id":"pay_example",
  "razorpay_signature":"<checkout signature>"
}
```

The backend validates stored order ownership, HMAC, provider capture, amount and currency before STARTED. Client success alone is insufficient. Webhook requires X-Razorpay-Signature plus x-razorpay-event-id and returns `{ "received":true }`; duplicate event IDs are handled transactionally. Do not alter the raw body before signature verification.

Route transfer processing remains separately disabled until approved/configured. Ride completion and customer capture are separate from Rider settlement. No manual-payout API was added. The paymentCompleted route cannot bypass these checks. Match provider test/live keys and webhook secrets; do not bypass payment gates for a completion test.

## WebSocket and event reference

Use SockJS endpoint /ws with STOMP CONNECT native header `Authorization: Bearer <JWT>`. The initial HTTP handshake is public so CONNECT can carry authentication; missing/invalid CONNECT credentials are rejected. Client STOMP SEND is denied; use authenticated HTTP mutation APIs. Subscriptions are ownership-checked, not public broadcasts.

| Destination | Authorized recipient |
| --- | --- |
| /topic/user/{userId} | That booking ADMIN account |
| /topic/rider/{riderId} | That RIDER profile owner |
| /topic/ride/{rideId} | Booking Admin or assigned Rider |
| /user/queue/ride/{rideId}/location | Booking Admin only |
| /user/queue/ride/{rideId}/events | Booking Admin or assigned Rider |

| Event type | Lifecycle meaning |
| --- | --- |
| RIDE_REQUEST | Persisted matching/rematching offer to a Rider |
| RIDE_ACCEPTED | Booking Admin sees assignment; other offer owners receive RIDE_CANCELLED |
| RIDER_CANCELLED | Booking Admin sees an allowed assigned-Rider cancellation |
| RIDE_CANCELLED_BY_USER | Assigned Rider or outstanding offer owners see customer cancellation |
| RIDE_CANCELLED | Losing offer or dispatch timeout; inspect current state |
| PAYMENT_REQUIRED | OTP passed; state AWAITING_PAYMENT |
| RIDE_STARTED | Verified captured payment allowed STARTED |
| RIDE_COMPLETED | Persisted completion/invoice; notify correct Admin and Rider |
| RIDER_LOCATION_UPDATED | Private location update; not a remote push type |

Socket RIDE_REQUEST uses pickupLat/pickupLon/dropLat/dropLon, unlike the pending HTTP response. The private Admin acceptance event can contain its ride OTP; remote push never does. Completion socket payload includes rideId, fare and invoiceId, with eventId on prepared private events. Refresh authorized history after reconnection and keep Admin's current monitor on the same completed ride. The backend never instructs navigation to a dashboard.

## Errors and retries

Application errors generally use `{ "timestamp":"<server timestamp>", "status":400, "error":"BAD_REQUEST", "message":"<safe reason>" }`. Validation adds details keyed by field. JSON ordering and timestamp timezone formatting are not contracts. Plain-text lifecycle errors, registration-confirmation errors and security-filter errors are exceptions to this envelope.

| Status | Typical interpretation |
| --- | --- |
| 400 | Invalid body/fields/OTP or unavailable acceptance |
| 401 | Invalid/expired JWT or checkout capability |
| 403 | Missing authentication or forbidden role/ownership |
| 404 | Missing ride or unavailable/not-owned offer |
| 409 | Conflicting state, accepted-offer decline or integrity conflict |
| 410 | Retired client payment-status endpoint |
| 429 | Rate limit; retry later, not an immediate loop |
| 500 | Unexpected server/database error |
| 503 | Readiness dependency or provider unavailable |

Use Idempotency-Key for booking retries and server state before retrying acceptance/payment. Provider timeouts do not authorize creating another payment or marking it captured. Network failure after an action may mean the action committed; reconcile rather than assuming failure.

## Database and release requirements

The last verified working target was Neon Rider Booking APP, project muddy-band-52686480, branch render-test, database neondb, schema public. The branch named production was empty. These are audit observations, not permission to change connections. Confirm the actual target again for deployment and never publish full connection strings/passwords.

The prod profile uses Flyway location classpath:db/production and history table production_schema_history, baseline version zero. V1 reconciles the foundational/payment schema and booking guards; V2 adds credential_version, password reset/mail, push and shared socket tables. Do not mix legacy db/migration V1–V12 with this separate lineage. Latest offer guards require no new migration or manual production data rewrite; they preserve the existing V1/V2 files.

| Tables | Purpose |
| --- | --- |
| users and confirmation_tokens | Account, role, credential version and registration confirmation |
| rider_details and rider_verification_details | Linked Rider profile, availability and private verification |
| rider_location | Latest Rider coordinates |
| ride_details and ride_request | Booking state and per-Rider offer attempts |
| invoices | One completion invoice per ride |
| ride_payments and rider_route_accounts | Capture, split accounting and trusted Route mappings |
| razorpay_webhook_events | Provider event deduplication |
| request_limits and checkout_sessions | Shared rate limits and scoped checkout capabilities |
| password_resets and reset_mail_jobs | Expiring recovery sessions and durable mail |
| push_devices | Unique token; primary key app and installation_id; active owner index |
| notification_inbox | Private event history; unique user_id and event_key; owner/time index |
| push_jobs | Leased delivery; unique event/device/token target and state/due index |
| socket_events and socket_deliveries and socket_nodes | Committed multi-instance socket fanout |
| production_schema_history | Applied Flyway versions/checksums, not a push queue |

Before live migration, take a fresh verified backup, restore/rehearse in staging, compare business data and confirm legacy active-ride handling. The retained 27 October recovery branch is not a fresh restore-tested backup. Changing code back does not undo database migrations or provider actions.

Required production configuration includes prod profile, secure datasource credentials, JWT_SECRET of at least 32 UTF-8 bytes, GOOGLE_MAPS_API_KEY and appropriate mail/public-base-URL settings. Preserve the working signing identity. Push defaults off through EXPO_PUSH_ENABLED=false. EXPO_ACCESS_TOKEN is backend-only and needed if enhanced Expo security requires it. Shared messaging defaults true in prod; PushWorker is an in-process scheduler, not a separately deployed Render queue service. Timely delivery requires an always-running backend.

Android needs matching FCM V1 credentials/native Firebase client configuration; iOS needs APNs, signing and provisioning. Use the existing Rider EAS project 5aecd6b2-417b-4a0e-907e-845cbe92174a with Android package and iOS bundle com.bikerider.rider. Provider secrets stay out of mobile/source control. Native physical-device builds are required for acceptance; Expo Go is not the remote-push test environment. See PUSH_FRONTEND_HANDOFF.md for integration details.

## Acceptance test sequence

1. Confirm staging revision/configuration, restore/rehearse V1/V2 and verify indexes and preserved business data.
2. Register/confirm/login separate Admin and Rider accounts; create the Rider profile, submit verification and have an operator approve it.
3. Make Rider AVAILABLE and save fresh GPS. Install native Android/iPhone builds, grant permission and register each installation against the matching backend.
4. Admin books with an Idempotency-Key. Verify owned SENT offers, inbox and durable jobs; test foreground, background and normally closed delivery on both phones.
5. Tap and fetch the exact pending offer. Test decline/retry, stale/accepted/other-owner rejection and a tapped offer beyond the normal list limit. Accept only the chosen valid offer.
6. Verify OTP, complete legitimate matching test-mode checkout/capture, observe STARTED, send private GPS and complete the assigned ride. Verify one PAID invoice and the correct Admin completion notification without dashboard reset.
7. Test duplicates, provider failures, invalid tokens, account switches, logout, permission denial, reconnect and worker interruption in staging. Distinguish OS force-stop restrictions from normally closed delivery.

The code baseline passed 79 backend tests and four preflight tests with GitHub CI success. Providers were mocked and PostgreSQL was local. These results are not proof of real Android/iPhone delivery, live migration or live checkout. Production activation remains subject to backup, configuration, deployment approval and physical-device testing.
