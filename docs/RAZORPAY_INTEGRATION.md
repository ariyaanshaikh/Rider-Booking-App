# RiderLink Razorpay integration

Implementation date: 26 October 2026. This document supersedes the older manual
`PUT /ride/paymentCompleted` workflow and the old OTP-starts-ride behavior.

## Fare and earnings

The backend fetches driving distance in meters using Google Distance Matrix when
creating a booking. It stores that distance and the fare. The UI's `fare`,
`adminId`, `riderId`, `rideId`, OTP and status fields do not control the booking.
The authenticated ADMIN account owns the booking.

- Fare = distance in km × ₹19, rounded HALF_UP to one paise.
- Business commission = 10% of the fare, rounded HALF_UP to one paise.
- Rider share = fare minus commission, so no paise is lost.
- Example: 19 km → ₹361.00 collected, ₹36.10 gross business commission,
  ₹324.90 rider share.

These are gross allocations from the supplied commission model. Gateway fees,
Route fees, taxes and operating costs are not deducted from the rider's 90% by
this implementation. Confirm accounting/tax treatment before production.

`GET /api/fare/calculate?sLat=...&sLon=...&dLat=...&dLon=...` now returns JSON:

```json
{"distanceMeters":19000,"amountPaise":36100,"businessPaise":3610,"riderPaise":32490}
```

Use the same coordinates to book with `POST /ride/request` and the booking-admin
JWT. Google distance is fetched again by the server at booking and frozen; a
displayed quote can differ if Google returns a different route. Never trust a
human-readable distance string or a client-supplied fare for payment.

## Ride and payment sequence

1. The assigned RIDER calls the existing `POST /ride/verify-otp?rideId=42&riderId=9&otp=1234`
   with their own JWT. The ride changes from ACCEPTED to AWAITING_PAYMENT.
   The rider remains ON_TRIP and cannot accept another booking.
2. The booking ADMIN calls `POST /api/payments/rides/42/order`. No request body is
   needed. The server commits a payment intent, creates a Razorpay order and
   returns its public key ID, order ID, amount, currency and allocation.
3. The admin's UI opens Razorpay Checkout with the returned values. Payment
   credentials are entered in Razorpay's hosted UI, not sent to this backend.
4. Checkout's handler posts the three Razorpay verification fields to the verify
   endpoint below. The server verifies the HMAC against its stored order ID and
   fetches the payment from Razorpay, checking order, amount, currency and capture.
5. Only a captured payment changes the ride to STARTED. A verified webhook can
   perform the same transition if the browser closes before its callback finishes.
6. The assigned rider ends the ride through `POST /ride/end`. The server requires
   a captured payment, marks the ride COMPLETED, creates one PAID invoice and
   queues the rider share as WAITING_ROUTE. Repeated completion is harmless.
7. When Route is enabled and the rider has an approved linked-account mapping,
   a background worker transfers only the stored 90% share. It distinguishes
   transfer processing from bank settlement.

## Payment API

All endpoints below use `Authorization: Bearer <JWT>` except the signed webhook.

| Method | Endpoint | Access |
|---|---|---|
| POST | `/api/payments/rides/{rideId}/order` | Booking ADMIN only |
| POST | `/api/payments/rides/{rideId}/verify` | Booking ADMIN only |
| GET | `/api/payments/rides/{rideId}` | Booking ADMIN or assigned RIDER |
| POST | `/api/payments/razorpay/webhook` | Razorpay HMAC signature and event ID |
| PUT | `/ride/paymentCompleted` | Retired, returns 410 |

Verification body:

```json
{
  "razorpay_order_id":"order_...",
  "razorpay_payment_id":"pay_...",
  "razorpay_signature":"..."
}
```

An order response includes:

```json
{
  "rideId":42,
  "amountPaise":36100,
  "businessPaise":3610,
  "riderPaise":32490,
  "currency":"INR",
  "orderId":"order_...",
  "paymentStatus":"ORDER_CREATED",
  "transferStatus":"NOT_ELIGIBLE",
  "rideStatus":"AWAITING_PAYMENT",
  "keyId":"rzp_test_..."
}
```

The GET status response has the same fields except `keyId`. Only the public key
ID is returned, never a key secret or webhook secret. A Razorpay Checkout success
callback alone is not proof of captured payment. If payment is still authorized,
the verify endpoint returns a conflict and the UI must wait for the captured
webhook, then refresh status.

For an integration screen, open `/payment-checkout.html`. Enter the booking-admin
JWT and ride ID after OTP verification. It keeps the token only in the tab's
memory. The mobile/web application should use its existing authenticated state
and the same API contract instead of asking users to paste tokens.

Private payment-confirmed `RIDE_STARTED` events are sent to the booking admin and
assigned rider at `/user/queue/ride/{rideId}/events`. Refresh the GET status or
`/api/profile/me/active-ride` after reconnecting: notifications are not a durable
source of payment truth. OTP verification publishes PAYMENT_REQUIRED through the
existing lifecycle topics. Those topics are hints only and do not authorize money
movement. Active-ride/location queries include AWAITING_PAYMENT.

## Configuration

Configure locally through the process environment or the IDE's run configuration,
and on Render through Environment. Do not commit real values.

```dotenv
JWT_SECRET=<a newly generated random secret of at least 32 bytes>
GOOGLE_MAPS_API_KEY=<server-side Google key with Distance Matrix enabled>
RAZORPAY_ENABLED=false
RAZORPAY_KEY_ID=<test key ID>
RAZORPAY_KEY_SECRET=<test key secret>
RAZORPAY_WEBHOOK_SECRET=<separate webhook signing secret>
RAZORPAY_ROUTE_ENABLED=false
```

`JWT_SECRET` is now required. The old hard-coded signing key is no longer used;
old tokens must be replaced by signing in again. `/login` is public for normal
username/password authentication. The arbitrary `/sendJWT`, unused SMS OTP
endpoints and demo data-creation route are denied. This closes token-issuance
bypasses before enabling payment APIs.

Set `RAZORPAY_ENABLED=true` only after adding matching Test Mode keys and a
webhook. Use automatic capture in Razorpay's capture settings. Configure the
webhook URL to the deployed service's `/api/payments/razorpay/webhook`, with a
separate secret matching `RAZORPAY_WEBHOOK_SECRET`. Subscribe to `payment.captured`,
`order.paid`, `refund.processed`, `transfer.processed` and `transfer.failed`.
Razorpay supplies `X-Razorpay-Signature` and `x-razorpay-event-id`.

Route remains disabled while support ticket #21549397 is pending. After approval,
onboard each rider through Razorpay and verify their linked bank/KYC details.
Only trusted business operations should add `rider_route_accounts` mappings and
set `approved=true` after approval. Application ADMIN users are paying customers,
so no customer-facing endpoint can alter payout destinations.

Enable `RAZORPAY_ROUTE_ENABLED` only after end-to-end testing with approved test
linked accounts and a matching environment. Do not point a local instance and
Render at different databases while both actively process the same merchant's
webhooks/transfers. Use separate merchant test/live credentials and webhook URLs.

## Reliability and operational limits

- Unique ride, order, payment, transfer and event IDs prevent duplicate records.
- Database row locks serialize OTP/start/completion and payment transitions.
- Payment amounts, currency, order ownership and HMAC signatures are checked.
- Provider calls have finite timeouts. Order creation uncertainty is recovered
  using its unique receipt. A retry does not blindly create another order.
- Transfer intent commits before the API call. Unknown transfer outcomes enter
  RECONCILE. The worker searches provider transfers before attempting any new
  transfer and never automatically resubmits an uncertain/failed transfer.
- A finance operator must resolve definitive provider failures or unrecoverable
  intents. Do not reset a SUBMITTING/RECONCILE row merely because a request timed
  out. Verify provider state first. This conservative behavior prevents duplicates.
- PROCESSED means the transfer processed, not bank credit. The worker polls the
  provider's transfer settlement status before displaying SETTLED.
- After OTP verification, cancellation/rematching endpoints return a conflict.
  Until refund/cancellation rules are agreed, use support/finance reconciliation.
  Captures for a cancelled/non-payable ride are recorded as REFUND_REQUIRED and
  do not restart the ride. Refunds processed in the provider dashboard are tracked;
  partial refunds block automatic transfers and require review. This version
  does not initiate refunds, resolve disputes or reverse completed transfers.
- Existing historical rides are not repriced or retroactively marked paid.
  New checkout requires a server-priced booking. The new flow must be tested
  with a new booking, not an old STARTED ride without a payment ledger record.
- Route fees, balance requirements and settlement timing remain subject to
  Razorpay approval. Delayed transfers require sufficient eligible funds.

## Database rollout

`V10__razorpay_payments.sql` adds distance/payment columns, payment ledger,
verified Route account mapping and webhook deduplication tables. It expands
ride/invoice checks without rewriting historical statuses. Legacy mixed-case
rows are preserved using NOT VALID checks; new writes are checked.

For a fresh database, run the base setup SQL, then V10. Existing local databases
also need V6, V7 and V9 if verification, account ownership or vehicle columns are
missing. Render has Flyway disabled, so apply the checked-in migration to the
actual connected branch (`render-test`) explicitly. Never apply the entire base
setup file to a populated database.

## Verification

Run the unit and application checks with a configured local PostgreSQL:

```sh
PAYMENT_TEST_DB_URL=jdbc:postgresql://localhost:5436/ariyaanshaikh ./mvnw \
  -Dtest=FarePolicyTest,RazorpaySecurityTest,RidePaymentFlowTest,PaymentStorePostgresTest,PaymentApplicationContextTest,RidePaymentProviderTest test
```

Postgres integration tests create and remove only generated `payment_test_*`
schemas. They do not call Razorpay, send emails, create real payments or modify
application data. Razorpay Test Mode checkout, webhook delivery and Route
transfers still require actual account credentials and approval before live use.

Official references: [Checkout](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/),
[webhooks](https://razorpay.com/docs/webhooks/validate-test/),
[payment-linked transfers](https://razorpay.com/docs/api/payments/route/create-transfers-payments/).
