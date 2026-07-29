# Quick Reference Guide - Rider Booking App

## 🚀 Application Startup Flow

```
PracticeApplication.main()
    ↓
@SpringBootApplication scans packages
    ↓
SecurityConfig registers:
  - AuthenticationManager
  - SecurityFilterChain (JWT filter)
  - CustomUserDetailsService
    ↓
WebSocketConfig registers:
  - STOMP endpoint at /ws
  - JwtChannelInterceptor (for WebSocket JWT auth)
    ↓
Flyway runs migrations (V1, V2, V3, V4)
    ↓
Spring Boot starts Tomcat on port 8080
    ↓
Ready to accept requests!
```

---

## 🔐 Authentication Flow (HTTP)

```
CLIENT REQUEST
  │
  ├─ Extract "Authorization: Bearer <JWT>" header
  │
  └─→ JwtFilter.doFilterInternal()
      ├─ Extract token (remove "Bearer " prefix)
      ├─ JwtUtils.extractUsername(token) → "johndoe"
      ├─ CustomUserDetailsService.loadUserByUsername("johndoe") → UserDetails
      ├─ JwtUtils.validateToken(token, userDetails) → true/false
      │
      ├─ IF VALID:
      │  └─ SecurityContextHolder.setAuthentication(auth)
      │     → Request proceeds with authenticated user
      │
      └─ IF INVALID:
         └─ SecurityContextHolder remains empty
            → Request proceeds but 403 at controller @Secured
```

---

## 🔐 WebSocket Authentication Flow

```
CLIENT CONNECTS: ws://localhost:8080/ws
  │
  └─→ JwtChannelInterceptor.preSend()
      ├─ Check for STOMP CONNECT command
      ├─ Extract "Authorization: Bearer <JWT>" from native headers
      ├─ Extract token
      ├─ JwtUtils.extractUsername(token) → "johndoe"
      ├─ CustomUserDetailsService.loadUserByUsername("johndoe") → UserDetails
      ├─ JwtUtils.validateToken(token, userDetails) → true/false
      │
      ├─ IF VALID:
      │  └─ StompHeaderAccessor.setUser(auth)
      │     → Principal set, can now subscribe to topics
      │
      └─ IF INVALID:
         └─ Log debug message
            → CONNECT allowed but no Principal
```

---

## 📱 Ride Booking Flow (Start to Finish)

```
1. CUSTOMER BOOKS RIDE
   POST /ride/request with { pickup, dropoff, fare, userId }
   │
   └─→ RideDetailsService.saveRideDetials()
       ├─ Generate OTP: 1000-9999
       ├─ Set otpExpiresAt: now + 10 min
       ├─ Save ride: status=REQUESTED
       ├─ RiderMatchingService.findNearestRiders()
       │  └─ SQL: Haversine formula, filter updated_at > 30s ago, LIMIT 5
       ├─ Create 5 RideRequest rows: status=SENT
       ├─ Broadcast to 5 riders: /topic/rider/{id}
       └─ Return rideId


2. RIDERS RECEIVE NOTIFICATION (WebSocket)
   Message: { type: RIDE_REQUEST, rideId, pickup, dropoff, fare }


3. RIDER ACCEPTS RIDE
   POST /ride/accept?rideId={id}&riderId={rid}
   │
   └─→ RideService.acceptRide()
       ├─ Atomic UPDATE: SET riderId WHERE riderId IS NULL
       ├─ If updated=0 → return false (already taken)
       ├─ If updated=1 → continue...
       ├─ Update RideRequest rows: winner→ACCEPTED, others→CANCELLED
       ├─ Notify user: /topic/user/{userId}
       │  Message: { type: RIDE_ACCEPTED, riderId, otp }
       ├─ Notify other riders: /topic/rider/{id}
       │  Message: { type: RIDE_CANCELLED, rideId }
       └─ Return true


4. RIDER SENDS LOCATION UPDATES (continuous)
   POST /rider/save/location
   POST /rider/fetch/livelocation
   │
   └─→ LiveLocationTrackingService
       ├─ saveRiderCurrentLocation(): UPSERT to DB
       └─ sendLocationUpdate(): Broadcast to /topic/ride/{id}


5. RIDER VERIFIES OTP AND STARTS RIDE
   POST /ride/verify-otp?rideId={id}&riderId={rid}&otp={otp}
   │
   └─→ RideService.startRide()
       ├─ Validate: rideStatus=ACCEPTED
       ├─ Validate: riderId matches
       ├─ Validate: OTP not expired (> 10 min)
       ├─ Validate: OTP matches (== stored)
       ├─ Update: rideStatus=STARTED
       ├─ Update: rider.status=ON_TRIP
       ├─ Notify: /topic/ride/{id}: { type: RIDE_STARTED }
       ├─ Notify: /topic/user/{id}: { type: RIDE_STARTED }
       └─ Return true


6. RIDER COMPLETES DELIVERY
   POST /ride/end?rideId={id}&riderId={rid}
   │
   └─→ RideService.endRide()
       ├─ Validate: rideStatus=STARTED
       ├─ Validate: riderId matches
       ├─ Update: rideStatus=COMPLETED
       ├─ Update: rider.status=AVAILABLE
       ├─ Create invoice: status=PENDING
       ├─ Notify: /topic/ride/{id}:
       │  { type: RIDE_COMPLETED, fare, invoiceId }
       ├─ Notify: /topic/user/{id}:
       │  { type: RIDE_COMPLETED, fare, invoiceId }
       └─ Return true


7. CUSTOMER SEES INVOICE & PAYS (async)
   Invoice worker reads PENDING invoices
   │
   └─→ Call Stripe/Razorpay
       ├─ Process payment
       ├─ Update invoice: status=SETTLED
       └─ Notify customer
```

---

## 🛑 Cancellation Flows

### **Scenario A: Rider Cancels After Accepting**

```
POST /ride/rider-cancel?rideId={id}&riderId={rid}
│
└─→ RideService.riderCancel()
    ├─ Validate: riderId matches assigned
    ├─ Update ride: status=RIDER_CANCELLED, riderId=0
    ├─ Notify user: /topic/user/{id}
    │  Message: { type: RIDER_CANCELLED, riderId }
    │
    ├─ REMATCH LOGIC:
    │  ├─ Get existing RideRequest rows
    │  ├─ Build exclusion list: [101, 102, 103, 104, 105] (already notified)
    │  ├─ Find next 5 nearest riders: [106, 107, 108, 109, 110]
    │  ├─ Create NEW RideRequest rows for new riders
    │  ├─ Notify new riders: /topic/rider/{id}
    │  │  Message: { type: RIDE_REQUEST, rideId, ... }
    │  │
    │  └─ Set ride back to: status=REQUESTED
    │
    └─ Result: Fresh batch of riders get chance to accept
```

### **Scenario B: User Cancels Ride**

```
POST /ride/cancel?rideId={id}
│
└─→ RideService.userCancel()
    ├─ Update ride: status=ADMIN_CANCELLED
    │
    ├─ If riderId > 0 (rider assigned):
    │  └─ Notify rider: /topic/rider/{id}
    │     Message: { type: RIDE_CANCELLED_BY_USER, rideId }
    │
    ├─ Update all RideRequest rows: status=CANCELLED
    ├─ Mark rider: status=AVAILABLE (if assigned)
    ├─ Handle refund (placeholder): invoiceStatus=REFUNDED
    │
    └─ Result: Ride terminated, rider freed up
```

---

## 🎯 Rider Matching Algorithm

```
RiderMatchingService.findNearestRiders(lat, lon)
│
└─→ RiderMatchingRepository.findNearestRiders()
    │
    └─→ Native SQL Query:
    
        SELECT rl.rider_id, rl.current_latitude, rl.current_longitude,
        (
            6371 * acos(
                cos(radians(:lat)) *
                cos(radians(rl.current_latitude)) *
                cos(radians(rl.current_longitude) - radians(:lon)) +
                sin(radians(:lat)) *
                sin(radians(rl.current_latitude))
            )
        ) AS distance
        FROM rider_location rl
        WHERE rl.updated_at > NOW() - INTERVAL '30 seconds'
        ORDER BY distance
        LIMIT 5
    
    ├─ Haversine formula calculates distance (in km)
    ├─ Filters: only locations updated in last 30 seconds
    ├─ Ordering: nearest first
    ├─ Limit: return top 5 riders
    │
    └─ Returns: List<Integer> of 5 rider IDs (or less if < 5 available)


RideDetailsService.saveRideDetials() uses this to:
    ├─ Get rider IDs: [101, 102, 103, 104, 105]
    ├─ Create RideRequest rows (5 rows)
    └─ Notify each rider via WebSocket
```

---

## 🔄 Atomic Accept Pattern (Race Condition Safe)

```
SCENARIO: 2 riders try to accept same ride simultaneously

RIDE_DETAILS table (before):
┌─────────┬──────────┬──────────────┐
│ ride_id │ rider_id │ ride_status  │
├─────────┼──────────┼──────────────┤
│    5    │  NULL    │ REQUESTED    │
└─────────┴──────────┴──────────────┘


THREAD 1 (Rider 101):
    UPDATE RIDE_DETAILS 
    SET rider_id = 101, ride_status = 'ACCEPTED'
    WHERE ride_id = 5 AND (rider_id IS NULL OR rider_id = 0)
    
    ↓ LOCK TABLE (transaction isolation)
    ↓ Execute UPDATE
    ↓ 1 row affected → Return 1
    ↓ Unlock table


THREAD 2 (Rider 102):
    UPDATE RIDE_DETAILS 
    SET rider_id = 102, ride_status = 'ACCEPTED'
    WHERE ride_id = 5 AND (rider_id IS NULL OR rider_id = 0)
    
    ↓ Wait for lock release
    ↓ LOCK TABLE
    ↓ Execute UPDATE (now rider_id = 101, not NULL!)
    ↓ 0 rows affected → Return 0
    ↓ Unlock table


RESULT AFTER BOTH COMPLETE:
┌─────────┬──────────┬──────────────┐
│ ride_id │ rider_id │ ride_status  │
├─────────┼──────────┼──────────────┤
│    5    │  101     │ ACCEPTED     │
└─────────┴──────────┴──────────────┘

✓ Only Rider 101 succeeds (gets return 1)
✓ Rider 102 gets return 0 (knows ride taken)
✓ No duplicate updates!
```

---

## 🕐 OTP Expiry Mechanism

```
CREATION:
    RideDetailsService.saveRideDetials()
    ├─ Generate OTP: random 1000-9999
    ├─ Set otpExpiresAt: LocalDateTime.now().plusMinutes(10)
    └─ Example: Created at 10:30:00 → Expires at 10:40:00


VERIFICATION (at any time):
    RideService.startRide()
    ├─ Check: if (LocalDateTime.now().isAfter(ride.getOtpExpiresAt()))
    │
    ├─ At 10:39:59:
    │  └─ isAfter(10:40:00) = false → OTP VALID ✓
    │
    ├─ At 10:40:00:
    │  └─ isAfter(10:40:00) = true → OTP EXPIRED ✗
    │
    └─ At 10:40:01:
       └─ isAfter(10:40:00) = true → OTP EXPIRED ✗


EXCEPTION HANDLING:
    if (OTP expired) {
        throw OtpExpiredException("OTP has expired...")
        → GlobalExceptionHandler catches it
        → Returns 400 { error: "OTP_EXPIRED" }
    }


NO ATTEMPT LIMITING:
    ❌ NOT IMPLEMENTED (per user requirement)
    ⚠️  Vulnerable to brute force!
    💡 Solution: Rate limit at API gateway (not in code)
```

---

## 📊 Database Query Examples

### **Find Nearest Riders:**
```sql
-- Find 5 nearest riders within 30-second location freshness
SELECT rider_id, distance FROM (
    SELECT rl.rider_id,
        6371 * acos(
            cos(radians(19.076)) *
            cos(radians(rl.current_latitude)) *
            cos(radians(rl.current_longitude) - radians(72.8777)) +
            sin(radians(19.076)) *
            sin(radians(rl.current_latitude))
        ) AS distance
    FROM rider_location rl
    WHERE rl.updated_at > NOW() - INTERVAL '30 seconds'
) sub
ORDER BY distance
LIMIT 5;
```

### **Atomic Accept:**
```sql
-- Only succeeds if rider_id is still NULL
UPDATE ride_details 
SET rider_id = 102, ride_status = 'ACCEPTED'
WHERE ride_id = 5 AND (rider_id IS NULL OR rider_id = 0);

-- Check rows affected:
-- 1 = Success (accepted)
-- 0 = Failure (already taken)
```

### **UPSERT Location:**
```sql
-- Always keeps latest location per rider
INSERT INTO rider_location (rider_id, current_latitude, current_longitude, updated_at)
VALUES (102, 19.0765, 72.8781, CURRENT_TIMESTAMP)
ON CONFLICT (rider_id) 
DO UPDATE SET 
    current_latitude = EXCLUDED.current_latitude,
    current_longitude = EXCLUDED.current_longitude,
    updated_at = CURRENT_TIMESTAMP;
```

---

## 🌐 WebSocket Topics & Messages

### **User Topics (Customer receives):**
```
Topic: /topic/user/{userId}

Message Types:
├─ { type: "RIDE_ACCEPTED", riderId: 102, rideId: 5, otp: 1234 }
├─ { type: "RIDE_STARTED", rideId: 5 }
├─ { type: "RIDE_COMPLETED", rideId: 5, fare: 250.50, invoiceId: 42 }
├─ { type: "RIDER_CANCELLED", rideId: 5, riderId: 102 }
└─ { type: "RIDE_CANCELLED_BY_USER", rideId: 5 }
```

### **Rider Topics (Rider receives):**
```
Topic: /topic/rider/{riderId}

Message Types:
├─ { type: "RIDE_REQUEST", rideId: 5, pickupLat: 19.076, ..., fare: 250.50 }
├─ { type: "RIDE_CANCELLED", rideId: 5 }  (if another rider accepted)
└─ { type: "RIDE_CANCELLED_BY_USER", rideId: 5 }
```

### **Ride Topics (Both user and rider receive):**
```
Topic: /topic/ride/{rideId}

Message Types:
├─ { type: "RIDE_STARTED", rideId: 5 }
├─ { lat: 19.077, lon: 72.879, riderId: 102 }  (location updates)
└─ { type: "RIDE_COMPLETED", rideId: 5, fare: 250.50, invoiceId: 42 }
```

---

## 💾 Transaction & Consistency Guarantees

### **RideDetailsService.saveRideDetials():**
```
@Transactional
├─ CREATE ride record (RIDE_DETAILS)
├─ FIND nearest riders (SELECT query)
├─ CREATE 5 ride_request records
├─ BROADCAST messages (side effects)

IF ANY STEP FAILS → ROLLBACK ALL
├─ Ride deleted
├─ Requests deleted
├─ Messages NOT sent (can't rollback WebSocket)

ENSURES: No orphaned ride_requests without ride!
```

### **RideService.acceptRide():**
```
@Transactional
├─ ATOMIC UPDATE ride (only one succeeds)
├─ UPDATE ride_request rows
├─ BROADCAST messages

IF UPDATE RETURNS 0 → Early return (not transactional anymore)
IF UPDATE RETURNS 1 → Continue within transaction

ENSURES: Ride + requests always in sync!
```

---

## 🔑 Key Constraints & Validations

### **RideDetailsDTO Validations:**
```
@NotNull → pickUpLatitude, pickUpLongitude, dropLatitude, dropLongitude, fare, adminId
@Positive → fare > 0
```

### **RiderLocationDTO Validations:**
```
@NotNull → rideId, riderId, latitude, longitude
```

### **Entity Constraints:**
```
RideDetailsEntity:
  - riderId: nullable (NULL until accepted)
  - rideStatus: must be in [REQUESTED, ACCEPTED, STARTED, COMPLETED, CANCELLED]
  - otpExpiresAt: must be > createdAt

RideRequestEntity:
  - status: must be in [SENT, ACCEPTED, CANCELLED]
  - rideId + riderId: combination unique (one request per rider per ride)

RiderLocationEntity:
  - riderId: PRIMARY KEY (unique, latest location kept)
```

---

## 🚦 API Response Codes

```
200 OK
  ├─ POST /ride/request → ride booked
  ├─ POST /ride/accept → accepted successfully
  ├─ POST /ride/verify-otp → OTP verified
  └─ POST /ride/end → ride ended

400 BAD REQUEST
  ├─ OtpExpiredException → "OTP_EXPIRED"
  ├─ InvalidRideStatusException → "INVALID_RIDE_STATUS"
  ├─ OTP mismatch → "OTP is incorrect"
  └─ Validation failed → "VALIDATION_FAILED" + field errors

404 NOT FOUND
  └─ RideNotFoundException → "RIDE_NOT_FOUND"

409 CONFLICT
  ├─ RiderNotAssignedException → "RIDER_NOT_ASSIGNED"
  └─ POST /ride/accept when already taken → 400 "Ride already taken"

500 INTERNAL SERVER ERROR
  └─ Any unhandled exception → "INTERNAL_SERVER_ERROR"
```

---

## 📚 File-to-Responsibility Mapping

| Responsibility | Primary File | Secondary Files |
|---|---|---|
| HTTP Security | SecurityConfig | JwtFilter, JwtUtils |
| WebSocket Security | WebSocketConfig | JwtChannelInterceptor |
| Ride Lifecycle | RideService | RideDetailsService |
| Matching Algorithm | RiderMatchingService | RiderMatchingRepository |
| Location Updates | LiveLocationTrackingService | RiderLocationRepository |
| User Authentication | CustomUserDetailsService | User, UserRepository |
| Error Handling | GlobalExceptionHandler | Custom Exception classes |
| REST Endpoints | RideRequestController | RideDetailsService, RideService |
| Data Models | Entity classes | Repository interfaces |
| Data Transfer | DTO classes | (none) |
| Ride Booking | RideDetailsService | RideDetailsEntity, RideDetailsRepository |
| Invoice | RideService, InvoiceRepository | InvoiceEntity |

---

## 🎓 How to Extend

### **Add Surge Pricing:**
```
Edit: RideDetailsService.saveRideDetials()
├─ After calculating fare from distance
├─ Check demand level (rides in progress)
├─ Apply multiplier: fare = fare * surgeFactor
└─ Save adjusted fare
```

### **Add Rating System:**
```
Create: RatingEntity, RatingRepository, RatingService
├─ POST /ride/{id}/rate?riderId={rid}&rating={1-5}
├─ Save RatingEntity
├─ Update rider average rating
└─ Broadcast rating via WebSocket
```

### **Add Scheduled Cleanup:**
```
Create: ScheduledTasks.java
├─ @Scheduled(fixedDelay = 60000)
├─ Delete rides with status=REQUESTED older than 5 minutes
├─ Delete stale RiderLocation entries (updated_at > 1 hour)
└─ Settle pending invoices
```

### **Add Payment Processing:**
```
Create: PaymentService, PaymentWorker
├─ @Scheduled(fixedDelay = 5000)
├─ Query invoices WHERE status=PENDING
├─ Call Stripe/Razorpay API
├─ Update invoice status to SETTLED
└─ Notify customer via email
```

---

Done! This guide should help you understand every aspect of your application! 🎉

