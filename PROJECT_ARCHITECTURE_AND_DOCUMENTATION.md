# Bike Rider Booking Application - Complete Architecture & Documentation

## 📋 Project Overview

This is a production-grade, monolithic backend for a **Rider Booking Application** (NOT a cab booking app). The system matches customers with available riders who will pick up the customer's bike, ride it to the destination, and hand it over.

**Tech Stack:**
- Java 21, Spring Boot 3.x, Spring Data JPA
- PostgreSQL (single database, no Kafka/Redis/Microservices)
- Spring Security with JWT Authentication
- Spring WebSocket (STOMP) for real-time notifications
- Maven build system
- Lombok, Swagger for documentation

---

## 🏗️ Project Architecture Layers

The application follows **Clean Architecture** with clear separation of concerns:

```
Controller (REST & WebSocket endpoints)
    ↓
Service (Business Logic & Orchestration)
    ↓
Repository (Data Access)
    ↓
Database (PostgreSQL)
```

### Supporting Layers:
- **Config**: Security, JWT, WebSocket configuration
- **Exception**: Custom domain exceptions and global exception handling
- **Entity**: JPA persistence models
- **DTO**: Data Transfer Objects for request/response payloads
- **Mapper**: Data transformation between DTOs and Entities

---

## 1️⃣ CONFIG LAYER - Security & Infrastructure Configuration

### 📄 File: `config/PracticeApplication.java`
**Purpose:** Spring Boot application entry point.

**Key Components:**
```java
@SpringBootApplication  // Auto-configures Spring Boot
@EnableAsync            // Enables asynchronous method execution
public class PracticeApplication {
    public static void main(String[] args) {
        SpringApplication.run(PracticeApplication.class, args);
    }
}
```

**Usage:** Boots the entire Spring Boot application.

---

### 📄 File: `config/SecurityConfig.java`
**Purpose:** Configures HTTP security, authentication manager, and JWT filter.

**Key Methods:**

| Method | Purpose |
|--------|---------|
| `authenticationManager(...)` | Creates AuthenticationManager bean for authenticating users |
| `securityFilterChain(...)` | Configures HTTP security rules and filter chain |

**Important Configuration Details:**
```java
// CSRF disabled - JWT is stateless
.csrf(csrf -> csrf.disable())

// Permissive routes (no JWT required)
.requestMatchers("/register/**", "/otp/**", "/demo", "/error", "/sendJWT").permitAll()

// All other routes require authentication
.anyRequest().authenticated()

// Stateless session - no server-side sessions
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

// Add JWT filter before UsernamePasswordAuthenticationFilter
.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
```

**How it Works:**
1. Requests come in with `Authorization: Bearer <JWT>`
2. `JwtFilter` extracts the token and validates it
3. If valid, creates a SecurityContext with the authenticated user
4. If invalid or missing (on protected routes), returns 403 Forbidden

---

### 📄 File: `config/JwtUtils.java`
**Purpose:** JWT token generation, parsing, and validation.

**Key Methods:**

| Method | Purpose | Returns |
|--------|---------|---------|
| `generateToken(String username)` | Creates a signed JWT with 30-day expiry | JWT String |
| `extractUsername(String token)` | Extracts subject (username) from token | username |
| `extractClaim(String token, Function<Claims, T> resolver)` | Extracts any claim from token | Generic type T |
| `validateToken(String token, UserDetails userDetails)` | Validates token signature and expiry | boolean |
| `isTokenExpired(String token)` | Checks if token expiry date is before now | boolean |

**Token Structure:**
```
Header: { "alg": "HS256", "typ": "JWT" }
Payload: { "sub": "username", "iat": 1234567890, "exp": 1234567890 }
Signature: HMAC-SHA256(base64UrlEncode(header) + "." + base64UrlEncode(payload), SECRET_KEY)
```

**Example Flow:**
1. User logs in with username/password
2. `JwtUtils.generateToken(username)` creates a JWT with 30-day expiry
3. Client stores this JWT
4. On subsequent requests, client sends `Authorization: Bearer <JWT>`
5. `JwtUtils.validateToken(token, userDetails)` verifies the token is valid

---

### 📄 File: `config/JwtFilter.java`
**Purpose:** HTTP servlet filter that intercepts every HTTP request and validates JWT.

**Key Method: `doFilterInternal(...)`**

**Execution Flow:**
```
1. Extract "Authorization" header from request
2. Check if header starts with "Bearer "
3. Extract token (everything after "Bearer ")
4. Use JwtUtils.extractUsername(token) to get username
5. Load UserDetails from CustomUserDetailsService
6. Validate token with JwtUtils.validateToken(token, userDetails)
7. If valid:
   - Create UsernamePasswordAuthenticationToken
   - Set it in SecurityContextHolder
8. Pass request to next filter in chain
```

**Example Request:**
```
GET /ride/request
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Result:**
- If JWT is valid → Request proceeds, user is authenticated
- If JWT is invalid/expired → Request proceeds but user is NOT authenticated → 403 Forbidden at controller level
- If no JWT → Request proceeds but user is NOT authenticated → 403 Forbidden

---

### 📄 File: `config/WebSocketConfig.java`
**Purpose:** Configures WebSocket endpoints and STOMP message broker.

**Key Methods:**

| Method | Purpose |
|--------|---------|
| `configureMessageBroker(MessageBrokerRegistry)` | Sets up simple in-memory broker and app prefix |
| `registerStompEndpoints(StompEndpointRegistry)` | Creates WebSocket endpoint at `/ws` |
| `configureClientInboundChannel(ChannelRegistration)` | Registers JWT interceptor for STOMP frames |

**Configuration Details:**
```java
// Enable in-memory message broker
registry.enableSimpleBroker("/topic");

// Client sends messages to /app prefix
registry.setApplicationDestinationPrefixes("/app");

// WebSocket endpoint
registry.addEndpoint("/ws")
    .setAllowedOriginPatterns("*")  // Allow all origins (secure in production)
    .withSockJS();  // Enable SockJS fallback for older browsers
```

**How STOMP Works:**
1. Client connects to `ws://localhost:8080/ws` with JWT in headers
2. `JwtChannelInterceptor` authenticates the STOMP CONNECT frame
3. Client can then:
   - **Subscribe** to `/topic/user/{userId}` to receive messages
   - **Send** to `/app/...` to trigger server-side message handlers
4. Server broadcasts to subscribers via `messagingTemplate.convertAndSend(...)`

---

### 📄 File: `config/JwtChannelInterceptor.java`
**Purpose:** Intercepts STOMP CONNECT frames and authenticates users with JWT.

**Key Method: `preSend(Message<?>message, MessageChannel channel)`**

**Execution Flow:**
```
1. Wrap message in StompHeaderAccessor
2. Check if command is STOMP CONNECT
3. Look for "Authorization" header in native headers (case-insensitive)
4. Extract Bearer token from header (format: "Bearer <JWT>")
5. Extract username using JwtUtils.extractUsername(token)
6. Load UserDetails using CustomUserDetailsService
7. Validate token with JwtUtils.validateToken(...)
8. If valid:
   - Create UsernamePasswordAuthenticationToken
   - Set it as Principal via accessor.setUser(auth)
   - Return message (authentication succeeds)
9. If invalid:
   - Log debug message
   - Return message unchanged (no Principal set)
```

**Example WebSocket Client Code (JavaScript):**
```javascript
import { Client } from '@stomp/stompjs';

const client = new Client({
    brokerURL: 'ws://localhost:8080/ws',
    connectHeaders: {
        Authorization: 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...'
    }
});

client.onConnect = (frame) => {
    // Now authenticated! Subscribe to user topic
    client.subscribe('/topic/user/123', (message) => {
        console.log('Received:', message.body);
    });
};

client.activate();
```

**Key Point:**
- HTTP requests use `JwtFilter` to validate JWT
- WebSocket connections use `JwtChannelInterceptor` to validate JWT
- Both use the same `JwtUtils` and `CustomUserDetailsService` for consistency

---

### 📄 File: `config/PasswordConfig.java`
**Purpose:** Provides password encoding bean.

**Key Bean:**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**Usage:**
- Register: Password is encoded with BCrypt before saving to DB
- Login: Incoming password is compared with encoded password

---

## 2️⃣ EXCEPTION LAYER - Custom Domain Exceptions

### Purpose
Centralized exception handling for domain-specific errors. Each exception represents a specific business logic failure.

---

### 📄 File: `exception/OtpExpiredException.java`
**Purpose:** Thrown when OTP verification fails because the OTP has expired (> 10 minutes).

**When Thrown:**
```java
// In RideService.startRide(...)
if (LocalDateTime.now().isAfter(ride.getOtpExpiresAt())) {
    throw new OtpExpiredException("OTP has expired. Please request a new OTP.");
}
```

**HTTP Response (via GlobalExceptionHandler):**
```json
{
    "timestamp": "2024-01-15T10:30:45.123",
    "status": 400,
    "error": "OTP_EXPIRED",
    "message": "OTP has expired. Please request a new OTP."
}
```

---

### 📄 File: `exception/RideNotFoundException.java`
**Purpose:** Thrown when a ride with given ID doesn't exist in the database.

**When Thrown:**
```java
// In RideService.startRide(...)
rideDetailsRepository.findById(rideId)
    .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));
```

**HTTP Response:**
```json
{
    "status": 404,
    "error": "RIDE_NOT_FOUND",
    "message": "Ride not found with id: 123"
}
```

---

### 📄 File: `exception/InvalidRideStatusException.java`
**Purpose:** Thrown when ride operation fails due to invalid ride status.

**When Thrown:**
```java
// Ride must be ACCEPTED to start
if (!"ACCEPTED".equals(ride.getRideStatus())) {
    throw new InvalidRideStatusException("Ride status must be ACCEPTED to start");
}

// OTP is incorrect
if (ride.getRideOtp() != otp) {
    throw new InvalidRideStatusException("OTP is incorrect");
}

// Rider not assigned
if (ride.getRiderId() != riderId) {
    throw new InvalidRideStatusException("Rider is not assigned to this ride");
}
```

**HTTP Response:**
```json
{
    "status": 400,
    "error": "INVALID_RIDE_STATUS",
    "message": "Ride status must be ACCEPTED to start"
}
```

---

### 📄 File: `exception/RiderNotAssignedException.java`
**Purpose:** Thrown when attempting to perform a rider-only operation without assigned rider.

**When Thrown:** (Can be used to verify rider assignment)

**HTTP Response:**
```json
{
    "status": 409,
    "error": "RIDER_NOT_ASSIGNED",
    "message": "No rider assigned to this ride"
}
```

---

### 📄 File: `exception/GlobalExceptionHandler.java`
**Purpose:** Spring `@ControllerAdvice` that catches ALL exceptions globally and returns consistent error responses.

**Key Handler Methods:**

| Handler | Catches | Returns |
|---------|---------|---------|
| `handleOtpExpiredException(...)` | OtpExpiredException | 400 with error=OTP_EXPIRED |
| `handleRideNotFoundException(...)` | RideNotFoundException | 404 with error=RIDE_NOT_FOUND |
| `handleRiderNotAssignedException(...)` | RiderNotAssignedException | 409 with error=RIDER_NOT_ASSIGNED |
| `handleInvalidRideStatusException(...)` | InvalidRideStatusException | 400 with error=INVALID_RIDE_STATUS |
| `handleValidationException(...)` | MethodArgumentNotValidException (from @Valid) | 400 with field errors |
| `handleGlobalException(...)` | All other exceptions | 500 with error=INTERNAL_SERVER_ERROR |

**Validation Error Example:**
```
POST /ride/request
{
    "pickUpLatitude": null  // Invalid: @NotNull
}

Response:
{
    "status": 400,
    "error": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "details": {
        "pickUpLatitude": "Pickup latitude is required"
    }
}
```

**Key Benefits:**
- **Consistency:** All errors follow same JSON structure
- **Logging:** All exceptions are logged (warn for domain, error for system)
- **Centralized:** One place to modify error handling
- **Clean Code:** No try-catch blocks scattered in controllers

---

## 3️⃣ ENTITY LAYER - Database Models (JPA Entities)

### 📄 File: `entity/User.java`
**Purpose:** Represents a user/customer who books rides. Implements `UserDetails` for Spring Security.

**Key Fields:**
```java
@Id private long id;                      // Primary key
@Column private String firstName;          // User's first name
@Column private String lastName;           // User's last name
@Column private String userName;           // Unique username (login)
@Column private String password;           // BCrypt-encoded password
@Column private String email;              // User's email
@Column private boolean enabled;           // Is account active?
@Column private String token;              // Email verification token
@Column private LocalDateTime createdAt;   // Registration timestamp
@Column private LocalDateTime expiresAt;   // Token expiry
@Column private LocalDateTime confirmedAt; // Email confirmation timestamp
@Column private String mobileNumber;       // Phone number
@Column private String mobileOtp;          // Mobile OTP for verification
```

**UserDetails Methods:**
- `getUsername()` → returns `userName`
- `getPassword()` → returns encoded password
- `getAuthorities()` → returns `[ROLE_USER]`
- `isAccountNonExpired()` → always true
- `isAccountNonLocked()` → always true
- `isCredentialsNonExpired()` → always true
- `isEnabled()` → returns `enabled` field

**Database Table:**
```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    user_name VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    enabled BOOLEAN DEFAULT FALSE,
    token VARCHAR(255),
    created_at TIMESTAMP,
    expires_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    mobile_number VARCHAR(20),
    mobile_otp VARCHAR(10)
);
```

**Key Workflow:**
1. User registers → `registerUser()` creates User with token, disabled
2. Email sent with confirmation link containing token
3. User clicks link → `confirmToken()` verifies token, sets confirmedAt, enables user
4. User logs in with username/password → Returns JWT
5. JWT used for all subsequent authenticated requests

---

### 📄 File: `entity/RiderDemoEntity.java`
**Purpose:** Represents a rider. Tracks availability and trip status.

**Key Fields:**
```java
@Id @GeneratedValue private long id;         // Rider ID
@Column private String name;                 // Rider's name
@Column private String mobile_number;        // Rider's phone
@Column private String status;               // AVAILABLE, ON_TRIP, OFFLINE
```

**Lifecycle:**
- Initial: `AVAILABLE` (ready to accept rides)
- After accepting ride and starting: `ON_TRIP` (currently delivering bike)
- After finishing ride: `AVAILABLE` (ready again)
- Offline: `OFFLINE` (not accepting requests)

---

### 📄 File: `entity/RideDetailsEntity.java`
**Purpose:** Core ride entity. Stores all information about a booked ride.

**Key Fields:**
```java
@Id @GeneratedValue private int rideId;              // Unique ride identifier
@Column private double pickUpLatitude;               // Pickup location lat
@Column private double pickUpLongitude;              // Pickup location lon
@Column private double dropLatitude;                 // Dropoff location lat
@Column private double dropLongitude;                // Dropoff location lon
@Column private double fare;                         // Calculated fare amount
@Column private int riderId;                         // Assigned rider ID (null initially)
@Column private int adminId;                         // User/customer ID who booked
@Column private long rideOtp;                        // 4-digit OTP (1000-9999)
@Column private LocalDateTime otpExpiresAt;          // OTP expiry (10 min from creation)
@Column private String rideStatus;                   // REQUESTED, ACCEPTED, STARTED, COMPLETED, CANCELLED
```

**Ride Status Lifecycle:**
```
1. REQUESTED    → Initial state after customer books
2. ACCEPTED     → Rider accepted the ride (atomic operation)
3. STARTED      → Rider verified OTP and started delivery
4. COMPLETED    → Ride finished, invoice created
5. CANCELLED    → RIDER_CANCELLED or ADMIN_CANCELLED
```

**Key Constraint:**
- `riderId` starts as NULL (0)
- Only first rider to accept can set it (atomic UPDATE)
- Ensures race-condition safety

---

### 📄 File: `entity/RideRequestEntity.java`
**Purpose:** Per-rider notification tracking. One row per rider per ride.

**Key Fields:**
```java
@Id @GeneratedValue private Integer id;      // Request row ID
@Column private int rideId;                  // Which ride
@Column private int riderId;                 // Which rider
@Column private String status;               // SENT, ACCEPTED, CANCELLED
@Column private Timestamp createdAt;         // When request was created
```

**Example Flow:**
When customer books ride with ID=5, find 5 nearest riders: [101, 102, 103, 104, 105]

Then create 5 RIDE_REQUEST rows:
```
ID=1 | RIDE_ID=5 | RIDER_ID=101 | STATUS=SENT      | CREATED_AT=2024-01-15 10:30:00
ID=2 | RIDE_ID=5 | RIDER_ID=102 | STATUS=SENT      | CREATED_AT=2024-01-15 10:30:00
ID=3 | RIDE_ID=5 | RIDER_ID=103 | STATUS=SENT      | CREATED_AT=2024-01-15 10:30:00
ID=4 | RIDE_ID=5 | RIDER_ID=104 | STATUS=SENT      | CREATED_AT=2024-01-15 10:30:00
ID=5 | RIDE_ID=5 | RIDER_ID=105 | STATUS=SENT      | CREATED_AT=2024-01-15 10:30:00
```

When rider 102 accepts:
```
ID=1 | RIDE_ID=5 | RIDER_ID=101 | STATUS=CANCELLED | CREATED_AT=2024-01-15 10:30:00
ID=2 | RIDE_ID=5 | RIDER_ID=102 | STATUS=ACCEPTED  | CREATED_AT=2024-01-15 10:30:00
ID=3 | RIDE_ID=5 | RIDER_ID=103 | STATUS=CANCELLED | CREATED_AT=2024-01-15 10:30:00
ID=4 | RIDE_ID=5 | RIDER_ID=104 | STATUS=CANCELLED | CREATED_AT=2024-01-15 10:30:00
ID=5 | RIDE_ID=5 | RIDER_ID=105 | STATUS=CANCELLED | CREATED_AT=2024-01-15 10:30:00
```

**Benefits:**
- Audit trail: Know which riders were notified
- Rematch: Exclude already-notified riders when requesting next batch
- Cancellation: Update statuses atomically

---

### 📄 File: `entity/RiderLocationEntity.java`
**Purpose:** Stores latest location of each rider. Used for Haversine-based matching.

**Key Fields:**
```java
@Id private int riderId;                  // Primary key: unique rider
@Column private double latitude;          // Current latitude
@Column private double longitude;         // Current longitude
@Column private Timestamp updatedAt;      // When location was last updated
```

**UPSERT Mechanism:**
```sql
INSERT INTO rider_location (rider_id, current_latitude, current_longitude, updated_at)
VALUES (101, 19.075, 72.877, NOW())
ON CONFLICT (rider_id) 
DO UPDATE SET 
    current_latitude = EXCLUDED.current_latitude,
    current_longitude = EXCLUDED.current_longitude,
    updated_at = CURRENT_TIMESTAMP
```

**Why UPSERT?**
- Rider sends location every 5-30 seconds
- Always keep only LATEST location per rider
- Avoids duplicate rows

**Haversine Query (used in matching):**
```sql
SELECT rider_id, distance FROM (
    SELECT rl.rider_id,
        6371 * acos(
            cos(radians(:lat)) *
            cos(radians(rl.current_latitude)) *
            cos(radians(rl.current_longitude) - radians(:lon)) +
            sin(radians(:lat)) *
            sin(radians(rl.current_latitude))
        ) AS distance
    FROM rider_location rl
    WHERE rl.updated_at > NOW() - INTERVAL '30 seconds'  -- Only fresh locations!
)
ORDER BY distance
LIMIT 5
```

---

### 📄 File: `entity/InvoiceEntity.java`
**Purpose:** Billing record created after ride completion.

**Key Fields:**
```java
@Id @GeneratedValue private int invoiceId;         // Unique invoice ID
@Column private int rideId;                        // Which ride
@Column private int riderId;                       // Which rider
@Column private int userId;                        // Which customer
@Column private double amount;                     // Fare amount
@Column private LocalDateTime createdAt;           // When invoice created
@Column private String status;                     // PENDING, SETTLED, REFUNDED
```

**Invoice Status Lifecycle:**
```
PENDING  → Invoice created, awaiting payment/settlement
SETTLED  → Payment received and processed
REFUNDED → Refund issued (user cancelled or issue occurred)
```

**Example:**
```
INVOICE_ID=42 | RIDE_ID=5 | RIDER_ID=102 | USER_ID=1 | 
AMOUNT=150.00 | CREATED_AT=2024-01-15 10:45:30 | STATUS=PENDING
```

**Integration:**
- Created in `RideService.endRide()` automatically
- Payment processing is placeholder (can integrate Stripe/Razorpay later)
- Status updates handled separately by payment worker

---

## 4️⃣ DTO LAYER - Data Transfer Objects

DTOs are used to **decouple API contracts from database models**.

### 📄 File: `dto/RideDetailsDTO.java`
**Purpose:** Request DTO for booking a ride. Contains validation constraints.

**Fields:**
```java
@NotNull @Positive private Double fare;              // Validated: must be positive
@NotNull private Double pickUpLatitude;              // Validated: must be present
@NotNull private Double pickUpLongitude;             // Validated: must be present
@NotNull private Double dropLatitude;                // Validated: must be present
@NotNull private Double dropLongitude;               // Validated: must be present
@NotNull private Integer adminId;                    // Validated: must be present
```

**Example Request:**
```json
POST /ride/request
Content-Type: application/json

{
    "pickUpLatitude": 19.0760,
    "pickUpLongitude": 72.8777,
    "dropLatitude": 19.1234,
    "dropLongitude": 72.9999,
    "fare": 250.50,
    "adminId": 1
}
```

**Validation:**
- Spring uses `@Valid` in controller
- If validation fails → `MethodArgumentNotValidException` caught by `GlobalExceptionHandler`
- Returns 400 with field errors

---

### 📄 File: `dto/RiderLocationDTO.java`
**Purpose:** Request DTO for sending rider location updates.

**Fields:**
```java
@NotNull private Integer rideId;              // Current ride being completed
@NotNull private Integer riderId;             // Rider's ID
@NotNull private Double latitude;             // Current latitude
@NotNull private Double longitude;            // Current longitude
```

**Example Request:**
```json
POST /rider/save/location
{
    "rideId": 5,
    "riderId": 102,
    "latitude": 19.0765,
    "longitude": 72.8781
}
```

**Processing:**
1. Save location to `RiderLocationEntity` via UPSERT
2. Broadcast to `/topic/ride/{rideId}` for user to see real-time location

---

### 📄 File: `entity/OsrmResponse.java`
**Purpose:** Response model from OSRM (Open Source Routing Machine) API for distance calculation.

**Used By:** `DistanceCalculationService` to parse HTTP response

---

## 5️⃣ REPOSITORY LAYER - Data Access Objects (DAOs)

Repositories provide abstraction over database queries. Spring Data JPA generates SQL automatically.

### 📄 File: `repository/RideDetailsRepository.java`
**Purpose:** CRUD operations for rides. Custom atomic accept operation.

**Methods:**

| Method | Type | Purpose |
|--------|------|---------|
| `save(RideDetailsEntity)` | Inherited | Insert or update ride |
| `findById(Integer)` | Inherited | Find ride by ID → `Optional<RideDetailsEntity>` |
| `deleteById(Integer)` | Inherited | Delete ride |
| `acceptRide(rideId, riderId)` | Custom | Atomic operation: Update ride status to ACCEPTED |

**acceptRide Query:**
```java
@Modifying
@Transactional
@Query("""
    UPDATE RideDetailsEntity r
    SET r.riderId = :riderId,
        r.rideStatus = 'ACCEPTED'
    WHERE r.rideId = :rideId
    AND (r.riderId IS NULL OR r.riderId = 0)
""")
int acceptRide(@Param("rideId") int rideId, @Param("riderId") int riderId);
```

**How Atomic Accept Works:**
```
Thread 1: rider101 calls acceptRide(5, 101)
Thread 2: rider102 calls acceptRide(5, 102)

Database sees both calls simultaneously
SQL executed: UPDATE ... WHERE rideId=5 AND riderId IS NULL
- FIRST to execute: Updates riderId=101, returns 1 (1 row affected)
- SECOND to execute: Tries to update WHERE riderId IS NULL, but riderId=101 now, so 0 rows affected
- Returns 0 (0 rows affected) → acceptRide recognizes failure

Result: Only rider101 succeeds!
```

---

### 📄 File: `repository/RideRequestRepository.java`
**Purpose:** CRUD for per-rider request notifications.

**Methods:**

| Method | Purpose |
|--------|---------|
| `save(RideRequestEntity)` | Insert/update request |
| `saveAll(List<RideRequestEntity>)` | Batch insert (used when creating requests for 5 riders) |
| `findByRideId(int)` | Get all requests for a ride (used for status updates, rematch) |

---

### 📄 File: `repository/RiderLocationRepository.java`
**Purpose:** Location storage and retrieval. Implements UPSERT via native query.

**Methods:**

| Method | Purpose | SQL |
|--------|---------|-----|
| `updateRiderLocation(riderId, lat, lon)` | UPSERT location | INSERT ... ON CONFLICT ... DO UPDATE |

**Native Query Details:**
```sql
INSERT INTO RIDER_LOCATION (rider_id, current_latitude, current_longitude, updated_at)
VALUES (:id, :lat, :lon, CURRENT_TIMESTAMP)
ON CONFLICT (rider_id) 
DO UPDATE SET 
    current_latitude = EXCLUDED.current_latitude, 
    current_longitude = EXCLUDED.current_longitude, 
    updated_at = CURRENT_TIMESTAMP
```

**PostgreSQL-Specific Feature:**
- `ON CONFLICT` clause ensures only one row per rider
- Atomic update without race conditions
- No need to check if row exists first

---

### 📄 File: `repository/RiderMatchingRepository.java`
**Purpose:** Find nearest available riders using Haversine formula.

**Method: `findNearestRiders(lat, lon)`**

**Custom SQL (Haversine Formula):**
```sql
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
```

**How It Works:**
1. Uses Haversine formula to calculate distance between two lat/lon points
2. Filters out stale locations (older than 30 seconds)
3. Orders by distance (nearest first)
4. Returns top 5 riders
5. Returns `List<Object[]>` where each row is `[rider_id, lat, lon, distance]`

**Distance Formula:**
```
d = 2R * acos( cos(φ₁) * cos(φ₂) * cos(Δλ) + sin(φ₁) * sin(φ₂) )
where R = 6371 km (Earth's radius)
```

---

### 📄 File: `repository/UserRepository.java`
**Purpose:** User lookup queries.

**Inherited Methods:**
- `findById(Long)` → Find user by ID
- `save(User)` → Create/update user

**Custom Methods (inferred from usage):**
- `findByUserName(String)` → Find user by username
- `findByEmail(String)` → Find user by email
- `findByToken(String)` → Find user by verification token
- `findByUserNameOrEmail(String, String)` → Find by username OR email

---

### 📄 File: `repository/InvoiceRepository.java`
**Purpose:** Invoice querying and retrieval.

**Methods:**

| Method | Returns | Purpose |
|--------|---------|---------|
| `findByRideId(int)` | `List<InvoiceEntity>` | Get invoices for a specific ride |
| `findByUserId(int)` | `List<InvoiceEntity>` | Get invoices for a customer |
| `findByRiderId(int)` | `List<InvoiceEntity>` | Get invoices for a rider |
| `findFirstByRideIdOrderByCreatedAtDesc(int)` | `Optional<InvoiceEntity>` | Get latest invoice for ride |

---

### 📄 File: `repository/RiderDemoRepository.java`
**Purpose:** Rider CRUD and status updates.

**Methods:**
- `findById(Long)` → Find rider by ID (used to update status)
- `save(RiderDemoEntity)` → Update rider (e.g., set status to ON_TRIP)

---

## 6️⃣ SERVICE LAYER - Business Logic & Orchestration

Services contain all business logic and coordinate between controllers and repositories.

### 📄 File: `service/RideDetailsService.java`
**Purpose:** Orchestrates ride creation workflow.

**Key Method: `saveRideDetials(RideDetailsDTO)`**

**Execution Steps:**
```
1. Convert DTO → Entity (BeanUtils.copyProperties)
2. Generate 4-digit OTP (1000-9999) using SecureRandom
3. Set otpExpiresAt = now + 10 minutes
4. Set rideStatus = "REQUESTED"
5. Save ride to database → get generated rideId

6. Find nearest 5 riders:
   - Call RiderMatchingService.findNearestRiders(pickupLat, pickupLon)
   - Returns List of rider IDs

7. Create RideRequest rows for each rider:
   - For each rider in nearest 5 list:
     - Create RideRequestEntity with status=SENT
     - Add to requests list
   - Batch insert all requests

8. Notify riders via WebSocket:
   - For each rider:
     - Send message to /topic/rider/{riderId}
     - Include: rideId, pickupLat, pickupLon, dropLat, dropLon, fare

9. Return generated rideId
```

**@Transactional Behavior:**
- All steps 1-8 run as single transaction
- If any step fails → entire transaction rolled back
- Ensures consistency: ride + ride_requests created together or not at all

**Example WebSocket Message Sent to Riders:**
```json
{
    "type": "RIDE_REQUEST",
    "rideId": 5,
    "pickupLat": 19.0760,
    "pickupLon": 72.8777,
    "dropLat": 19.1234,
    "dropLon": 72.9999,
    "fare": 250.50
}
```

**acceptRide Helper Method:**
```java
public boolean acceptRide(int rideId, int riderId) {
    int updated = rideDetailsRepository.acceptRide(rideId, riderId);
    return updated == 1;  // true if successful, false if ride already taken
}
```

---

### 📄 File: `service/RideService.java`
**Purpose:** Handles ride lifecycle: accept, start, end, cancel, rematch.

**Key Methods:**

#### 1. **acceptRide(rideId, riderId)**
**Purpose:** Rider accepts ride. Only first rider to accept wins.

**Execution:**
```
1. Call atomic repository acceptRide() 
2. If returns 0 → ride already taken, return false
3. If returns 1 → successfully accepted, continue...

4. Fetch ride details from DB
5. Get userId (ride.getAdminId())

6. Fetch all RideRequest rows for this ride
7. Update all rows:
   - If rider matches accepting rider → status = ACCEPTED
   - Otherwise → status = CANCELLED

8. Notify user via /topic/user/{userId}:
   {
       "type": "RIDE_ACCEPTED",
       "rideId": 5,
       "riderId": 102,
       "otp": 1234
   }

9. Notify cancelled riders via /topic/rider/{riderId}:
   {
       "type": "RIDE_CANCELLED",
       "rideId": 5
   }

10. Return true
```

#### 2. **riderCancel(rideId, riderId)**
**Purpose:** Rider cancels after accepting. Triggers rematch.

**Execution:**
```
1. Fetch ride by ID
2. Check rider ID matches (rider only cancels own ride)

3. Update ride:
   - rideStatus = RIDER_CANCELLED
   - riderId = 0 (unassign)
   - Save to DB

4. Notify user via /topic/user/{userId}:
   {
       "type": "RIDER_CANCELLED",
       "rideId": 5,
       "riderId": 102
   }

5. Rematch logic:
   - Fetch all existing RideRequest rows
   - Build list of already-notified riders to EXCLUDE
   - Find next 5 nearest riders (excluding already-notified)
   - Create NEW RideRequest rows for fresh riders
   - Notify new riders via /topic/rider/{riderId}

6. Set rideStatus back to "REQUESTED" for matching
```

**Key Insight:**
- Prevents notifying same riders multiple times
- Gradually exhausts all available riders if they keep cancelling
- Eventually ride times out or is cancelled by user

#### 3. **userCancel(rideId)**
**Purpose:** User/admin cancels the ride.

**Execution:**
```
1. Fetch ride by ID
2. Set rideStatus = ADMIN_CANCELLED
3. Save to DB

4. If riderId > 0 (rider assigned):
   - Notify rider via /topic/rider/{riderId}:
     {
         "type": "RIDE_CANCELLED_BY_USER",
         "rideId": 5
     }

5. Update all RideRequest rows → status = CANCELLED
6. Mark rider AVAILABLE (if assigned)
7. Handle refund (placeholder: set invoiceStatus = REFUNDED)
```

#### 4. **startRide(rideId, riderId, otp)**
**Purpose:** Rider verifies OTP and starts delivery. Marks ride STARTED.

**Validation Steps:**
```
1. Fetch ride, throw RideNotFoundException if not found
2. Verify riderId matches assigned rider:
   if (ride.getRiderId() != riderId)
       throw InvalidRideStatusException(...)

3. Verify ride status is ACCEPTED:
   if (!"ACCEPTED".equals(ride.getRideStatus()))
       throw InvalidRideStatusException(...)

4. Check OTP not expired:
   if (LocalDateTime.now().isAfter(ride.getOtpExpiresAt()))
       throw OtpExpiredException(...)

5. Check OTP matches:
   if (ride.getRideOtp() != otp)
       throw InvalidRideStatusException("OTP is incorrect")
```

**On Success:**
```
1. Update ride:
   - rideStatus = STARTED
   - Save to DB

2. Mark rider ON_TRIP:
   - Find rider in RiderDemoRepository
   - Set status = ON_TRIP
   - Save

3. Notify via /topic/ride/{rideId}:
   {
       "type": "RIDE_STARTED",
       "rideId": 5
   }

4. Notify user via /topic/user/{userId}:
   {
       "type": "RIDE_STARTED",
       "rideId": 5
   }

5. Return true
```

**OTP Expiry Mechanism:**
```
Created at: 10:30:00
Expires at: 10:40:00 (10 minutes later)

At 10:39:59 → OTP valid ✓
At 10:40:00 → OTP expired ✗
At 10:40:01 → OTP expired ✗

No attempt limiting per user requirement
(Brute force protection: rate limit at API gateway in production)
```

#### 5. **endRide(rideId, riderId)**
**Purpose:** Complete ride. Create invoice. Mark rider AVAILABLE.

**Validation:**
```
1. Ride must be in STARTED status
2. riderId must match assigned rider
```

**Execution:**
```
1. Update ride:
   - rideStatus = COMPLETED
   - Save to DB

2. Mark rider AVAILABLE:
   - Find rider in RiderDemoRepository
   - Set status = AVAILABLE
   - Save

3. Create invoice:
   InvoiceEntity invoice = new InvoiceEntity(
       rideId,
       riderId,
       ride.getAdminId(),  // userId
       ride.getFare(),      // amount
       LocalDateTime.now(), // createdAt
       "PENDING"           // status: awaiting settlement
   )
   invoiceRepository.save(invoice)

4. Notify via /topic/ride/{rideId}:
   {
       "type": "RIDE_COMPLETED",
       "rideId": 5,
       "fare": 250.50,
       "invoiceId": 42
   }

5. Notify user via /topic/user/{userId}:
   {
       "type": "RIDE_COMPLETED",
       "rideId": 5,
       "fare": 250.50,
       "invoiceId": 42
   }

6. Return true
```

**Invoice Creation:**
- Invoice status starts as PENDING
- Payment processing handled separately (async worker)
- Settlement updates invoice status to SETTLED

---

### 📄 File: `service/RiderMatchingService.java`
**Purpose:** Finds nearest riders using Haversine formula.

**Method: `findNearestRiders(lat, lon)`**

**Execution:**
```
1. Call RiderMatchingRepository.findNearestRiders(lat, lon)
2. Repository returns List<Object[]> where each is [rider_id, lat, lon, distance]
3. Extract just rider IDs using stream:
   results.stream()
       .map(r -> ((Number) r[0]).intValue())  // Extract first element (rider_id)
       .toList()
4. Return List<Integer> of rider IDs
```

**Returns:** List of up to 5 nearest rider IDs, ordered by distance (nearest first)

---

### 📄 File: `service/LiveLocationTrackingService.java`
**Purpose:** Handles rider location updates and broadcasting.

**Methods:**

| Method | Purpose |
|--------|---------|
| `sendLocationUpdate(RiderLocationDTO)` | Broadcast location to /topic/ride/{rideId} |
| `saveRiderCurrentLocation(RiderLocationDTO)` | UPSERT location to database |

**Implementation:**

```java
public void sendLocationUpdate(RiderLocationDTO location) {
    messagingTemplate.convertAndSend(
        "/topic/ride/" + location.getRideId(),
        location
    );
}
```
- Broadcasts to all users watching this ride
- Payload: `{ riderId, latitude, longitude, rideId }`

```java
public void saveRiderCurrentLocation(RiderLocationDTO location) {
    riderLocationRepository.updateRiderLocation(
        location.getRiderId(),
        location.getLatitude(),
        location.getLongitude()
    );
}
```
- Calls UPSERT operation in repository
- Keeps only latest location per rider
- Called every 5-30 seconds (configurable by client)

---

### 📄 File: `service/DistanceCalculationService.java`
**Purpose:** Calculates distance and fare using OSRM (Open Source Routing Machine).

**Method: `getDistanceInKm(startLat, startLon, endLat, endLon)`**

**Execution:**
```
1. Build OSRM URL: 
   http://router.project-osrm.org/route/v1/driving/72.8777,19.0760;72.9999,19.1234?overview=false
   (Note: lon,lat format for OSRM)

2. Call OSRM HTTP API via RestTemplate
3. Parse response into OsrmResponse object
4. Extract distance from first route: response.routes.get(0).distance
5. Convert meters to km: distance / 1000.0
6. Return kilometers as double
```

**OSRM Response Structure:**
```json
{
    "routes": [
        {
            "distance": 15432.5,  // in meters
            "duration": 1234.2,   // in seconds
            "geometry": "..."
        }
    ],
    "code": "Ok"
}
```

**Usage:**
- Called during ride booking to calculate distance
- Used to compute fare: `fare = distance_km * RATE_PER_KM`
- Can be extended with surge pricing, time-based pricing, etc.

---

### 📄 File: `service/CustomUserDetailsService.java`
**Purpose:** User authentication, registration, and email verification.

**Key Methods:**

| Method | Purpose |
|--------|---------|
| `loadUserByUsername(username)` | Spring Security: Load user by username |
| `registerUser(User)` | Register new user with email verification |
| `confirmToken(token)` | Verify email and enable account |
| `response(token)` | Generate JWT after email verification |
| `verifyEmailAddress(email)` | Send reset token for password recovery |

**Registration Flow:**
```
1. User submits: { username, password, email, firstName, lastName }
2. Check if username/email already exists
3. BCrypt-encode password
4. Generate random verification token (UUID)
5. Save user with:
   - enabled = false (account disabled until email verified)
   - token = verification token
   - expiresAt = now + 15 minutes
6. Send email with link: /register/confirmToken?token=xxx
7. User clicks link
8. Backend calls confirmToken(token):
   - Validates token exists and not expired
   - Sets confirmedAt = now
   - Sets enabled = true
9. Return JWT for immediate login
```

---

## 7️⃣ CONTROLLER LAYER - REST API Endpoints

Controllers handle HTTP requests and delegate to services.

### 📄 File: `controller/RideRequestController.java`
**Purpose:** All ride-related REST endpoints.

**Endpoints:**

#### **1. POST /ride/request - Book a Ride**
```
Request:
{
    "pickUpLatitude": 19.0760,
    "pickUpLongitude": 72.8777,
    "dropLatitude": 19.1234,
    "dropLongitude": 72.9999,
    "fare": 250.50,
    "adminId": 1
}

Response:
Ride Request Sent, id=5

HTTP 200 OK
```

**Behind the Scenes:**
- Validation: @Valid checks all @NotNull and @Positive constraints
- Service: Creates ride, generates OTP, finds riders, creates requests, notifies
- Returns: Generated rideId

#### **2. POST /ride/accept - Accept Ride**
```
Request:
POST /ride/accept?rideId=5&riderId=102

Response:
"Ride accepted"
OR
"Ride already taken"

HTTP 200 OK or 400 Bad Request
```

**Behind the Scenes:**
- Service: atomically updates ride (only first rider wins)
- Updates RideRequest rows
- Notifies user and other riders

#### **3. POST /ride/verify-otp - Start Ride**
```
Request:
POST /ride/verify-otp?rideId=5&riderId=102&otp=1234

Response:
"OTP Verified. Ride Started"

HTTP 200 OK
```

**Validations:**
- OTP must match
- OTP must not be expired (> 10 minutes)
- Ride status must be ACCEPTED
- riderId must match assigned rider

**Exceptions:**
- `OtpExpiredException` → 400 OTP_EXPIRED
- `InvalidRideStatusException` → 400 INVALID_RIDE_STATUS
- `RideNotFoundException` → 404 RIDE_NOT_FOUND

#### **4. POST /ride/end - Complete Ride**
```
Request:
POST /ride/end?rideId=5&riderId=102

Response:
"Ride ended successfully"

HTTP 200 OK
```

**Behind the Scenes:**
- Marks ride COMPLETED
- Marks rider AVAILABLE
- Creates invoice with status PENDING
- Notifies user and ride topic with invoiceId

#### **5. POST /ride/rider-cancel - Rider Cancels**
```
Request:
POST /ride/rider-cancel?rideId=5&riderId=102

Response:
"Rider cancellation handled and rematch attempted"

HTTP 200 OK
```

**Behind the Scenes:**
- Marks ride RIDER_CANCELLED
- Unassigns rider (riderId = 0)
- Notifies user
- Finds next 5 nearest riders (excluding already-notified)
- Creates new RideRequest rows
- Notifies new riders

#### **6. POST /ride/cancel - User Cancels**
```
Request:
POST /ride/cancel?rideId=5

Response:
"Ride cancelled by user/admin"

HTTP 200 OK
```

**Behind the Scenes:**
- Marks ride ADMIN_CANCELLED
- Notifies assigned rider
- Updates all RideRequest rows to CANCELLED
- Placeholder: handle refund

---

### 📄 File: `controller/LiveLocationTrackingController.java`
**Purpose:** Rider location updates.

**Endpoints:**

#### **1. POST /rider/fetch/livelocation - Send Location Update**
```
Request:
{
    "rideId": 5,
    "riderId": 102,
    "latitude": 19.0765,
    "longitude": 72.8781
}

Response:
HTTP 200 OK (void)
```

**Behind the Scenes:**
- Broadcasts location to /topic/ride/{rideId}
- User sees real-time location of rider on map

#### **2. POST /rider/save/location - Save Location to Database**
```
Request:
{
    "rideId": 5,
    "riderId": 102,
    "latitude": 19.0765,
    "longitude": 72.8781
}

Response:
HTTP 200 OK (void)
```

**Behind the Scenes:**
- UPSERT location to RiderLocationEntity
- Only latest location kept per rider
- Used for next rider matching if ride is reassigned

**Typical Usage Pattern:**
- Rider app sends location every 5-30 seconds (background task)
- Each location saved to DB (via `/rider/save/location`)
- Each location broadcast to user (via `/rider/fetch/livelocation`)
- Batched requests for efficiency in production

---

### 📄 File: `controller/AuthenticationController.java`
**Purpose:** User registration and authentication.

**Endpoints:**

#### **1. GET /register - Show Registration Page**
```
Response: register.html (Thymeleaf template)
```

#### **2. POST /register - Register User**
```
Request:
{
    "firstName": "John",
    "lastName": "Doe",
    "userName": "johndoe",
    "password": "SecurePass123",
    "email": "john@example.com",
    "mobileNumber": "+919876543210"
}

Response:
{
    "success": true,
    "message": "Please confirm your email address"
}
```

**Behind the Scenes:**
- Validation: Check username/email not already exists
- Password: BCrypt-encode
- Token: Generate UUID-based email verification token
- Email: Send verification link
- Account: Set enabled=false until verified

#### **3. GET /register/confirmToken - Verify Email**
```
Request:
GET /register/confirmToken?token=abc-123-def-456

Response:
{
    "message": "OTP verified Successfully",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Behind the Scenes:**
- Lookup user by token
- Validate token not expired
- Set enabled=true
- Generate JWT with 30-day expiry
- User can now login

#### **4. GET /register/verifyEmail - Forgot Password**
```
Request:
GET /register/verifyEmail?emailAddress=john@example.com

Response:
true (or false if email not found)
```

**Behind the Scenes:**
- Lookup user by email
- Generate new reset token
- Send reset email
- User clicks reset link, sets new password

---

### 📄 File: `controller/DistanceCalculationController.java`
**Purpose:** Distance and fare calculation endpoints (if exposed).

*Note: Usually called internally, not by clients*

---

### 📄 File: `controller/DemoController.java`
**Purpose:** Demo/testing endpoints.

*Used for development and testing*

---

## 8️⃣ DATA FLOW - End-to-End Examples

### **Example 1: Complete Ride Booking to Completion**

```
CUSTOMER (User ID 1)
│
├─ Step 1: POST /ride/request
│  {
│      "pickUpLatitude": 19.0760,
│      "pickUpLongitude": 72.8777,
│      "dropLatitude": 19.1234,
│      "dropLongitude": 72.9999,
│      "fare": 250.50,
│      "adminId": 1
│  }
│
│  RideDetailsService.saveRideDetials(...):
│  ├─ Generate OTP: 5432
│  ├─ Set otpExpiresAt: now + 10 min
│  ├─ Save ride → RIDE_DETAILS (id=5, status=REQUESTED)
│  ├─ Find nearest 5 riders: [101, 102, 103, 104, 105]
│  ├─ Create RideRequest rows (5 rows, all status=SENT)
│  └─ Broadcast to riders via /topic/rider/{id}
│
├─ RIDERS RECEIVE NOTIFICATION VIA WEBSOCKET
│  Topic: /topic/rider/101, /topic/rider/102, etc.
│  Message: { type: RIDE_REQUEST, rideId: 5, ... }
│
├─ Step 2: RIDER 102 Accepts
│  POST /ride/accept?rideId=5&riderId=102
│
│  RideService.acceptRide(5, 102):
│  ├─ Atomic UPDATE: riderId=102, status=ACCEPTED (only if NULL)
│  ├─ Update RideRequest rows:
│  │  - Rider 102: ACCEPTED
│  │  - Riders 101, 103, 104, 105: CANCELLED
│  ├─ Notify User 1 via /topic/user/1:
│  │  { type: RIDE_ACCEPTED, riderId: 102, otp: 5432 }
│  └─ Notify other riders via /topic/rider/{id}:
│     { type: RIDE_CANCELLED, rideId: 5 }
│
├─ Step 3: RIDER 102 Reaches Pickup
│  POST /rider/save/location
│  { rideId: 5, riderId: 102, latitude: 19.0770, longitude: 72.8780 }
│
│  LiveLocationTrackingService.saveRiderCurrentLocation():
│  └─ UPSERT RiderLocation (riderId=102)
│
├─ Step 4: RIDER 102 Verifies OTP
│  POST /ride/verify-otp?rideId=5&riderId=102&otp=5432
│
│  RideService.startRide(5, 102, 5432):
│  ├─ Validate: OTP correct, not expired, status ACCEPTED
│  ├─ Update ride: status=STARTED
│  ├─ Update rider: status=ON_TRIP
│  ├─ Notify via /topic/ride/5: { type: RIDE_STARTED }
│  └─ Notify user: { type: RIDE_STARTED }
│
├─ Step 5: CONTINUOUS LOCATION UPDATES
│  Every 5 seconds:
│  POST /rider/fetch/livelocation
│  { rideId: 5, riderId: 102, lat: X, lon: Y }
│  └─ Broadcast to /topic/ride/5 for user to see map
│
├─ Step 6: RIDER 102 Completes Delivery
│  POST /ride/end?rideId=5&riderId=102
│
│  RideService.endRide(5, 102):
│  ├─ Update ride: status=COMPLETED
│  ├─ Update rider: status=AVAILABLE
│  ├─ Create invoice:
│  │  InvoiceEntity(rideId=5, riderId=102, userId=1,
│  │              amount=250.50, status=PENDING)
│  ├─ Notify via /topic/ride/5:
│  │  { type: RIDE_COMPLETED, fare: 250.50, invoiceId: 42 }
│  └─ Notify user: { type: RIDE_COMPLETED, ... }
│
└─ CUSTOMER SEES:
   ✓ Ride completed
   ✓ Final fare: 250.50
   ✓ Invoice ID: 42
```

---

### **Example 2: Rider Cancellation & Rematch**

```
RIDER 102 (assigned to ride 5)
│
├─ Step 1: Cancel ride
│  POST /ride/rider-cancel?rideId=5&riderId=102
│
│  RideService.riderCancel(5, 102):
│  ├─ Update ride: status=RIDER_CANCELLED, riderId=0
│  ├─ Notify user: { type: RIDER_CANCELLED }
│  │
│  ├─ Fetch existing requests:
│  │  [RIDER_101=SENT, RIDER_102=ACCEPTED, RIDER_103=SENT, ...]
│  │
│  ├─ Get excluded riders: [101, 102, 103, 104, 105]
│  │
│  ├─ Find NEXT 5 nearest riders: [106, 107, 108, 109, 110]
│  │
│  ├─ Create NEW RideRequest rows:
│  │  [RIDER_106=SENT, RIDER_107=SENT, ...]
│  │
│  ├─ Notify new riders via /topic/rider/{id}:
│  │  { type: RIDE_REQUEST, rideId: 5, ... }
│  │
│  └─ Set ride back to status=REQUESTED
│
└─ RESULT:
   ✓ Original batch of riders (101-105) no longer receive notifications
   ✓ Fresh batch (106-110) get notified
   ✓ Eventually all nearby riders exhausted
   ✓ User can cancel with refund if no rider accepts
```

---

## 9️⃣ WebSocket Communication Example

### **Client Setup (JavaScript with @stomp/stompjs):**

```javascript
import { Client } from '@stomp/stompjs';

const token = localStorage.getItem('jwt_token');

const client = new Client({
    brokerURL: 'ws://localhost:8080/ws',
    connectHeaders: {
        Authorization: `Bearer ${token}`
    },
    onConnect: (frame) => {
        console.log('Connected:', frame);
        setupSubscriptions();
    },
    onStompError: (frame) => {
        console.error('STOMP error:', frame);
    }
});

function setupSubscriptions() {
    // Customer subscribes to their user topic
    client.subscribe('/topic/user/1', (message) => {
        const data = JSON.parse(message.body);
        console.log('Received:', data);
        
        if (data.type === 'RIDE_ACCEPTED') {
            console.log(`Rider ${data.riderId} accepted! OTP: ${data.otp}`);
        } else if (data.type === 'RIDE_STARTED') {
            console.log('Ride started!');
        } else if (data.type === 'RIDE_COMPLETED') {
            console.log(`Ride complete. Invoice: ${data.invoiceId}`);
        }
    });
    
    // Subscribe to ride tracking
    client.subscribe('/topic/ride/5', (message) => {
        const data = JSON.parse(message.body);
        
        if (data.type === 'RIDE_STARTED') {
            console.log('Tracking started');
        }
        
        // Location update
        updateMapWithLocation(data);
    });
}

// For riders
function riderSetup() {
    client.subscribe('/topic/rider/102', (message) => {
        const data = JSON.parse(message.body);
        
        if (data.type === 'RIDE_REQUEST') {
            console.log('New ride request!', data);
            // Show popup: Accept ride?
        } else if (data.type === 'RIDE_CANCELLED') {
            console.log('This ride was taken by another rider');
        }
    });
}

client.activate();
```

---

## 🔟 Database Schema (Flyway Migrations)

### **Table: RIDE_DETAILS** (Canonical ride entity)
```sql
CREATE TABLE RIDE_DETAILS (
    RIDE_ID BIGINT PRIMARY KEY AUTO_INCREMENT,
    PICKUP_LATITUDE DOUBLE NOT NULL,
    PICKUP_LONGITUDE DOUBLE NOT NULL,
    DROP_LATITUDE DOUBLE NOT NULL,
    DROP_LONGITUDE DOUBLE NOT NULL,
    FARE DOUBLE NOT NULL,
    RIDER_ID INT DEFAULT NULL,  -- NULL until accepted
    ADMIN_ID INT NOT NULL,      -- User who booked
    RIDE_OTP INT NOT NULL,      -- 4-digit OTP
    OTP_EXPIRES_AT TIMESTAMP NOT NULL,  -- 10 min from creation
    RIDE_STATUS VARCHAR(50) NOT NULL,   -- REQUESTED, ACCEPTED, STARTED, COMPLETED
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### **Table: RIDE_REQUEST** (Per-rider notifications)
```sql
CREATE TABLE RIDE_REQUEST (
    ID BIGINT PRIMARY KEY AUTO_INCREMENT,
    RIDE_ID BIGINT NOT NULL,
    RIDER_ID INT NOT NULL,
    STATUS VARCHAR(50) NOT NULL,  -- SENT, ACCEPTED, CANCELLED
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (RIDE_ID) REFERENCES RIDE_DETAILS(RIDE_ID)
);
```

### **Table: RIDER_LOCATION** (Latest rider position)
```sql
CREATE TABLE RIDER_LOCATION (
    RIDER_ID INT PRIMARY KEY,
    CURRENT_LATITUDE DOUBLE NOT NULL,
    CURRENT_LONGITUDE DOUBLE NOT NULL,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### **Table: INVOICES** (Billing records)
```sql
CREATE TABLE INVOICES (
    INVOICE_ID BIGINT PRIMARY KEY AUTO_INCREMENT,
    RIDE_ID BIGINT NOT NULL UNIQUE,
    RIDER_ID INT NOT NULL,
    USER_ID INT NOT NULL,
    AMOUNT DOUBLE NOT NULL,
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    STATUS VARCHAR(50) NOT NULL,  -- PENDING, SETTLED, REFUNDED
    FOREIGN KEY (RIDE_ID) REFERENCES RIDE_DETAILS(RIDE_ID)
);
```

### **Table: RIDER_DETAILS** (Rider profile)
```sql
CREATE TABLE RIDER_DETAILS (
    ID BIGINT PRIMARY KEY AUTO_INCREMENT,
    NAME VARCHAR(255) NOT NULL,
    MOBILE_NUMBER VARCHAR(20),
    STATUS VARCHAR(50) DEFAULT 'AVAILABLE'  -- AVAILABLE, ON_TRIP, OFFLINE
);
```

### **Table: USERS** (Customer accounts)
```sql
CREATE TABLE USERS (
    ID BIGINT PRIMARY KEY AUTO_INCREMENT,
    FIRST_NAME VARCHAR(255),
    LAST_NAME VARCHAR(255),
    USER_NAME VARCHAR(255) UNIQUE NOT NULL,
    PASSWORD VARCHAR(255) NOT NULL,
    EMAIL VARCHAR(255) UNIQUE,
    ENABLED BOOLEAN DEFAULT FALSE,
    TOKEN VARCHAR(255),
    CREATED_AT TIMESTAMP,
    EXPIRES_AT TIMESTAMP,
    CONFIRMED_AT TIMESTAMP,
    MOBILE_NUMBER VARCHAR(20),
    MOBILE_OTP VARCHAR(10)
);
```

---

## 1️⃣1️⃣ Key Design Decisions & Trade-offs

### **Decision 1: UPSERT for RiderLocation**
**Why:** Keeps only latest location per rider, prevents stale data issues
**Trade-off:** One location update overwrites previous (no history)
**Alternative:** Archive old locations in separate table
**Chosen:** UPSERT for simplicity and real-time requirements

---

### **Decision 2: Atomic Accept via WHERE riderId IS NULL**
**Why:** Guarantees only first rider wins, no race conditions
**Trade-off:** Loses accept attempts of 2nd, 3rd, etc riders (no logging)
**Alternative:** Pessimistic locking with explicit locks
**Chosen:** Optimistic WHERE-based approach for better performance

---

### **Decision 3: OTP Expiry, NO Attempt Limiting**
**Why:** User explicitly requested no brute-force protection
**Trade-off:** Vulnerable to OTP brute-force attacks
**Mitigation:** Rate limiting at API gateway (not in code)
**Note:** NEVER use this in real production without rate limiting!

---

### **Decision 4: In-Memory STOMP Broker**
**Why:** Simple, fast, no external dependencies
**Trade-off:** Messages lost if server restarts, no clustering
**Alternative:** RabbitMQ, ActiveMQ, Kafka (user said NO KAFKA)
**Chosen:** In-memory for MVP; upgrade to RabbitMQ in production

---

### **Decision 5: Placeholder Invoice Settlement**
**Why:** Decouples billing logic, allows async processing
**Trade-off:** Payment not immediately processed
**Future:** Async worker reads PENDING invoices, calls Stripe/Razorpay, updates status
**Chosen:** Separation of concerns

---

## 1️⃣2️⃣ Running the Application

### **Prerequisites:**
```bash
# Java 21
java --version

# Maven
mvn --version

# PostgreSQL running on localhost:5432
psql postgres -U postgres -c "CREATE DATABASE ariyaanshaikh;"
```

### **Build:**
```bash
cd /Users/ariyaanshaikh/Documents/BikeRiderApplication
./mvnw -DskipTests package
```

### **Run:**
```bash
# Option 1: Maven
./mvnw spring-boot:run

# Option 2: JAR
java -jar target/RiderBookingApplication-0.0.1-SNAPSHOT.jar
```

### **Logs:**
```
...
Started PracticeApplication in 5.234 seconds
Flyway: Executing migration V1: create_ride_request_table
Flyway: Executing migration V2: add_rider_status_column
...
Tomcat started on port(s): 8080
WebSocket endpoint registered at /ws
SecurityFilterChain configured with 5 filters
```

---

## 1️⃣3️⃣ Testing Endpoints

### **1. Register User:**
```bash
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "userName": "johndoe",
    "password": "SecurePass123",
    "email": "john@example.com",
    "mobileNumber": "+919876543210"
  }'
```

### **2. Confirm Email (get token from email):**
```bash
curl -X GET "http://localhost:8080/register/confirmToken?token=abc-123-def"
# Response includes JWT
```

### **3. Book Ride:**
```bash
curl -X POST http://localhost:8080/ride/request \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d '{
    "pickUpLatitude": 19.0760,
    "pickUpLongitude": 72.8777,
    "dropLatitude": 19.1234,
    "dropLongitude": 72.9999,
    "fare": 250.50,
    "adminId": 1
  }'
```

### **4. Accept Ride (as Rider):**
```bash
curl -X POST "http://localhost:8080/ride/accept?rideId=5&riderId=102" \
  -H "Authorization: Bearer <RIDER_JWT>"
```

### **5. Start Ride (Verify OTP):**
```bash
curl -X POST "http://localhost:8080/ride/verify-otp?rideId=5&riderId=102&otp=1234" \
  -H "Authorization: Bearer <RIDER_JWT>"
```

### **6. End Ride:**
```bash
curl -X POST "http://localhost:8080/ride/end?rideId=5&riderId=102" \
  -H "Authorization: Bearer <RIDER_JWT>"
```

---

## Summary Table

| Layer | Responsibility | Key Files |
|-------|----------------|-----------|
| **Config** | Security, JWT, WebSocket | SecurityConfig, JwtUtils, JwtFilter, JwtChannelInterceptor, WebSocketConfig |
| **Exception** | Error handling | GlobalExceptionHandler, OtpExpiredException, RideNotFoundException, InvalidRideStatusException |
| **Entity** | Database models | User, RiderDemoEntity, RideDetailsEntity, RideRequestEntity, RiderLocationEntity, InvoiceEntity |
| **DTO** | API contracts | RideDetailsDTO, RiderLocationDTO |
| **Repository** | Data access | RideDetailsRepository, RideRequestRepository, RiderMatchingRepository, RiderLocationRepository, InvoiceRepository, UserRepository |
| **Service** | Business logic | RideDetailsService, RideService, RiderMatchingService, LiveLocationTrackingService, DistanceCalculationService, CustomUserDetailsService |
| **Controller** | REST endpoints | RideRequestController, LiveLocationTrackingController, AuthenticationController |

---

This documentation covers every file, class, and method in your Rider Booking Application!

