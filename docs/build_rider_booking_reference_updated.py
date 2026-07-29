from pathlib import Path

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


OUT = Path(__file__).with_name("RIDER_BOOKING_API_AND_DATABASE_REFERENCE_UPDATED.docx")
BLUE = "2E74B5"
NAVY = "0B2545"
DARK_BLUE = "1F4D78"
HEADER_FILL = "E8EEF5"
CODE_FILL = "F5F8FC"
BODY_WIDTH = 6.4


def set_font(run, size=11, bold=False, color=None, name="Calibri"):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:ascii"), name)
    run._element.rPr.rFonts.set(qn("w:hAnsi"), name)
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = RGBColor.from_string(color)


def add_run(paragraph, text, **kwargs):
    run = paragraph.add_run(text)
    set_font(run, **kwargs)
    return run


def shade(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, top=72, start=120, bottom=72, end=120):
    tc_pr = cell._tc.get_or_add_tcPr()
    margins = tc_pr.first_child_found_in("w:tcMar")
    if margins is None:
        margins = OxmlElement("w:tcMar")
        tc_pr.append(margins)
    for side, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = margins.find(qn(f"w:{side}"))
        if node is None:
            node = OxmlElement(f"w:{side}")
            margins.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_table_geometry(table, widths):
    """Set consistent table, grid, and cell widths in DXA."""
    widths_dxa = [round(value * 1440) for value in widths]
    table.autofit = False
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    tbl_pr = table._tbl.tblPr
    tbl_w = tbl_pr.first_child_found_in("w:tblW")
    if tbl_w is None:
        tbl_w = OxmlElement("w:tblW")
        tbl_pr.append(tbl_w)
    tbl_w.set(qn("w:w"), str(sum(widths_dxa)))
    tbl_w.set(qn("w:type"), "dxa")
    tbl_ind = tbl_pr.first_child_found_in("w:tblInd")
    if tbl_ind is None:
        tbl_ind = OxmlElement("w:tblInd")
        tbl_pr.append(tbl_ind)
    tbl_ind.set(qn("w:w"), "120")
    tbl_ind.set(qn("w:type"), "dxa")
    grid_cols = table._tbl.tblGrid.gridCol_lst
    for column, width in zip(grid_cols, widths_dxa):
        column.set(qn("w:w"), str(width))
    for row in table.rows:
        for index, width in enumerate(widths_dxa):
            cell = row.cells[index]
            cell.width = Inches(width / 1440)
            tc_pr = cell._tc.get_or_add_tcPr()
            tc_w = tc_pr.find(qn("w:tcW"))
            if tc_w is None:
                tc_w = OxmlElement("w:tcW")
                tc_pr.append(tc_w)
            tc_w.set(qn("w:w"), str(width))
            tc_w.set(qn("w:type"), "dxa")


def repeat_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    header = OxmlElement("w:tblHeader")
    header.set(qn("w:val"), "true")
    tr_pr.append(header)


def base_paragraph(document, text="", size=10.5, bold=False, color=None, after=6, before=0):
    paragraph = document.add_paragraph()
    paragraph.paragraph_format.space_before = Pt(before)
    paragraph.paragraph_format.space_after = Pt(after)
    paragraph.paragraph_format.line_spacing = 1.18
    if text:
        add_run(paragraph, text, size=size, bold=bold, color=color)
    return paragraph


def heading(document, text, level=1):
    paragraph = document.add_paragraph()
    paragraph.paragraph_format.space_before = Pt(16 if level == 1 else 11 if level == 2 else 8)
    paragraph.paragraph_format.space_after = Pt(7 if level == 1 else 5)
    paragraph.paragraph_format.keep_with_next = True
    size = 16 if level == 1 else 13 if level == 2 else 11.5
    add_run(paragraph, text, size=size, bold=True, color=BLUE if level < 3 else DARK_BLUE)
    return paragraph


def bullets(document, values):
    for value in values:
        paragraph = document.add_paragraph(style="List Bullet")
        paragraph.paragraph_format.space_after = Pt(5)
        paragraph.paragraph_format.line_spacing = 1.15
        paragraph.paragraph_format.keep_together = True
        add_run(paragraph, value, size=10.5)


def add_table(document, headers, rows, widths, body_size=8.3):
    table = document.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    for index, value in enumerate(headers):
        cell = table.rows[0].cells[index]
        cell.text = ""
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        shade(cell, HEADER_FILL)
        set_cell_margins(cell)
        add_run(cell.paragraphs[0], value, size=8.8, bold=True, color=DARK_BLUE)
    repeat_header(table.rows[0])
    for row in rows:
        cells = table.add_row().cells
        for index, value in enumerate(row):
            cell = cells[index]
            cell.text = ""
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            set_cell_margins(cell)
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_after = Pt(0)
            paragraph.paragraph_format.line_spacing = 1.08
            add_run(paragraph, str(value), size=body_size)
    set_table_geometry(table, widths)
    return table


def code_block(document, text):
    lines = text.splitlines() or [""]
    for index, line in enumerate(lines):
        paragraph = document.add_paragraph()
        paragraph.paragraph_format.left_indent = Inches(0.16)
        paragraph.paragraph_format.right_indent = Inches(0.08)
        # Put spacing on a real line rather than on an empty spacer paragraph.
        # LibreOffice can collapse an empty paragraph and place the next block
        # alongside the last shaded line during PDF rendering.
        paragraph.paragraph_format.space_after = Pt(3 if index == len(lines) - 1 else 0)
        paragraph.paragraph_format.line_spacing = 1.0
        paragraph.paragraph_format.keep_together = True
        p_pr = paragraph._p.get_or_add_pPr()
        shd = OxmlElement("w:shd")
        shd.set(qn("w:fill"), CODE_FILL)
        p_pr.append(shd)
        add_run(paragraph, line or " ", size=8.4, color=DARK_BLUE, name="Consolas")


def expected_response(document, expected):
    """Render multiline responses as real paragraphs, not soft line breaks."""
    lines = expected.splitlines() or [""]
    paragraph = base_paragraph(document, after=0)
    add_run(paragraph, "Expected result: ", size=9.4, bold=True, color=DARK_BLUE)
    add_run(paragraph, lines[0], size=9.4)
    last_paragraph = paragraph

    for line in lines[1:]:
        response_line = base_paragraph(document, after=0)
        response_line.paragraph_format.keep_together = True
        if line:
            add_run(response_line, line, size=9.4)
        else:
            # A visible-height blank line preserves grouping without a renderer-
            # dependent empty paragraph.
            add_run(response_line, "\u00A0", size=5)
        last_paragraph = response_line

    last_paragraph.paragraph_format.space_after = Pt(5)


def endpoint_block(document, method, path, auth, request_kind, request_example, expected, note=None):
    heading(document, f"{method} {path}", 3)
    meta = base_paragraph(document, after=3)
    add_run(meta, "Authentication: ", size=9.4, bold=True, color=DARK_BLUE)
    add_run(meta, auth, size=9.4)
    add_run(meta, "   |   Request type: ", size=9.4, bold=True, color=DARK_BLUE)
    add_run(meta, request_kind, size=9.4)
    base_paragraph(document, "Postman test", size=9.4, bold=True, color=DARK_BLUE, after=2)
    code_block(document, request_example)
    expected_response(document, expected)
    if note:
        note_paragraph = base_paragraph(document, after=6)
        add_run(note_paragraph, "Note: ", size=9.2, bold=True, color="9C6500")
        add_run(note_paragraph, note, size=9.2, color="9C6500")


def page_break(document):
    document.add_page_break()


def section_heading(document, text):
    """Start a major section on a new page without an orphaned break paragraph."""
    paragraph = heading(document, text, 1)
    paragraph.paragraph_format.page_break_before = True
    return paragraph


def make_document():
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
    add_run(footer, "Internal engineering reference - updated 29 July 2026", size=8.5, color="666666")

    # Cover
    title = doc.add_paragraph()
    title.paragraph_format.space_before = Pt(72)
    title.paragraph_format.space_after = Pt(6)
    add_run(title, "RIDER BOOKING APPLICATION", size=24, bold=True, color=NAVY)
    subtitle = doc.add_paragraph()
    subtitle.paragraph_format.space_after = Pt(22)
    add_run(subtitle, "Complete API, Postman Test, Flow, and Database Reference", size=15, color=BLUE)
    base_paragraph(doc, "Scope: all currently mapped HTTP endpoints, WebSocket behavior, rider KYC/bank details, rider history, ride lifecycle, invoices, and PostgreSQL tables.", size=11, after=16)
    add_table(doc, ["Decision", "Implemented behavior"], [
        ("Ride-start OTP", "No clock-based expiry. It works only for the assigned rider while the ride remains ACCEPTED."),
        ("Rider matching", "Returns all AVAILABLE riders whose saved location is newer than 10 minutes and within 10 km."),
        ("Rider verification", "One verification record per rider. Each save resets verified to false; PATCH can set the boolean to true."),
        ("Invoice creation", "A successful end of a STARTED ride creates one PENDING invoice automatically."),
    ], [1.65, 4.75])
    heading(doc, "How to use this guide", 1)
    bullets(doc, [
        "Set the Postman variables shown in section 1, then run the test order before testing individual endpoints.",
        "Every endpoint has a real request shape: JSON, URL query parameters, form-data, or an HTML page request. Do not send JSON to a query/form endpoint.",
        "Use the companion SQL setup file for a clean PostgreSQL schema. Do not run the entire setup script on a Flyway-managed database.",
        "KYC and bank fields are highly sensitive. Restrict these APIs to authorized staff and use masking/encryption before a production deployment.",
    ])

    # Overview
    section_heading(doc, "1. Test setup and authentication")
    base_paragraph(doc, "Base URL: http://localhost:8088. Use Postman environment variables so the same tests work across environments.", size=10.5)
    add_table(doc, ["Variable", "Example", "How it is obtained"], [
        ("baseUrl", "http://localhost:8088", "Set manually."),
        ("username", "testuser01", "Created through POST /register or pre-existing in USERS."),
        ("jwt", "eyJ...", "For development, GET /sendJWT?username={{username}}; for a normal login flow, permit /login in security configuration first."),
        ("adminId", "1", "Existing USERS.ID used in POST /ride/request."),
        ("riderId", "1", "Returned by POST /rider."),
        ("rideId", "42", "Returned in the POST /ride/request response text."),
        ("rideOtp", "1234", "Delivered to the accepted rider via the RIDE_ACCEPTED WebSocket event or read from the database only in a test environment."),
        ("verificationToken", "123456", "Email/OTP token generated by registration or verification flow."),
    ], [1.25, 1.65, 3.5])
    heading(doc, "Authentication rules as currently implemented", 2)
    bullets(doc, [
        "Public: /register/**, /otp/**, /demo, /error, and /sendJWT.",
        "Bearer JWT required: /login, /api/auth/validate, /api/fare/**, /rider/**, and /ride/**.",
        "Important: /login is protected by the current security configuration, so an anonymous login request receives 403. Add /login to the permit list before treating it as a standard login API.",
        "Important: /sendJWT is public and can issue a token for any supplied username. It is useful for local testing only and must be restricted before deployment.",
    ])
    heading(doc, "Common Postman headers", 2)
    code_block(doc, "Authorization: Bearer {{jwt}}\nContent-Type: application/json")
    heading(doc, "Suggested end-to-end test order", 2)
    add_table(doc, ["Step", "Action", "Variables produced"], [
        ("1", "Create or identify an enabled application user; obtain a development JWT.", "username, adminId, jwt"),
        ("2", "Create a rider, then create its verification details and save a fresh location.", "riderId"),
        ("3", "Verify rider matching with GET /rider/nearby.", "riderId"),
        ("4", "Request a ride, accept it, obtain the ride OTP, and start it.", "rideId, rideOtp"),
        ("5", "End the ride, check rider history, and query the generated invoice in PostgreSQL.", "completed ride data"),
    ], [0.55, 4.15, 1.7])

    # Complete endpoint inventory
    section_heading(doc, "2. Complete HTTP endpoint inventory")
    base_paragraph(doc, "The application exposes 26 controller endpoints. The detailed Postman-ready tests follow in sections 3–7.", size=10.5)
    heading(doc, "Account and session endpoints", 2)
    add_table(doc, ["Method", "Path", "Auth", "Input"], [
        ("GET", "/register", "Public", "None; returns HTML view"),
        ("POST", "/register", "Public", "User JSON"),
        ("GET", "/register/confirmToken", "Public", "token query parameter"),
        ("GET", "/register/verifyEmail", "Public", "emailAddress query parameter"),
        ("POST", "/register/updateUser", "Public", "x-www-form-urlencoded User fields"),
        ("GET", "/demo", "Public", "None; returns HTML view"),
        ("POST", "/login", "JWT required currently", "Login JSON"),
        ("GET", "/sendJWT", "Public", "username query parameter"),
        ("POST", "/otp/send/sms", "Public", "phone and userName query parameters"),
        ("POST", "/otp/verify", "Public", "key, otp, username query parameters"),
        ("GET", "/api/auth/validate", "JWT", "Bearer token"),
    ], [0.65, 2.25, 1.1, 2.4])
    heading(doc, "Rider, ride, and utility endpoints", 2)
    add_table(doc, ["Method", "Path", "Auth", "Input"], [
        ("POST", "/rider", "JWT", "Rider JSON"),
        ("POST", "/rider/{riderId}/verification-details", "JWT", "Verification JSON"),
        ("GET", "/rider/{riderId}/verification-details", "JWT", "Path variable"),
        ("PATCH", "/rider/{riderId}/verification", "JWT", "verified boolean query parameter"),
        ("GET", "/rider/{riderId}/history", "JWT", "Path variable"),
        ("POST", "/rider/fetch/livelocation", "JWT", "Location JSON"),
        ("POST", "/rider/save/location", "JWT", "Location JSON"),
        ("GET", "/rider/nearby", "JWT", "lat and lon query parameters"),
        ("POST", "/ride/request", "JWT", "Ride JSON"),
        ("POST", "/ride/accept", "JWT", "rideId and riderId query parameters"),
        ("POST", "/ride/verify-otp", "JWT", "rideId, riderId, otp query parameters"),
        ("POST", "/ride/end", "JWT", "rideId and riderId query parameters"),
        ("POST", "/ride/rider-cancel", "JWT", "rideId and riderId query parameters"),
        ("POST", "/ride/cancel", "JWT", "rideId query parameter"),
        ("GET", "/api/fare/calculate", "JWT", "sLat, sLon, dLat, dLon query parameters"),
    ], [0.65, 2.65, 0.9, 2.2])

    # Account detail endpoints. The inventory tables above fill the preceding
    # page, so let Word flow this section naturally instead of inserting a
    # second page break that would create an empty page.
    heading(doc, "3. Account and session endpoint tests", 1)
    endpoint_block(doc, "GET", "/register", "None (public)", "No body", "GET {{baseUrl}}/register", "200 OK with the registration HTML view, not JSON.")
    endpoint_block(doc, "POST", "/register", "None (public)", "raw JSON", '''POST {{baseUrl}}/register
Content-Type: application/json

{
  "firstName": "Test",
  "lastName": "User",
  "userName": "{{username}}",
  "password": "Test@12345",
  "email": "testuser01@example.com",
  "mobileNumber": "9999999999",
  "mobileOtp": ""
}''', '''200 OK
{
  "success": true,
  "message": "Please confirm your email address"
}''', "The user starts disabled; a confirmation token is stored on the USERS row and sent by the configured email service.")
    endpoint_block(doc, "GET", "/register/confirmToken", "None (public)", "Query parameter", "GET {{baseUrl}}/register/confirmToken?token={{verificationToken}}", '''200 OK on a valid unused token, typically:
{
  "message": "OTP verified Successfully",
  "token": "<jwt>"
}''', "Invalid, expired, or previously confirmed tokens go through the global exception handler.")
    endpoint_block(doc, "GET", "/register/verifyEmail", "None (public)", "Query parameter", "GET {{baseUrl}}/register/verifyEmail?emailAddress=testuser01@example.com", "200 OK with body true when the email exists and a new token was sent; false when it does not exist.")
    endpoint_block(doc, "POST", "/register/updateUser", "None (public)", "x-www-form-urlencoded", '''POST {{baseUrl}}/register/updateUser
Content-Type: application/x-www-form-urlencoded

firstName=Test
lastName=User
userName={{username}}
password=Test@12345
email=testuser01@example.com
mobileNumber=9999999999''', "200 OK with plain text `user updatedb successfully` as currently coded.", "This legacy endpoint binds a form model; it does not accept a JSON request body.")

    heading(doc, "Account and session tests continued", 1)
    endpoint_block(doc, "GET", "/demo", "None (public)", "No body", "GET {{baseUrl}}/demo", "200 OK with the `home` HTML view. It also calls a demo service that attempts to create a hard-coded rider.", "Use only for local demonstrations; it changes database state.")
    endpoint_block(doc, "POST", "/login", "Bearer JWT required by current configuration", "raw JSON", '''POST {{baseUrl}}/login
Content-Type: application/json

{
  "username": "{{username}}",
  "password": "Test@12345"
}''', '''If /login is added to permitAll and credentials are valid:
200 OK
{"token":"<jwt>"}

With the current SecurityConfig and no Bearer token:
403 Forbidden before this controller method runs.''', "The endpoint is mapped, but it is not a usable anonymous login flow until SecurityConfig permits /login.")
    endpoint_block(doc, "GET", "/sendJWT", "None (public)", "Query parameter", "GET {{baseUrl}}/sendJWT?username={{username}}", '''200 OK
{
  "message": "JWT Token has been generated successfully on the basis of Username",
  "token": "<jwt>"
}''', "For subsequent protected calls, the token subject must match a user that exists in USERS. Restrict or remove this endpoint outside local testing.")
    endpoint_block(doc, "POST", "/otp/send/sms", "None (public)", "Query parameters", "POST {{baseUrl}}/otp/send/sms?phone=9999999999&userName={{username}}", "200 OK with plain text confirming that the OTP was sent.")
    endpoint_block(doc, "POST", "/otp/verify", "None (public)", "Query parameters", "POST {{baseUrl}}/otp/verify?key=9999999999&otp=123456&username={{username}}", '''Success:
{"message":"OTP verified Successfully","token":"<jwt>"}

Failure:
{"message":"Verification Failed"}''')
    endpoint_block(doc, "GET", "/api/auth/validate", "Bearer {{jwt}}", "No body", "GET {{baseUrl}}/api/auth/validate\nAuthorization: Bearer {{jwt}}", '''200 OK
{
  "valid": true,
  "username": "{{username}}",
  "message": "Welcome back!"
}''')

    # Rider endpoints
    heading(doc, "4. Rider profile, KYC, and location endpoint tests", 1)
    endpoint_block(doc, "POST", "/rider", "Bearer {{jwt}}", "raw JSON", '''POST {{baseUrl}}/rider
Authorization: Bearer {{jwt}}
Content-Type: application/json

{
  "name": "Rahul Rider",
  "mobileNumber": "9876543210",
  "status": "AVAILABLE"
}''', '''201 Created
{
  "id": 1,
  "name": "Rahul Rider",
  "mobile_number": "9876543210",
  "status": "AVAILABLE"
}''', "Copy the returned id into the Postman variable riderId. Valid status values are AVAILABLE, ON_TRIP, and OFFLINE.")
    endpoint_block(doc, "POST", "/rider/{riderId}/verification-details", "Bearer {{jwt}}", "raw JSON", '''POST {{baseUrl}}/rider/{{riderId}}/verification-details
Authorization: Bearer {{jwt}}
Content-Type: application/json

{
  "aadhaarCardNumber": "123456789012",
  "panCardNumber": "ABCDE1234F",
  "drivingLicenseNumber": "MH0120230001234",
  "bankAccountNumber": "123456789012",
  "bankIfscCode": "HDFC0001234",
  "bankAccountName": "Rahul Sharma"
}''', '''201 Created
{
  "riderId": 1,
  "aadhaarCardNumber": "123456789012",
  "panCardNumber": "ABCDE1234F",
  "drivingLicenseNumber": "MH0120230001234",
  "bankAccountNumber": "123456789012",
  "bankIfscCode": "HDFC0001234",
  "bankAccountName": "Rahul Sharma",
  "verified": false
}''', "riderId comes from the path, not the JSON. The table uses RIDER_ID as its unique primary key; saving again updates the same record and resets verified to false.")

    heading(doc, "Rider KYC and history tests continued", 1)
    endpoint_block(doc, "GET", "/rider/{riderId}/verification-details", "Bearer {{jwt}}", "No body", "GET {{baseUrl}}/rider/{{riderId}}/verification-details\nAuthorization: Bearer {{jwt}}", "200 OK with the stored verification-details JSON shown in the preceding POST example.", "The current implementation returns unmasked sensitive information. Add staff authorization and response masking before production.")
    endpoint_block(doc, "PATCH", "/rider/{riderId}/verification", "Bearer {{jwt}}", "Boolean query parameter", "PATCH {{baseUrl}}/rider/{{riderId}}/verification?verified=true\nAuthorization: Bearer {{jwt}}", "200 OK with the verification-details object and `verified: true`.", "Send `true` or `false`, not `Y` or `N`.")
    endpoint_block(doc, "GET", "/rider/{riderId}/history", "Bearer {{jwt}}", "No body", "GET {{baseUrl}}/rider/{{riderId}}/history\nAuthorization: Bearer {{jwt}}", '''200 OK
[
  {
    "rideId": 42,
    "pickUpLatitude": 19.076,
    "pickUpLongitude": 72.8777,
    "dropLatitude": 19.11,
    "dropLongitude": 72.9,
    "fare": 125.0,
    "riderId": 1,
    "adminId": 1,
    "rideOtp": 1234,
    "rideStatus": "COMPLETED"
  }
]''', "Returns rides whose RIDE_DETAILS.RIDER_ID equals the path riderId, newest ride ID first. No pagination is implemented.")
    endpoint_block(doc, "POST", "/rider/fetch/livelocation", "Bearer {{jwt}}", "raw JSON", '''POST {{baseUrl}}/rider/fetch/livelocation
Authorization: Bearer {{jwt}}
Content-Type: application/json

{
  "rideId": {{rideId}},
  "riderId": {{riderId}},
  "latitude": 19.0760,
  "longitude": 72.8777
}''', "200 OK with an empty body. The same location object is published to WebSocket destination `/topic/ride/{rideId}`; it is not saved to the database.")

    heading(doc, "Rider location and matching tests", 1)
    endpoint_block(doc, "POST", "/rider/save/location", "Bearer {{jwt}}", "raw JSON", '''POST {{baseUrl}}/rider/save/location
Authorization: Bearer {{jwt}}
Content-Type: application/json

{
  "rideId": 0,
  "riderId": {{riderId}},
  "latitude": 19.0760,
  "longitude": 72.8777
}''', "200 OK with an empty body. PostgreSQL inserts or updates the rider's single RIDER_LOCATION row and refreshes UPDATED_AT.", "rideId is required by the DTO even though this save operation does not use it; use 0 for a standalone location update.")
    endpoint_block(doc, "GET", "/rider/nearby", "Bearer {{jwt}}", "Query parameters", "GET {{baseUrl}}/rider/nearby?lat=19.0760&lon=72.8777\nAuthorization: Bearer {{jwt}}", "200 OK with an array of rider IDs, for example `[1, 2]`.", "Only AVAILABLE riders at a saved location newer than 10 minutes and within 10 km are returned. The response exposes IDs only, not distance/name/location.")
    heading(doc, "Matching tests", 2)
    bullets(doc, [
        "Create two AVAILABLE riders with locations within 10 km, and one farther away. Confirm only the close riders appear.",
        "Set a close rider's STATUS to ON_TRIP or OFFLINE and confirm it disappears from the result.",
        "Set UPDATED_AT to 10 minutes old or older and confirm that rider is ignored.",
    ])

    # Ride endpoints
    section_heading(doc, "5. Ride lifecycle endpoint tests")
    endpoint_block(doc, "POST", "/ride/request", "Bearer {{jwt}}", "raw JSON", '''POST {{baseUrl}}/ride/request
Authorization: Bearer {{jwt}}
Content-Type: application/json

{
  "pickUpLatitude": 19.0760,
  "pickUpLongitude": 72.8777,
  "dropLatitude": 19.1100,
  "dropLongitude": 72.9000,
  "fare": 125.00,
  "adminId": {{adminId}}
}''', "200 OK with plain text `Ride Request Sent, id=42`. Copy the numeric id to `rideId`.", "Do not send rideId, riderId, rideOtp, or rideStatus in this body. The service generates/controls them; a nonzero submitted riderId can block later acceptance. It then creates RIDE_REQUEST rows for matching riders and publishes RIDE_REQUEST events.")
    endpoint_block(doc, "POST", "/ride/accept", "Bearer {{jwt}}", "Query parameters", "POST {{baseUrl}}/ride/accept?rideId={{rideId}}&riderId={{riderId}}\nAuthorization: Bearer {{jwt}}", "200 OK `Ride accepted` for the first successful candidate; 400 `Ride already taken` if another rider already won.", "Listen to `/topic/user/{adminId}` for the RIDE_ACCEPTED event containing the OTP in the current implementation.")
    endpoint_block(doc, "POST", "/ride/verify-otp", "Bearer {{jwt}}", "Query parameters", "POST {{baseUrl}}/ride/verify-otp?rideId={{rideId}}&riderId={{riderId}}&otp={{rideOtp}}\nAuthorization: Bearer {{jwt}}", "200 OK `OTP Verified. Ride Started` when rider, status, and OTP are valid.", "There is no time expiry. The same OTP fails if the ride is not ACCEPTED, the rider does not match, or the value is wrong.")

    section_heading(doc, "Ride cancellation and completion tests")
    endpoint_block(doc, "POST", "/ride/end", "Bearer {{jwt}}", "Query parameters", "POST {{baseUrl}}/ride/end?rideId={{rideId}}&riderId={{riderId}}\nAuthorization: Bearer {{jwt}}", "200 OK `Ride ended successfully` only for an assigned rider while the ride is STARTED.", "This changes the ride to COMPLETED, makes the rider AVAILABLE, creates one PENDING invoice, and publishes RIDE_COMPLETED.")
    endpoint_block(doc, "POST", "/ride/rider-cancel", "Bearer {{jwt}}", "Query parameters", "POST {{baseUrl}}/ride/rider-cancel?rideId={{rideId}}&riderId={{riderId}}\nAuthorization: Bearer {{jwt}}", "200 OK `Rider cancellation handled and rematch attempted`.", "Only the assigned rider causes a cancellation/rematch. The method returns 200 even when the supplied rider is not assigned.")
    endpoint_block(doc, "POST", "/ride/cancel", "Bearer {{jwt}}", "Query parameter", "POST {{baseUrl}}/ride/cancel?rideId={{rideId}}\nAuthorization: Bearer {{jwt}}", "200 OK `Ride cancelled by user/admin`.", "Sets ADMIN_CANCELLED and cancels RIDE_REQUEST rows; it notifies an assigned rider if one exists.")
    heading(doc, "Ride lifecycle assertions", 2)
    add_table(doc, ["After endpoint", "Assert in API/WebSocket", "Assert in database"], [
        ("/ride/request", "RIDE_REQUEST event received by qualifying rider.", "RIDE_DETAILS is REQUESTED; matching RIDE_REQUEST rows are SENT."),
        ("/ride/accept", "RIDE_ACCEPTED event contains the ride ID and OTP.", "Winner is assigned; other RIDE_REQUEST rows are CANCELLED."),
        ("/ride/verify-otp", "RIDE_STARTED event is published.", "RIDE_DETAILS is STARTED; rider status is ON_TRIP."),
        ("/ride/end", "RIDE_COMPLETED event includes invoiceId.", "RIDE_DETAILS is COMPLETED; one INVOICES row is PENDING; rider is AVAILABLE."),
    ], [1.2, 2.55, 2.85])

    # Utilities + WebSocket
    section_heading(doc, "6. Fare, demo, and WebSocket tests")
    endpoint_block(doc, "GET", "/api/fare/calculate", "Bearer {{jwt}}", "Query parameters", "GET {{baseUrl}}/api/fare/calculate?sLat=19.0760&sLon=72.8777&dLat=19.1100&dLon=72.9000\nAuthorization: Bearer {{jwt}}", "200 OK with a plain string based on the Google distance response.", "The present code uses `% .2s` formatting and a literal placeholder rather than a numeric fare calculation, so output is truncated (for example, `Total Fare: ₹Ne`). Do not use it for billing.")
    heading(doc, "SockJS/STOMP connection", 2)
    base_paragraph(doc, "WebSocket path: `{{baseUrl}}/ws`. Use a SockJS/STOMP client and send the JWT in the CONNECT frame. The current interceptor reads this header, but its current behavior does not reliably reject an unauthenticated STOMP CONNECT; treat this as a security gap to fix.", size=10)
    add_table(doc, ["Subscription", "Expected event types", "How to test"], [
        ("/topic/rider/{riderId}", "RIDE_REQUEST, RIDE_CANCELLED, RIDE_CANCELLED_BY_USER", "Create a ride, then accept or cancel it."),
        ("/topic/user/{adminId}", "RIDE_ACCEPTED, RIDER_CANCELLED, RIDE_STARTED, RIDE_COMPLETED", "Follow the full ride sequence."),
        ("/topic/ride/{rideId}", "RiderLocationDTO, RIDE_STARTED, RIDE_COMPLETED", "POST /rider/fetch/livelocation, then start/end a ride."),
    ], [1.75, 2.3, 2.55])
    heading(doc, "WebSocket location test message", 2)
    code_block(doc, '''STOMP CONNECT headers
Authorization: Bearer {{jwt}}

HTTP API that publishes the event
POST {{baseUrl}}/rider/fetch/livelocation
{
  "rideId": {{rideId}},
  "riderId": {{riderId}},
  "latitude": 19.0760,
  "longitude": 72.8777
}''')
    heading(doc, "Representative server events", 2)
    code_block(doc, '''RIDE_REQUEST
{"type":"RIDE_REQUEST","rideId":{{rideId}},"pickupLat":19.076,
 "pickupLon":72.8777,"dropLat":19.11,"dropLon":72.9,"fare":125.0}

RIDE_ACCEPTED
{"type":"RIDE_ACCEPTED","rideId":{{rideId}},"riderId":{{riderId}},"otp":{{rideOtp}}}

RIDE_COMPLETED
{"type":"RIDE_COMPLETED","rideId":{{rideId}},"fare":125.0,"invoiceId":24}''')

    # Responses/error behaviour
    heading(doc, "7. Validation and error testing", 1)
    base_paragraph(doc, "Use these negative tests to validate that the client handles errors correctly. The global exception handler returns JSON for DTO validation, ride lookup/status failures, and uncaught exceptions.", size=10.5)
    add_table(doc, ["Test", "Request", "Expected response"], [
        ("Missing rider name", "POST /rider with `name` omitted", "400 VALIDATION_FAILED with details.name = `Rider name is required`."),
        ("Bad rider status", "POST /rider with `status: \"BUSY\"`", "400 VALIDATION_FAILED with the allowed-status message."),
        ("Missing verification field", "POST verification-details with bankIfscCode omitted", "400 VALIDATION_FAILED; the blank field is named in details."),
        ("Unknown rider verification", "GET /rider/999999/verification-details", "Current generic handler returns 500 INTERNAL_SERVER_ERROR because IllegalArgumentException has no specific mapping."),
        ("Wrong ride OTP", "POST /ride/verify-otp with an incorrect otp", "400 INVALID_RIDE_STATUS with an OTP-incorrect message."),
        ("End before start", "POST /ride/end for an ACCEPTED ride", "400 INVALID_RIDE_STATUS because status must be STARTED."),
        ("No/invalid JWT", "Call a protected /rider or /ride endpoint", "401/403 depending on the Spring Security/JWT failure path."),
    ], [1.4, 2.45, 2.75])
    base_paragraph(doc, "Validation annotations on RiderLocationDTO are not enforced by the two location endpoints because those controller parameters do not use @Valid. Send all four location fields in tests anyway.", size=9.4, color="9C6500", after=5)
    heading(doc, "Sample validation response", 2)
    code_block(doc, '''{
  "timestamp": "2026-07-29T12:00:00",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "details": {
    "name": "Rider name is required"
  }
}''')

    # Database
    section_heading(doc, "8. Database reference")
    base_paragraph(doc, "The companion script `DATABASE_SETUP_AND_REFERENCE.sql` creates the complete PostgreSQL schema for a new database. The following inventory includes every physical table used by the current mappings.", size=10.5)
    add_table(doc, ["Table", "Purpose", "Primary/key columns"], [
        ("USERS", "Application account, confirmation token, mobile OTP, and enabled state.", "ID; USER_NAME and EMAIL indexed uniquely."),
        ("RIDER_DETAILS", "Rider identity, mobile number, and availability state.", "ID; STATUS is AVAILABLE, ON_TRIP, or OFFLINE."),
        ("RIDER_VERIFICATION_DETAILS", "Aadhaar, PAN, licence, and bank details for one rider.", "RIDER_ID is both PK and FK to RIDER_DETAILS.ID; IS_VERIFIED defaults false."),
        ("RIDER_LOCATION", "One current location per rider.", "RIDER_ID PK/FK; coordinates and UPDATED_AT."),
        ("RIDE_DETAILS", "Ride route, fare, OTP, assignment, and lifecycle state.", "RIDE_ID; RIDER_ID, ADMIN_ID, RIDE_STATUS."),
        ("RIDE_REQUEST", "One matching/notification record per ride and candidate rider.", "ID; RIDE_ID, RIDER_ID, STATUS, CREATED_AT."),
        ("INVOICES", "One invoice created per successful ride completion.", "INVOICE_ID; RIDE_ID is unique; rider, user, amount, status."),
    ], [1.75, 2.65, 2.2])
    heading(doc, "Rider verification table relationship", 2)
    code_block(doc, '''RIDER_DETAILS (ID)
       1 ─────────────── 0..1 RIDER_VERIFICATION_DETAILS (RIDER_ID)

RIDER_ID is the verification table primary key and foreign key.
The API path /rider/{riderId}/verification-details supplies this identifier.''')
    heading(doc, "Useful validation queries", 2)
    code_block(doc, '''-- Verification record for a rider
SELECT * FROM RIDER_VERIFICATION_DETAILS WHERE RIDER_ID = 1;

-- Rider's assigned ride history
SELECT * FROM RIDE_DETAILS WHERE RIDER_ID = 1 ORDER BY RIDE_ID DESC;

-- Invoice created by a completed ride
SELECT * FROM INVOICES WHERE RIDE_ID = 42;

-- Fresh, available rider locations
SELECT rl.* FROM RIDER_LOCATION rl
JOIN RIDER_DETAILS rd ON rd.ID = rl.RIDER_ID
WHERE rd.STATUS = 'AVAILABLE'
  AND rl.UPDATED_AT > CURRENT_TIMESTAMP - INTERVAL '10 minutes';''')

    section_heading(doc, "9. Migration, deployment, and security checklist")
    heading(doc, "Migration order", 2)
    bullets(doc, [
        "The repository does not include baseline migrations for all tables. For a brand-new database, use DATABASE_SETUP_AND_REFERENCE.sql as the reliable complete schema script.",
        "V1 creates RIDE_REQUEST.",
        "V2 adds a status column to the legacy-named RIDER_DEMO table. The current entity uses RIDER_DETAILS, so verify the deployed table name or create a clean schema with the companion script.",
        "V3 creates INVOICES.",
        "V4 adds OTP_EXPIRES_AT; V5 removes it because ride-start OTP expiry was removed.",
        "V6 creates RIDER_VERIFICATION_DETAILS with RIDER_ID as the one-to-one primary/foreign key.",
    ])
    heading(doc, "Before deployment", 2)
    bullets(doc, [
        "Run `./mvnw test`, then run the Postman sequence against a real PostgreSQL database; the test context alone does not prove database connectivity or migration success.",
        "Apply only numbered Flyway migrations to an existing Flyway database. Run the consolidated setup script only for a new database.",
        "Restrict `/sendJWT`, allow `/login` deliberately if it should be public, and add role/ownership authorization for rider, ride, and verification operations.",
        "Encrypt Aadhaar, PAN, driving licence, and bank values at rest; do not log them or return them unmasked to non-admin callers.",
        "Move database, SMTP, SMS, and JWT secrets from local configuration into a secret manager or environment variables, then rotate any exposed credentials.",
        "Replace the placeholder fare string with a tested numeric fare formula before using it for invoices or payments.",
    ])
    heading(doc, "Document scope note", 2)
    base_paragraph(doc, "This guide documents current behavior in the checked-in controllers and services. Where a behavior is insecure, incomplete, or inconsistent, it is labelled as a current implementation note rather than presented as a production recommendation.", size=10.5)

    doc.save(OUT)
    print(OUT)


if __name__ == "__main__":
    make_document()
