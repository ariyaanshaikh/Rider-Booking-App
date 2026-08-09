"""Build the current Rider Booking API and database reference.

This is intentionally separate from the earlier generator so the document can
be regenerated after API changes without bringing back obsolete endpoint
contracts.
"""

from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Inches, Pt

# Reuse the established document styling helpers from the existing reference.
from build_rider_booking_reference_updated import (
    BLUE,
    NAVY,
    add_run,
    add_table,
    base_paragraph,
    bullets,
    code_block,
    endpoint_block,
    heading,
    section_heading,
)


OUT = Path(__file__).with_name("RIDER_BOOKING_API_AND_DATABASE_REFERENCE_UPDATED.docx")


def add_note(document, text):
    paragraph = base_paragraph(document, after=7)
    add_run(paragraph, "Important: ", size=9.3, bold=True, color="9C6500")
    add_run(paragraph, text, size=9.3, color="9C6500")


def configure_document():
    doc = Document()
    section = doc.sections[0]
    section.top_margin = section.bottom_margin = Inches(1)
    section.left_margin = section.right_margin = Inches(1)
    section.header_distance = section.footer_distance = Inches(0.492)
    doc.styles["Normal"].font.name = "Calibri"
    doc.styles["Normal"].font.size = Pt(10.5)

    header = section.header.paragraphs[0]
    header.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    add_run(header, "Rider Booking Application | Technical Reference", size=8.5, color="666666")
    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_run(footer, "Internal engineering reference - updated 8 August 2026", size=8.5, color="666666")
    return doc


def flow_section(document, text):
    """A major heading that follows the preceding content when there is room."""
    return heading(document, text, 1)


def cover(document):
    title = document.add_paragraph()
    title.paragraph_format.space_before = Pt(72)
    title.paragraph_format.space_after = Pt(6)
    add_run(title, "RIDER BOOKING APPLICATION", size=24, bold=True, color=NAVY)

    subtitle = document.add_paragraph()
    subtitle.paragraph_format.space_after = Pt(20)
    add_run(subtitle, "Current API, Test Sequence, WebSocket, and Database Reference", size=14.5, color=BLUE)

    base_paragraph(
        document,
        "Scope: the deployed Spring Boot API, role-based rider/admin workflow, "
        "rider verification, 10 km matching, secure rider live location, private "
        "WebSocket delivery, ride lifecycle, invoices, and Neon/PostgreSQL schema.",
        size=10.8,
        after=16,
    )
    add_table(document, ["Implemented decision", "Current behavior"], [
        ("Account roles", "USERS.ROLE is ADMIN or RIDER. A rider profile is linked one-to-one through RIDER_DETAILS.USER_ID."),
        ("Matching", "Only VERIFIED, AVAILABLE riders with a location newer than 10 minutes and within 10 km are eligible."),
        ("Dispatch availability", "A rider can change only their own status between AVAILABLE and OFFLINE. An accepted rider is set to ON_TRIP and cannot toggle status until the ride ends or is cancelled."),
        ("Ride OTP", "No clock expiry. It works only while the assigned ride remains ACCEPTED."),
        ("Pre-ride location", "A rider saves only latitude/longitude; the server derives the rider ID from the authenticated account."),
        ("Live tracking", "An assigned rider sends only latitude/longitude. The server derives the active ride and privately sends it to the booking admin."),
        ("Vehicle details", "Each ride request stores its vehicleName and chassisNo in RIDE_DETAILS and returns them in profile ride GET responses."),
        ("Invoice", "Ending a STARTED ride creates one PENDING invoice automatically."),
    ], [1.7, 4.7], body_size=8.8)
    heading(document, "How to use this guide", 1)
    bullets(document, [
        "Use the ordered sequence in section 3 for a complete end-to-end test. It prevents the common error of trying to track a rider before the rider has an accepted ride.",
        "Use ADMIN and RIDER JWTs separately. The frontend can cache IDs for display, but the backend remains the authority for rider ownership and the active ride.",
        "Run the consolidated SQL setup script only for a new database. For the deployed Neon database, apply the numbered migrations and confirm V7, V8, and V9 are present.",
        "Never put JWTs, Aadhaar, PAN, bank account details, or database passwords into public screenshots, CodePen links, or source control.",
    ])


def setup_and_roles(document):
    section_heading(document, "1. Environment, identities, and roles")
    base_paragraph(document, "Base URLs", size=11.5, bold=True, color=BLUE, after=4)
    add_table(document, ["Environment", "Base URL"], [
        ("Local development", "http://localhost:8088"),
        ("Render deployment", "https://rider-booking-app.onrender.com"),
    ], [1.75, 4.65])

    heading(document, "Required Postman variables", 2)
    add_table(document, ["Variable", "Meaning", "Example"], [
        ("baseUrl", "Selected server URL", "https://rider-booking-app.onrender.com"),
        ("adminJwt", "JWT whose subject is the booking ADMIN username", "eyJ..."),
        ("riderJwt", "JWT whose subject is the rider username", "eyJ..."),
        ("adminId", "USERS.ID of the booking admin", "2"),
        ("riderUserId", "USERS.ID of the rider account", "3"),
        ("riderId", "RIDER_DETAILS.ID linked to riderUserId", "3"),
        ("rideId", "RIDE_DETAILS.RIDE_ID after a booking", "2"),
        ("rideOtp", "Generated 4-digit OTP after ride acceptance", "4829"),
    ], [1.25, 3.35, 1.8], body_size=8.6)

    heading(document, "Data model: only two account roles", 2)
    code_block(document, """USERS
  ID | USER_NAME | EMAIL | PASSWORD | ENABLED | ROLE
  2  | ariyaan   | ...   | hashed   | true    | ADMIN
  3  | vijay     | ...   | hashed   | true    | RIDER

RIDER_DETAILS
  ID | USER_ID | NAME         | STATUS
  3  | 3       | Vijay Rider  | AVAILABLE

RIDER_VERIFICATION_DETAILS
  RIDER_ID | IS_VERIFIED | ...private KYC / bank fields...
  3        | true        | ...

RIDER_DETAILS.USER_ID is UNIQUE and references USERS.ID.
RIDER_VERIFICATION_DETAILS.RIDER_ID is both its primary key and a foreign key to RIDER_DETAILS.ID.""")
    add_note(document, "The rider display name (for example, 'Vijay Rider') is not a login username. JWT subject must exactly match USERS.USER_NAME (for example, 'vijay').")

    heading(document, "Current endpoint authorization", 2)
    add_table(document, ["Caller", "Allowed operations"], [
        ("Public", "Registration/email confirmation, SMS OTP endpoints, GET /demo, and GET /sendJWT (development-only and insecure)."),
        ("Any authenticated user", "Own profile, own ride history, own active ride, own invoices, rider history, nearby rider lookup, fare calculation, and /api/auth/validate."),
        ("ADMIN", "Create rider profile; request a ride; cancel a booking."),
        ("RIDER", "Save own pre-ride location; publish own active-ride live location; accept/cancel/start/end a ride."),
    ], [1.5, 5.0], body_size=8.7)
    add_note(document, "The role annotations prevent an ADMIN token from using the rider-only location endpoints. Ownership checks for caller-supplied rideId/riderId in ride lifecycle endpoints remain a future hardening item.")


def endpoint_catalog(document):
    flow_section(document, "2. Complete endpoint catalog")
    base_paragraph(document, "There are 33 controller endpoints. Sections 4–9 provide Postman-ready examples and the correct sequence.", size=10.5)
    heading(document, "Accounts and session", 2)
    add_table(document, ["Method", "Path", "Access", "Input"], [
        ("GET", "/register", "Public", "None; registration HTML"),
        ("POST", "/register", "Public", "User JSON; role required"),
        ("GET", "/register/confirmToken", "Public", "token query"),
        ("GET", "/register/verifyEmail", "Public", "emailAddress query"),
        ("POST", "/register/updateUser", "Public", "legacy form fields"),
        ("GET", "/demo", "Public", "None; demo only"),
        ("POST", "/login", "Authenticated by current config", "username/password JSON"),
        ("GET", "/sendJWT", "Public (unsafe)", "username query"),
        ("POST", "/otp/send/sms", "Public", "phone, userName query"),
        ("POST", "/otp/verify", "Public", "key, otp, username query"),
        ("GET", "/health", "Public", "None; service health JSON"),
        ("GET", "/api/auth/validate", "JWT", "Bearer token"),
    ], [0.62, 2.25, 1.27, 2.15], body_size=8.1)
    heading(document, "Current-user profile data (frontend)", 2)
    add_table(document, ["Method", "Path", "Access", "Input"], [
        ("GET", "/api/profile/me", "JWT", "Bearer token"),
        ("GET", "/api/profile/me/rides", "JWT", "Bearer token"),
        ("GET", "/api/profile/me/active-ride", "JWT", "Bearer token"),
        ("GET", "/api/profile/me/invoices", "JWT", "Bearer token"),
        ("GET", "/api/profile/me/rider-location", "RIDER", "Bearer token"),
        ("GET", "/api/profile/me/verification-details", "RIDER", "Bearer token"),
    ], [0.62, 2.52, 1.08, 2.07], body_size=8.0)
    heading(document, "Rider, ride, and utility", 2)
    add_table(document, ["Method", "Path", "Access", "Input"], [
        ("POST", "/rider", "RIDER", "Rider profile JSON; ownership from JWT"),
        ("POST", "/rider/{riderId}/verification-details", "JWT", "KYC/bank JSON"),
        ("GET", "/rider/{riderId}/verification-details", "JWT", "Path ID"),
        ("PATCH", "/rider/{riderId}/verification?verified=", "JWT", "Boolean query"),
        ("GET", "/rider/{riderId}/history", "JWT", "Path ID"),
        ("POST", "/rider/save/location", "RIDER", "latitude/longitude JSON"),
        ("PATCH", "/rider/status?status=", "RIDER", "AVAILABLE or OFFLINE query"),
        ("POST", "/rider/fetch/livelocation", "RIDER", "latitude/longitude JSON"),
        ("GET", "/rider/nearby?lat=&lon=", "JWT", "Coordinates query"),
        ("POST", "/ride/request", "ADMIN", "Ride JSON"),
        ("POST", "/ride/accept?rideId=&riderId=", "RIDER", "Query IDs"),
        ("POST", "/ride/verify-otp?rideId=&riderId=&otp=", "RIDER", "Query IDs + OTP"),
        ("POST", "/ride/end?rideId=&riderId=", "RIDER", "Query IDs"),
        ("POST", "/ride/rider-cancel?rideId=&riderId=", "RIDER", "Query IDs"),
        ("POST", "/ride/cancel?rideId=", "ADMIN", "Query ID"),
        ("GET", "/api/fare/calculate", "JWT", "sLat, sLon, dLat, dLon query"),
    ], [0.62, 2.52, 1.08, 2.07], body_size=7.85)


def sequence(document):
    flow_section(document, "3. Correct end-to-end test sequence")
    base_paragraph(document, "Perform these steps in order. A successful later step depends on earlier database state.", size=10.6)
    add_table(document, ["Step", "Who / JWT", "Endpoint or action", "Required result"], [
        ("1", "Admin", "Register or identify ADMIN user", "Enabled ADMIN account; record USERS.ID and an ADMIN JWT."),
        ("2", "Rider", "Register or identify RIDER user", "Enabled RIDER account; record USERS.ID and a RIDER JWT."),
        ("2A", "Admin + Rider", "GET /api/profile/me", "Use the JWT to load the profile screen; no client-supplied ID is needed."),
        ("3", "Rider", "POST /rider", "Create the rider's own profile; the server links it to the authenticated RIDER account."),
        ("4", "Admin", "POST verification details; PATCH verified=true", "Exactly one verification row for riderId; matching requires verified=true."),
        ("5", "Rider", "POST /rider/save/location", "Fresh GPS row for that rider; no ride is required."),
        ("6", "Admin", "GET /rider/nearby", "Verified AVAILABLE rider appears if within 10 km and fresh."),
        ("7", "Admin", "POST /ride/request", "New REQUESTED ride and matching RIDE_REQUEST rows."),
        ("8", "Rider", "POST /ride/accept", "Only the authenticated, AVAILABLE rider who received the SENT request can accept; ride becomes ACCEPTED and rider becomes ON_TRIP."),
        ("9", "Admin + Rider", "Connect private WebSocket; POST /rider/fetch/livelocation", "Admin receives a private location message for the accepted ride."),
        ("10", "Rider", "POST /ride/verify-otp", "Ride becomes STARTED."),
        ("11", "Rider", "POST /ride/end", "Ride becomes COMPLETED; rider AVAILABLE; one PENDING invoice is created."),
    ], [0.48, 1.2, 2.35, 2.29], body_size=8.05)
    add_note(document, "POST /rider/save/location is for being found by matching. POST /rider/fetch/livelocation is only for an accepted/started ride and private admin tracking.")


def account_tests(document):
    flow_section(document, "4. Account and session endpoint tests")
    endpoint_block(document, "POST", "/register", "None (public)", "raw JSON", '''POST {{baseUrl}}/register
Content-Type: application/json

{
  "firstName": "Ariyaan",
  "lastName": "Admin",
  "userName": "ariyaan",
  "password": "StrongPassword",
  "email": "admin@example.com",
  "mobileNumber": "9999999999",
  "mobileOtp": "",
  "role": "ADMIN"
}''', '''200 OK
{
  "success": true,
  "message": "Please confirm your email address"
}''', "For a rider use role: RIDER. Only ADMIN and RIDER are valid. The API response does not return the created database user ID; read it from USERS for test setup.")

    endpoint_block(document, "GET", "/register/confirmToken", "None (public)", "Query parameter", "GET {{baseUrl}}/register/confirmToken?token={{verificationToken}}", '''200 OK on a valid token
{
  "message": "OTP verified Successfully",
  "token": "<JWT>",
  "userId": 3
}''', "Confirmation enables the account and returns its USERS.ID as userId. The returned token subject is USERS.USER_NAME. Store userId only for UI/display; protected APIs derive ownership from the JWT.")

    endpoint_block(document, "GET", "/register/verifyEmail", "None (public)", "Query parameter", "GET {{baseUrl}}/register/verifyEmail?emailAddress=admin@example.com", '''Existing email:
{"success":true,"message":"A confirmation email has been requested"}

Missing email:
404 {"success":false,"message":"No user was found for this email address"}''')

    endpoint_block(document, "POST", "/register/updateUser", "None (public)", "x-www-form-urlencoded", '''POST {{baseUrl}}/register/updateUser
Content-Type: application/x-www-form-urlencoded

firstName=Test
lastName=User
userName=testuser
password=StrongPassword
email=test@example.com
mobileNumber=9999999999''', "200 OK with the current legacy plain-text message. This is a legacy form endpoint; do not send JSON.")

    endpoint_block(document, "POST", "/login", "Current SecurityConfig requires a JWT", "raw JSON", '''POST {{baseUrl}}/login
Content-Type: application/json

{
  "username": "ariyaan",
  "password": "StrongPassword"
}''', '''When the request is allowed and credentials are valid:
200 OK
{"token":"<JWT>"}

Current anonymous behavior:
403 before the controller method.''', "The endpoint exists but /login is not in SecurityConfig permitAll. Use the confirmation token response for the current test flow, or deliberately change the security configuration before treating /login as a public login endpoint.")

    endpoint_block(document, "GET", "/sendJWT", "None (public; development only)", "Query parameter", "GET {{baseUrl}}/sendJWT?username=ariyaan", '''200 OK
{"message":"JWT Token has been generated successfully on the basis of Username","token":"<JWT>"}''', "The supplied username must exist in USERS. This endpoint can create tokens without a password, so it must be removed/restricted before production.")

    endpoint_block(document, "POST", "/otp/send/sms", "None (public)", "Query parameters", "POST {{baseUrl}}/otp/send/sms?phone=9999999999&userName=ariyaan", "200 OK with a plain-text SMS confirmation.")
    endpoint_block(document, "POST", "/otp/verify", "None (public)", "Query parameters", "POST {{baseUrl}}/otp/verify?key=9999999999&otp=123456&username=ariyaan", '''Success:
{"message":"OTP verified Successfully","token":"<JWT>"}

Failure:
{"message":"Verification Failed"}''')
    endpoint_block(document, "GET", "/api/auth/validate", "Bearer {{adminJwt}}", "No body", "GET {{baseUrl}}/api/auth/validate\nAuthorization: Bearer {{adminJwt}}", '''200 OK
{"valid":true,"username":"ariyaan","message":"Welcome back!"}''')
    endpoint_block(document, "GET", "/health", "None (public)", "No body", "GET {{baseUrl}}/health", '''200 OK
{"status":"UP"}''', "Use this endpoint to confirm the Render service is running before testing authentication or WebSocket calls.")
    endpoint_block(document, "GET", "/register", "None (public)", "No body", "GET {{baseUrl}}/register", "200 OK with registration HTML, not JSON.")
    endpoint_block(document, "GET", "/demo", "None (public)", "No body", "GET {{baseUrl}}/demo", "200 OK with home HTML. It may create a hard-coded demo rider, so do not use it in shared data.")


def profile_tests(document):
    flow_section(document, "5. Frontend profile and dashboard read APIs")
    base_paragraph(document, "These endpoints are designed for the mobile/web profile screens. Send only Authorization: Bearer <JWT>. Do not send userId, riderId, or rideId: the server resolves the current account from the JWT.", size=10.2)

    endpoint_block(document, "GET", "/api/profile/me", "Bearer adminJwt or riderJwt", "No body", '''GET {{baseUrl}}/api/profile/me
Authorization: Bearer {{riderJwt}}''', '''200 OK (RIDER example)
{
  "userId": 3,
  "firstName": "Vijay",
  "lastName": "Shekhar",
  "userName": "vijay",
  "email": "vijay@gmail.com",
  "mobileNumber": "916730",
  "role": "RIDER",
  "enabled": true,
  "rider": {
    "riderId": 3,
    "name": "Vijay Rider",
    "mobileNumber": "9167300000",
    "status": "AVAILABLE",
    "verificationSubmitted": true,
    "verified": true
  }
}''', "ADMIN users receive their account fields and role, without a rider object. A confirmed rider without a RIDER_DETAILS row receives rider: null, not a server error. Password, email token, mobile OTP, and JWT are never returned.")

    endpoint_block(document, "GET", "/api/profile/me/rides", "Bearer adminJwt or riderJwt", "No body", '''GET {{baseUrl}}/api/profile/me/rides
Authorization: Bearer {{adminJwt}}''', '''200 OK
[
  {
    "rideId": 2,
    "pickupLatitude": 19.076,
    "pickupLongitude": 72.8777,
    "dropLatitude": 19.11,
    "dropLongitude": 72.9,
    "fare": 125.0,
    "chassisNo": "MBLHA10A1NHB12345",
    "vehicleName": "Honda Activa 6G",
    "riderId": 3,
    "adminId": 2,
    "rideStatus": "COMPLETED"
  }
]''', "ADMIN gets rides where ADMIN_ID is the logged-in user ID. RIDER gets rides where RIDER_ID is the rider profile linked to the logged-in user. The history deliberately excludes rideOtp.")

    endpoint_block(document, "GET", "/api/profile/me/active-ride", "Bearer adminJwt or riderJwt", "No body", '''GET {{baseUrl}}/api/profile/me/active-ride
Authorization: Bearer {{adminJwt}}''', '''200 OK (ADMIN with accepted ride)
{
  "activeRide": {
    "rideId": 2,
    "pickupLatitude": 19.076,
    "pickupLongitude": 72.8777,
    "dropLatitude": 19.11,
    "dropLongitude": 72.9,
    "fare": 125.0,
    "chassisNo": "MBLHA10A1NHB12345",
    "vehicleName": "Honda Activa 6G",
    "riderId": 3,
    "adminId": 2,
    "rideStatus": "ACCEPTED"
  },
  "rideOtp": 4829
}''', "Active means ACCEPTED or STARTED. The booking ADMIN receives rideOtp so its UI can show the passenger OTP. A RIDER never receives rideOtp. If there is no active ride: {\"activeRide\": null}.")

    endpoint_block(document, "GET", "/api/profile/me/invoices", "Bearer adminJwt or riderJwt", "No body", '''GET {{baseUrl}}/api/profile/me/invoices
Authorization: Bearer {{adminJwt}}''', '''200 OK
[
  {
    "invoiceId": 1,
    "rideId": 2,
    "riderId": 3,
    "userId": 2,
    "amount": 125.0,
    "createdAt": "2026-08-07T12:00:00",
    "status": "PENDING"
  }
]''', "ADMIN receives invoices belonging to their USERS.ID. RIDER receives invoices belonging to their linked RIDER_DETAILS.ID. An account with no invoices receives [].")

    endpoint_block(document, "GET", "/api/profile/me/rider-location", "Bearer riderJwt — RIDER role", "No body", '''GET {{baseUrl}}/api/profile/me/rider-location
Authorization: Bearer {{riderJwt}}''', '''200 OK
{
  "riderId": 3,
  "location": {
    "latitude": 19.077,
    "longitude": 72.8777,
    "updatedAt": "2026-08-07T12:00:00.000+00:00"
  }
}''', "Returns only the logged-in rider's last saved location. An ADMIN JWT receives 403. If the rider has not yet saved a location, location is null.")

    endpoint_block(document, "GET", "/api/profile/me/verification-details", "Bearer riderJwt — RIDER role", "No body", '''GET {{baseUrl}}/api/profile/me/verification-details
Authorization: Bearer {{riderJwt}}''', '''200 OK
{
  "riderId": 3,
  "verificationDetails": {
    "aadhaarCardNumber": "123456789013",
    "panCardNumber": "ABCDE1234F",
    "drivingLicenseNumber": "MH0120230001234",
    "bankAccountNumber": "123456789012",
    "bankIfscCode": "HDFC0001234",
    "bankAccountName": "Vijay Rider",
    "verified": true
  }
}''', "This endpoint is intentionally limited to the owning rider because it contains sensitive KYC/bank data. Never log this response or expose it in an admin list. If no details are saved yet, verificationDetails is null.")


def rider_tests(document):
    flow_section(document, "6. Rider profile, verification, availability, and matching")
    endpoint_block(document, "POST", "/rider", "Bearer {{riderJwt}} — RIDER role", "raw JSON", '''POST {{baseUrl}}/rider
Authorization: Bearer {{riderJwt}}
Content-Type: application/json

{
  "name": "Vijay Rider",
  "mobileNumber": "9167300000",
  "status": "AVAILABLE"
}''', '''201 Created
{
  "id": 3,
  "name":"Vijay Rider",
  "mobile_number":"9167300000",
  "status":"AVAILABLE",
  "userId":3
}''', "The request must not contain userId. The server takes the username from the rider JWT, finds that RIDER account, and stores its USERS.ID as USER_ID. Copy id to {{riderId}}. USER_ID is unique, so one login account cannot own two rider profiles; a duplicate profile request is rejected. A rider can submit only AVAILABLE or OFFLINE—ON_TRIP is controlled by the ride lifecycle.")

    endpoint_block(document, "POST", "/rider/{riderId}/verification-details", "Bearer JWT", "raw JSON", '''POST {{baseUrl}}/rider/{{riderId}}/verification-details
Authorization: Bearer {{adminJwt}}
Content-Type: application/json

{
  "aadhaarCardNumber": "123456789013",
  "panCardNumber": "ABCDE1234F",
  "drivingLicenseNumber": "MH0120230001234",
  "bankAccountNumber": "123456789012",
  "bankIfscCode": "HDFC0001234",
  "bankAccountName": "Vijay Rider"
}''', '''201 Created
{
  "riderId":3,
  "aadhaarCardNumber":"123456789013",
  "panCardNumber":"ABCDE1234F",
  "drivingLicenseNumber":"MH0120230001234",
  "bankAccountNumber":"123456789012",
  "bankIfscCode":"HDFC0001234",
  "bankAccountName":"Vijay Rider",
  "verified":false
}''', "The rider ID comes only from the URL path. For rider 3, call /rider/3/verification-details. The body must not contain riderId. Saving again updates that same rider record and resets verified to false.")

    endpoint_block(document, "GET", "/rider/{riderId}/verification-details", "Bearer JWT", "No body", "GET {{baseUrl}}/rider/{{riderId}}/verification-details\nAuthorization: Bearer {{adminJwt}}", "200 OK with the stored verification JSON.", "This currently exposes unmasked KYC/bank fields. Do not expose this endpoint to untrusted clients in production.")
    endpoint_block(document, "PATCH", "/rider/{riderId}/verification", "Bearer JWT", "Boolean query parameter", "PATCH {{baseUrl}}/rider/{{riderId}}/verification?verified=true\nAuthorization: Bearer {{adminJwt}}", "200 OK with the verification object where verified is true.", "Use true or false, not Y/N. Verified must be true before matching can return the rider.")
    endpoint_block(document, "GET", "/rider/{riderId}/history", "Bearer JWT", "No body", "GET {{baseUrl}}/rider/{{riderId}}/history\nAuthorization: Bearer {{riderJwt}}", "200 OK with an array of rides whose RIDER_ID equals the path value, newest ride ID first.")

    endpoint_block(document, "PATCH", "/rider/status", "Bearer {{riderJwt}} — RIDER role", "status query parameter", '''PATCH {{baseUrl}}/rider/status?status=OFFLINE
Authorization: Bearer {{riderJwt}}''', '''200 OK
{
  "riderId": 3,
  "status": "OFFLINE"
}''', "Use only AVAILABLE or OFFLINE. The server identifies the rider from the JWT, so the client cannot update another rider's status. ON_TRIP is controlled by ride acceptance/lifecycle; a rider with an active assignment cannot change availability. OFFLINE and ON_TRIP riders are excluded from matching.")

    endpoint_block(document, "POST", "/rider/save/location", "Bearer {{riderJwt}} — RIDER role", "raw JSON", '''POST {{baseUrl}}/rider/save/location
Authorization: Bearer {{riderJwt}}
Content-Type: application/json

{
  "latitude": 19.0770,
  "longitude": 72.8777
}''', '''200 OK
{
  "type":"RIDER_LOCATION_SAVED",
  "riderId":3,
  "latitude":19.077,
  "longitude":72.8777
}''', "No rideId or riderId is sent. The server derives riderId from the JWT → USERS → RIDER_DETAILS.USER_ID mapping, then atomically upserts the current rider location. This endpoint works before a ride is assigned.")

    endpoint_block(document, "GET", "/rider/nearby", "Bearer JWT", "Query parameters", "GET {{baseUrl}}/rider/nearby?lat=19.0760&lon=72.8777\nAuthorization: Bearer {{adminJwt}}", "200 OK with a rider-ID array, for example [3].", "A rider is returned only when location is newer than 10 minutes, status is AVAILABLE, verification is true, and Haversine distance is <= 10 km.")


def ride_and_tracking(document):
    flow_section(document, "7. Ride request, acceptance, and live tracking")
    endpoint_block(document, "POST", "/ride/request", "Bearer {{adminJwt}} — ADMIN role", "raw JSON", '''POST {{baseUrl}}/ride/request
Authorization: Bearer {{adminJwt}}
Content-Type: application/json

{
  "pickUpLatitude": 19.0760,
  "pickUpLongitude": 72.8777,
  "dropLatitude": 19.1100,
  "dropLongitude": 72.9000,
  "fare": 125.00,
  "chassisNo": "MBLHA10A1NHB12345",
  "vehicleName": "Honda Activa 6G",
  "adminId": {{adminId}}
}''', "200 OK with plain text: Ride Request Sent, id=2", "chassisNo and vehicleName are required and stored on RIDE_DETAILS. Copy the returned number to {{rideId}}. The server creates a 4-digit ride OTP, sets status REQUESTED, finds eligible riders, saves RIDE_REQUEST rows, and publishes RIDE_REQUEST events. Do not send rideId, riderId, rideOtp, or rideStatus in the body.")

    endpoint_block(document, "POST", "/ride/accept", "Bearer {{riderJwt}} — RIDER role", "Query parameters", "POST {{baseUrl}}/ride/accept?rideId={{rideId}}&riderId={{riderId}}\nAuthorization: Bearer {{riderJwt}}", "200 OK: Ride accepted", "The submitted riderId must match the authenticated rider's JWT-linked RIDER_DETAILS profile. The rider must be AVAILABLE, have no ACCEPTED or STARTED ride, and have a SENT RIDE_REQUEST row for this ride. This prevents rider-ID impersonation and accepting a guessed or already-assigned ride. The first valid accept wins atomically; the winner becomes ON_TRIP and other RIDE_REQUEST rows are CANCELLED.")

    heading(document, "Multiple rider requests and first-accept behavior", 2)
    base_paragraph(document, "One ride can be sent to several eligible riders. The server creates one RIDE_REQUEST row for each rider, all with the same rideId and status SENT.", size=10.0, after=4)
    code_block(document, '''Before any acceptance
ride_id | rider_id | status
42      | 3        | SENT
42      | 7        | SENT
42      | 12       | SENT

After rider 7 accepts first
ride_id | rider_id | status
42      | 3        | CANCELLED
42      | 7        | ACCEPTED
42      | 12       | CANCELLED

RIDE_DETAILS for ride 42
RIDER_ID = 7
RIDE_STATUS = ACCEPTED''')
    add_note(document, "The cancellation of other candidate rows is automatic. Riders 3 and 12 also receive a RIDE_CANCELLED WebSocket event at /topic/rider/{riderId}, so their apps can remove that request immediately.")

    heading(document, "Private live-location WebSocket test", 2)
    base_paragraph(document, "Connect CodePen/browser as the booking ADMIN whose username matches the ride's ADMIN_ID. For ride 2 in the current test database that user is ariyaan. The rider must be assigned to a ride with status ACCEPTED or STARTED.", size=9.8, after=5)
    code_block(document, '''Browser STOMP CONNECT header
Authorization: Bearer {{adminJwt}}

Private subscription
/user/queue/ride/{{rideId}}/location

Rider HTTP update (after the browser subscription is connected)
POST {{baseUrl}}/rider/fetch/livelocation
Authorization: Bearer {{riderJwt}}
Content-Type: application/json

{
  "latitude": 19.0780,
  "longitude": 72.8780
}''')
    base_paragraph(document, "Expected HTTP response", size=9.4, bold=True, color=BLUE, after=2)
    code_block(document, '''{
  "type":"RIDER_LOCATION_UPDATED",
  "rideId":2,
  "riderId":3,
  "latitude":19.078,
  "longitude":72.878
}''')
    base_paragraph(document, "Expected private WebSocket message", size=9.4, bold=True, color=BLUE, after=2)
    code_block(document, '''{
  "type":"RIDER_LOCATION_UPDATED",
  "rideId":2,
  "riderId":3,
  "latitude":19.078,
  "longitude":72.878
}''')
    add_note(document, "Do not subscribe to /topic/ride/{rideId} for live GPS updates. The current live-location event is private and goes to /user/queue/ride/{rideId}/location only. A rider JWT cannot receive the booking admin's private queue.")

    endpoint_block(document, "POST", "/rider/fetch/livelocation", "Bearer {{riderJwt}} — RIDER role", "raw JSON", '''POST {{baseUrl}}/rider/fetch/livelocation
Authorization: Bearer {{riderJwt}}
Content-Type: application/json

{
  "latitude": 19.0780,
  "longitude": 72.8780
}''', "200 OK with RIDER_LOCATION_UPDATED including server-derived riderId and rideId.", "The server uses JWT username → USERS record → RIDER_DETAILS.USER_ID → active ACCEPTED/STARTED ride. If no active ride exists it returns 403 No active ride is assigned to this rider.")


def completion_and_utilities(document):
    flow_section(document, "8. Ride completion, cancellation, and utility tests")
    endpoint_block(document, "POST", "/ride/verify-otp", "Bearer {{riderJwt}} — RIDER role", "Query parameters", "POST {{baseUrl}}/ride/verify-otp?rideId={{rideId}}&riderId={{riderId}}&otp={{rideOtp}}\nAuthorization: Bearer {{riderJwt}}", "200 OK: OTP Verified. Ride Started", "The ride-start OTP has no clock expiry. It is accepted only for the assigned rider while status is ACCEPTED. On success the ride becomes STARTED; the rider was already made ON_TRIP when the ride was accepted.")
    endpoint_block(document, "POST", "/ride/end", "Bearer {{riderJwt}} — RIDER role", "Query parameters", "POST {{baseUrl}}/ride/end?rideId={{rideId}}&riderId={{riderId}}\nAuthorization: Bearer {{riderJwt}}", "200 OK: Ride ended successfully", "Only a STARTED assigned ride can end. The transaction sets the ride to COMPLETED, makes the rider AVAILABLE, and creates one INVOICES row with status PENDING and amount equal to fare.")
    endpoint_block(document, "POST", "/ride/rider-cancel", "Bearer {{riderJwt}} — RIDER role", "Query parameters", "POST {{baseUrl}}/ride/rider-cancel?rideId={{rideId}}&riderId={{riderId}}\nAuthorization: Bearer {{riderJwt}}", "200 OK: Rider cancellation handled and rematch attempted", "The service clears the assigned rider, notifies the booking user, marks existing requests cancelled/accepted as appropriate, and attempts to notify new eligible candidates.")
    endpoint_block(document, "POST", "/ride/cancel", "Bearer {{adminJwt}} — ADMIN role", "Query parameter", "POST {{baseUrl}}/ride/cancel?rideId={{rideId}}\nAuthorization: Bearer {{adminJwt}}", "200 OK: Ride cancelled by user/admin", "The ride becomes ADMIN_CANCELLED and all RIDE_REQUEST rows become CANCELLED. An assigned rider receives a cancellation event.")
    endpoint_block(document, "GET", "/api/fare/calculate", "Bearer JWT", "Query parameters", "GET {{baseUrl}}/api/fare/calculate?sLat=19.0760&sLon=72.8777&dLat=19.1100&dLon=72.9000\nAuthorization: Bearer {{adminJwt}}", "200 OK with the current plain-text distance/fare placeholder response.", "The present fare output is not a production pricing calculation. Do not use it for invoice amounts or payments.")

    heading(document, "Ride lifecycle assertions", 2)
    add_table(document, ["After action", "Database assertion", "WebSocket event"], [
        ("request", "RIDE_DETAILS.REQUESTED and RIDE_REQUEST.SENT rows", "/topic/rider/{riderId}: RIDE_REQUEST"),
        ("accept", "RIDE_DETAILS.ACCEPTED; winning rider ID assigned; rider ON_TRIP", "/topic/user/{adminId}: RIDE_ACCEPTED"),
        ("verify OTP", "RIDE_DETAILS.STARTED; rider remains ON_TRIP", "/topic/ride/{rideId} and /topic/user/{adminId}: RIDE_STARTED"),
        ("end", "RIDE_DETAILS.COMPLETED; rider AVAILABLE; one PENDING invoice", "/topic/ride/{rideId} and /topic/user/{adminId}: RIDE_COMPLETED"),
    ], [1.1, 3.05, 2.1], body_size=8.0)


def websocket(document):
    flow_section(document, "9. WebSocket destinations and complete CodePen test")
    base_paragraph(document, "WebSocket endpoint: {{baseUrl}}/ws using SockJS and STOMP. The HTTP handshake is public, but STOMP CONNECT must contain a native Authorization: Bearer <JWT> header. The backend now attaches the JWT identity to the real STOMP CONNECT frame, enabling private user queues.", size=10.2)
    add_table(document, ["Destination", "Event / use", "Who should connect"], [
        ("/topic/rider/{riderId}", "RIDE_REQUEST, RIDE_CANCELLED, RIDE_CANCELLED_BY_USER", "Rider app; public topic subscription is not yet per-rider authorization protected."),
        ("/topic/user/{adminId}", "RIDE_ACCEPTED, RIDER_CANCELLED, RIDE_STARTED, RIDE_COMPLETED", "Booking admin app; current public topic."),
        ("/topic/ride/{rideId}", "RIDE_STARTED, RIDE_COMPLETED", "Ride screen; current public topic."),
        ("/user/queue/ride/{rideId}/location", "RIDER_LOCATION_UPDATED", "Only the booking user's correctly authenticated WebSocket session."),
    ], [2.15, 2.55, 1.55], body_size=8.05)
    heading(document, "CodePen HTML", 2)
    code_block(document, '''<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
<pre id="output">Connecting...</pre>
<script>
const output = document.getElementById("output");
const ADMIN_JWT = "PASTE_ARIYAAN_ADMIN_JWT_HERE";
const RIDE_ID = 2;
const client = new StompJs.Client({
  webSocketFactory: () => new SockJS("{{baseUrl}}/ws"),
  connectHeaders: { Authorization: "Bearer " + ADMIN_JWT },
  reconnectDelay: 5000,
  debug: console.log,
  onConnect: () => {
    output.textContent = "Connected. Subscribed to private ride location.";
    client.subscribe(`/user/queue/ride/${RIDE_ID}/location`, message => {
      output.textContent += "\\n\\nReceived:\\n" + message.body;
    });
  },
  onStompError: frame => {
    output.textContent = "STOMP error: " + frame.headers["message"];
  }
});
client.activate();
</script>''')
    add_note(document, "Replace {{baseUrl}} with the actual base URL in CodePen and replace the JWT placeholder with the real booking admin token. Do not paste real JWTs into a public CodePen or screenshot.")


def database_and_migrations(document):
    flow_section(document, "10. Database reference and migration order")
    base_paragraph(document, "Use docs/DATABASE_SETUP_AND_REFERENCE.sql for a clean PostgreSQL database. The schema includes constraints and a safe Haversine query that returns verified, fresh, AVAILABLE riders within 10 km.", size=10.5)
    add_table(document, ["Table", "Purpose", "Key relationship / rule"], [
        ("USERS", "All application login accounts", "ROLE check: ADMIN or RIDER. USER_NAME and EMAIL are unique."),
        ("RIDER_DETAILS", "Operational rider profile", "USER_ID is UNIQUE FK to USERS.ID; status AVAILABLE / ON_TRIP / OFFLINE."),
        ("RIDER_VERIFICATION_DETAILS", "KYC/bank record", "RIDER_ID is PK + FK to RIDER_DETAILS.ID; exactly one record per rider."),
        ("RIDER_LOCATION", "Current GPS point", "RIDER_ID is PK + FK; atomic upsert means one row per rider."),
        ("RIDE_DETAILS", "Booking and lifecycle", "RIDE_ID PK; vehicle name, chassis no., rider/admin IDs, OTP, status."),
        ("RIDE_REQUEST", "Matching candidates", "Unique (RIDE_ID, RIDER_ID) avoids duplicate candidate notification rows."),
        ("INVOICES", "Completion invoice", "RIDE_ID is unique, so only one invoice can exist per ride."),
    ], [1.85, 2.15, 2.25], body_size=8.2)
    heading(document, "Migrations", 2)
    add_table(document, ["Version", "Purpose"], [
        ("V1", "Create RIDE_REQUEST."),
        ("V2", "Legacy RIDER_DEMO status migration; current entity/schema uses RIDER_DETAILS."),
        ("V3", "Create INVOICES."),
        ("V4", "Add ride OTP expiry column (later removed)."),
        ("V5", "Remove ride OTP expiry; ride-start OTP no longer has time expiry."),
        ("V6", "Create RIDER_VERIFICATION_DETAILS."),
        ("V7", "Add RIDER_DETAILS.USER_ID, unique index, and FK to USERS."),
        ("V8", "Add USERS.ROLE with ADMIN/RIDER check; existing users default to ADMIN."),
        ("V9", "Add RIDE_DETAILS.CHASSIS_NO and VEHICLE_NAME for ride-request vehicle details."),
    ], [0.8, 5.45], body_size=8.5)
    add_note(document, "Render is configured with Flyway disabled in production. Apply V7, V8, and V9 manually to the Render-connected Neon branch before deploying code that references USERS.ROLE, RIDER_DETAILS.USER_ID, CHASSIS_NO, or VEHICLE_NAME.")
    heading(document, "Useful database validation queries", 2)
    code_block(document, '''-- Confirm account role and rider mapping
SELECT u.id, u.user_name, u.role, r.id AS rider_id, r.status
FROM users u LEFT JOIN rider_details r ON r.user_id = u.id;

-- Confirm rider verification linkage
SELECT r.id, r.name, v.rider_id, v.is_verified
FROM rider_details r
LEFT JOIN rider_verification_details v ON v.rider_id = r.id;

-- Confirm an active ride for a rider
SELECT ride_id, rider_id, admin_id, vehicle_name, chassis_no, ride_status
FROM ride_details
WHERE rider_id = 3 AND ride_status IN ('ACCEPTED', 'STARTED');

-- Confirm an invoice after ride completion
SELECT * FROM invoices WHERE ride_id = 2;''')


def troubleshooting(document):
    flow_section(document, "11. Troubleshooting and production controls")
    add_table(document, ["Symptom", "Cause", "Correct action"], [
        ("column u1_0.role does not exist", "Code deployed before V8 schema migration", "Apply V8 to the exact Neon branch Render uses."),
        ("UsernameNotFoundException: Rahul", "JWT subject is a rider display name, not USERS.USER_NAME", "Use a JWT whose subject is an existing login username, for example vijay."),
        ("No active ride is assigned", "Rider calls /fetch/livelocation without ACCEPTED/STARTED ride", "Use /save/location before matching; use /fetch/livelocation only after acceptance."),
        ("Rider KYC saved under wrong rider", "URL path used another rider ID", "Call /rider/3/verification-details for rider ID 3; body never controls riderId."),
        ("WebSocket connected but no GPS message", "Wrong JWT identity, wrong private destination, old message, or no active assigned ride", "Connect booking admin's JWT, subscribe /user/queue/ride/{rideId}/location, then make a fresh rider POST."),
        ("No nearby rider", "Not verified, unavailable, stale location, or beyond 10 km", "Patch verified=true, save fresh rider location, set AVAILABLE, and check coordinates."),
    ], [1.72, 2.35, 2.18], body_size=7.95)
    heading(document, "Before production", 2)
    bullets(document, [
        "Remove or secure GET /sendJWT. It must never issue tokens for arbitrary usernames in production.",
        "Make /login deliberately public only after confirming authentication, rate limits, and password policy; currently it is blocked by SecurityConfig.",
        "Encrypt Aadhaar, PAN, licence, and bank data at rest; redact it from logs and avoid returning it in ordinary mobile responses.",
        "Ownership checks are implemented for /ride/accept. Add the same authenticated-rider ownership checks to /ride/verify-otp, /ride/end, and cancellation endpoints so a rider token cannot submit another rider's ID.",
        "Authorize subscriptions to public /topic/rider, /topic/user, and /topic/ride destinations. The live GPS destination already uses a private user queue.",
        "Store mobile JWTs in Android Keystore/iOS Keychain-backed secure storage; clear active ride data on completion or logout.",
    ])
    heading(document, "Final acceptance checklist", 2)
    add_table(document, ["Check", "Pass condition"], [
        ("Schema", "USERS.ROLE and RIDER_DETAILS.USER_ID exist on the same Neon branch Render uses."),
        ("Roles", "ADMIN cannot call rider location endpoints; RIDER cannot create a ride."),
        ("Matching", "Only a verified, AVAILABLE rider with a location newer than 10 minutes appears within 10 km."),
        ("Tracking", "Booking admin receives a fresh rider location at /user/queue/ride/{rideId}/location."),
        ("Lifecycle", "A rider can accept only their own SENT request while AVAILABLE, then start with the correct OTP and end their assigned ride."),
        ("Invoice", "A completed ride produces exactly one PENDING invoice with the ride fare."),
    ], [1.45, 5.15], body_size=8.35)


def make_document():
    document = configure_document()
    cover(document)
    setup_and_roles(document)
    endpoint_catalog(document)
    sequence(document)
    account_tests(document)
    profile_tests(document)
    rider_tests(document)
    ride_and_tracking(document)
    completion_and_utilities(document)
    websocket(document)
    database_and_migrations(document)
    troubleshooting(document)
    document.save(OUT)
    print(OUT)


if __name__ == "__main__":
    make_document()
