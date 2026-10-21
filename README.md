# Electricity Billing System

A Java Swing desktop app for an electricity provider, built as a second-year university OOP project. Admins manage customers, meters and monthly bills; customers view, generate and pay their bills. Data is stored in PostgreSQL (Supabase), behind a small backend API.

## Quick start

```
python run.py
```

This one command checks your setup, builds the app if the code changed, starts the backend (which connects to the Supabase database), and opens the desktop app. Closing the app also stops the server it started.

| Command | What it does |
|---|---|
| `python run.py` | Build if needed, start the server, open the app |
| `python run.py --build` | Force a fresh build first |
| `python run.py --server` | Run only the backend (Ctrl+C to stop) |
| `python run.py --app` | Open only the app (server already running) |
| `python run.py --check` | Check Java, `.env`, the build and the database connection, then exit |

Needs Python 3.8+ and JDK 17+. The first time, copy `.env.example` to `.env` and fill it in (see Setup below).

## Features

Bills follow Bangladeshi rules: the **BERC slab tariff** (with the 0–50 unit lifeline rate), a demand charge on the sanctioned load, 5% VAT, meter rent, a due date and a 5% late fee. A real September 2026 Magura Palli Bidyut bill (235 units, Tk 1,963 / Tk 2,056 after the due date) is used as the test case.

| Admin | Customer |
|---|---|
| Add customers with the meter number printed on their meter, category and sanctioned load | Sign up with meter number + registered email |
| **Bill from a meter photo**: the server reads the display and checks the meter number; readings can't be typed | Home page with due date, overdue and high-usage alerts |
| **Bill checker** flags suspicious bills before they're saved | Bill history and a printable Bangladeshi-style invoice |
| Bills with due dates and an overdue filter (paid only by verified online payment) | **"Why is this bill different?"** explanation |
| Complaints: reply, set status, customer is notified | **Usage planner**: forecast from today's reading, slab alert, appliance calculator |
| Reports: collection rate, arrears by age, top defaulters, CSV export | Pay online with SSLCommerz (bKash, Nagad, Rocket, cards); late fee added once overdue |
| Team: list of all admins with last sign-in, add admins; tariffs page and an **activity (audit) log** |
| **Meter applications**: review, approve or reject, connect the installed meter | **New meter sign-up**: phone OTP, NID check (photos of both sides), then apply for a meter and follow its status | Notifications (new bill, payment, reminders) and complaints with ticket numbers |

See [docs/FEATURES-CHECKLIST.md](docs/FEATURES-CHECKLIST.md) for which features from the research were integrated and which weren't.

## Architecture

```
 Desktop app (Swing)  ──HTTP + JSON──▶  API server  ──JDBC──▶  Supabase PostgreSQL
 release/client/                        release/server/
 no database password                   the only holder of the database password
```

- **The app never touches the database.** It contains no database code or driver, so a copy of the app reveals nothing about the database.
- **The server decides who may do what.** Logging in returns a random session token (valid 8 hours). Every request is checked against the token's owner: admin-only actions need an admin token, and a customer can only read or change their own meter's data, whatever meter number they send.
- **Password guessing is limited.** After 5 wrong passwords, that username is locked for 15 minutes.
- **The database is closed to everything except the server.** Row-level security is on with no policies, which blocks Supabase's public Data API.

## Project structure

```
├── src/electricity/billing/system/
│   ├── Main.java     App entry point: theme, then splash screen             (app)
│   ├── ui/           Splash, sign-in and main windows; Page base class        (app)
│   │   ├── theme/        Theme (colours, fonts, light/dark), Icons, Fmt
│   │   ├── components/   Card, Btn, Sidebar, BarChart, DataTable, Toast, ...
│   │   └── pages/        admin/ and customer/ screens
│   ├── client/       ApiClient + AuthClient, CustomerClient, BillingClient   (app)
│   ├── server/       ApiServer, Router, Endpoints, SessionStore, LoginThrottle (server)
│   ├── service/      Auth, Customer, Billing, Payment, Complaint, Notification, Audit services (server)
│   ├── db/           Database + one DAO per table, PreparedStatement only   (server)
│   ├── security/     PasswordHasher, salted PBKDF2                           (server)
│   ├── meterphoto/   MeterPhotoReader: offline LCD and number-wheel readers + meter serial check  (server)
│   ├── billing/      Tariff ← NormalTariff (slabs) / IndustrialTariff (flat); BillCalculator,
│   │                 BillChecker, BillExplainer, SlabGuard                    (server)
│   ├── model/        Customer, Bill, Meter, TariffPlan, Complaint, ... records (shared)
│   └── common/       Api request/response records, Json, Config, Validation  (shared)
├── lib/              FlatLaf + Inter font (UI), Gson (JSON), PostgreSQL JDBC driver
├── database/         schema.sql (new database), migration-v2/v3/v4 (upgrades, run in order)
├── scripts/          build-release.ps1
├── release/          client/ and server/, each a ready-to-run JAR with its lib/
├── docs/             ISSUES-CHECKLIST.md, FEATURES-CHECKLIST.md
└── nbproject/, build.xml, manifest.mf    NetBeans project files
```

## OOP design

- **Encapsulation:** immutable records for domain data; pages never touch SQL or HTTP directly.
- **Abstraction and layering:** windows → API clients → HTTP → endpoints → services (business rules) → DAOs (SQL) → database.
- **Inheritance:** every screen extends `Page`; reusable widgets such as `StatCard` extend `Card`.
- **Polymorphism:** `Tariff.forBillType(...)` returns the pricing strategy for a customer category (telescopic slabs for homes, one flat rate for commercial); adding a new category means adding one class.

**Bill formula** (as on a Bangladeshi bill):

1. Units = reading in the meter photo − previous reading (from the last bill).
2. Energy charge, slab by slab (0–75 at Tk 5.26, 76–200 at Tk 8.50, …; 50 units or fewer in a month: all at the lifeline Tk 4.63).
3. + Demand charge = sanctioned load (kW) × Tk 42.
4. + 5% VAT on energy + demand charge.
5. + Meter rent (Tk 10). Rounded up to whole taka; due 15 days after the bill date.
6. After the due date: add a 5% late fee on energy + demand charge.

Rates live in the `tariff_plan` / `tariff_slab` tables, not in code. To bring in a new BERC order, add a plan with its effective date.

## Design

The interface uses a custom design system called **Midnight Grid**, built on [FlatLaf](https://www.formdev.com/flatlaf/) with the Inter font:

- **Colours:** an electric blue to aqua brand gradient, an amber accent and a deep navy sidebar. Light and dark modes switch live (moon or sun button) and the choice is remembered.
- **Layout:** one main window with sidebar navigation. Pages fade and slide in.
- **Motion:**
  - animated aurora "power-line" backgrounds on the splash and sign-in screens
  - count-up numbers and growing bar charts on the dashboards
  - a sliding sidebar highlight and cards that glow on hover
  - slide-in notifications, and a shake when a sign-in fails
- **Screens:**
  - **Admin:** dashboard with a billing chart, customer list with search, a 2-step "new customer" wizard, live bill preview as you type, bills with filters, and a team list of every admin
  - **Customer:** home with the latest bill and a usage chart, bill history, a printable invoice, a 2-step pay flow, and an editable profile
- **Icons:** all drawn in code (`ui/theme/Icons.java`), so they're sharp at any size.

## Setup

**Requirements:** JDK 17+, and a free [Supabase](https://supabase.com) project. For reading NID cards at sign-up, also install [Tesseract OCR](https://github.com/UB-Mannheim/tesseract/wiki) (free, offline) on the machine that runs the server: on Windows, `winget install UB-Mannheim.TesseractOCR`. The server's start-up log says which reader it uses; without Tesseract it falls back to a simpler built-in reader.

1. **Database.** In the Supabase SQL Editor, run:
   - [`database/schema.sql`](database/schema.sql) for a new database, or
   - [`database/migration-v2.sql`](database/migration-v2.sql) to upgrade a database made with the original schema, then
   - [`database/migration-v3-payments.sql`](database/migration-v3-payments.sql) to add online payments, then
   - [`database/migration-v4-bd-billing.sql`](database/migration-v4-bd-billing.sql) to add BERC tariffs, meter readings, due dates, complaints, notifications and the audit log, then
   - [`database/migration-v5-meter-photos.sql`](database/migration-v5-meter-photos.sql) to add meter serial numbers and meter photos, then
   - [`database/migration-v6-new-meter-applications.sql`](database/migration-v6-new-meter-applications.sql) to add the NID records, applicant accounts and meter applications, then
   - [`database/migration-v7-application-form.sql`](database/migration-v7-application-form.sql) to add the full application form's fields.
2. **Settings.** Copy [`.env.example`](.env.example) to `.env` and fill it in. The server needs the `SUPABASE_*` values; the app only needs `EBS_API_URL`.
3. **Start the server**, and leave it running:
   ```
   cd release/server
   java -jar ebs-server.jar
   ```
4. **Start the app**:
   ```
   cd release/client
   java -jar Electricity_Billing_System.jar
   ```
   From NetBeans, run `server/ApiServer.java` first, then `Main.java`.
5. **First login.** On the Signup screen, create the first admin ("Admin" is only offered while no admin exists). Add customers from the admin dashboard and give each one their meter number so they can sign up.

**Rebuilding the JARs** after changing the code:
`powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1`

### Running the server for other computers

By default the server only accepts connections from the same computer. To let others use the app:

1. Host the server where they can reach it, and set `EBS_SERVER_HOST=0.0.0.0` there.
2. Put it behind **HTTPS**, for example a hosting service that terminates TLS for you, or a reverse proxy. Without HTTPS, passwords and session tokens cross the network unencrypted.
3. Give users only the client folder and an `.env` containing `EBS_API_URL=https://your-server`. Never give them the `SUPABASE_*` values.

## Meter photo readings

Admins don't choose the customer or type a reading. To make a bill, the admin just uploads a photo of the meter, and the **server** reads it:

1. **Display:** finds the lit (green) LCD, straightens it, and reads the seven-segment digits by checking which segments are dark, e.g. `528.53` → 528 kWh. On an older electromechanical meter with no LCD, it reads the number wheels instead (light digits on dark drums). A last wheel caught between two numbers is read as the lowest possible digit, as meter readers do, and the bill preview warns the admin, who must acknowledge it before saving; a wheel in a red window counts tenths.
2. **Which meter:** finds the printed meter number (e.g. `No: E-756216`) and checks it against every customer's meter number on file, character by character against font templates. Exactly one must match; that customer is billed.
3. **Previous reading:** taken from that meter's last bill. Only for a meter's very first bill does the admin type it in.
4. **No guessing:** if any digit or segment is unclear, the display can be read more than one way, or the meter number matches no customer (or more than one), the photo is refused with advice on retaking it.
5. **Evidence:** the photo is stored with the bill. The same photo can't be used for two bills, and the previous reading always comes from the last bill. Admins and the customer can view the photo behind each bill.

It all runs offline in plain Java, with no external service. Tested on the sample meter photo and variations of it (resized, blurred, darker, brighter, low contrast, heavy JPEG, tilted ±3°, noisy): the display was read correctly in 19/19 cases and the serial confirmed in 18/19, and a wrong serial was never accepted.

**Limits:** it expects a backlit green LCD or dark number wheels with light digits, and a printed serial in a common sans-serif or narrow DIN-style font. Number groups printed apart on one line (`S81C   67062`) are read together. A grey (unlit) display, a handwritten or engraved serial, or a very blurred, angled or glare-covered photo is refused rather than read, and the admin retakes the photo. Someone determined could still photograph a different meter whose serial was altered to match; stored photos make that discoverable.

## Signing up for a new meter

The sign-up tab offers **I have a meter** (customers whose meter the office has registered) and **I need a new meter**:

1. **Phone:** verified one of three ways, chosen on the server:
   - **Firebase Phone Authentication** (real SMS from Google): the app opens a verification page in the browser (Firebase needs its reCAPTCHA check), Firebase texts the code, and the page sends Firebase's signed ID token back. The server checks the token's signature against Google's published keys, the project, the expiry and that it's for the same number. Set up below.
   - **Your own SMS gateway** (`SMS_MODE=http`).
   - **Demo mode** (default): the code is shown on screen.

   In the last two, a 6-digit code is sent by SMS (or shown on screen in demo mode). It expires in 5 minutes; 5 wrong tries or a resend inside 30 seconds are refused, and codes are stored only as hashes. Without an SMS gateway (the default **demo mode**) nothing is texted and the code is shown on screen and in the server console; set `SMS_MODE=http` and `SMS_HTTP_URL` in `.env` to send real SMS (see `.env.example`).
2. **NID:** the applicant uploads two photos: the **front** of their NID card (with the NID number) and the **back** (with the address and barcode). The front is read with **Tesseract OCR** on the server, several ways at once (as it is, sharpened, enlarged, black-and-white; turned if it's sideways or upside down; levelled if it's slanted), and the text from all the passes is pooled. The server finds which record in the **dummy NID database** (`nid_registry`) the card's NID number belongs to (an exact match), then checks that record's date of birth (exact) and English name (allowing a slip of a letter or two) are on the card too. Whatever Tesseract misses, the built-in reader gets a second look at. The back photo is required but not read; it only has to be a photo other than the front. One account per NID.
3. **Account:** a username and password create an **Applicant** account (sign in on the Customer side).

Applicants see **My application** and **Apply for a new meter**, which asks for everything DPDC's online new-connection form does (field list: [`docs/DPDC-new-connection-form-fields.txt`](docs/DPDC-new-connection-form-fields.txt)): consumer type (personal, or organization / institute; investors are pointed to BIDA), father's and mother's names, gender and spouse (personal) or the organization's name, a mailing and a connection address (house, road, section, block, division, district, thana, post code, e-mail, telephone, mobile; tick to copy one to the other), area and NOCS, connection type, phase, BERC tariff category, number of meters (and whether their owners differ), existing meters and load, and the declaration. The same rules apply: at least 2 kW per meter, the total load within the tariff's range, three-phase above 7 kW or on medium tension. The applicant's name, NID, date of birth and phone come from their verification; for an organization they are the proprietor's. Differences from DPDC's form: districts cover all of Bangladesh (not just Dhaka and Narayanganj), the thana is typed (with Dhaka and Narayanganj suggested), NOCS is optional (there's no office list to choose from), the connection address needs an e-mail (bills and payment receipts go there), and there's no captcha (applicants are already signed in after phone and NID checks). Tariff categories over 5000 kW can't be applied for here; each category is billed on the closer of this system's two tariffs. Admins review them on **Meter applications** (Submitted → Under review → Approved / Rejected, with a note to the applicant). Once the meter is installed, **Connect meter** with its printed number creates the customer, sets up the meter and turns the applicant's login into their customer login, all in one step.

**Important:** the NID records are manually stored sample data, not the Election Commission's database (which needs authorised access). Matching a card against them shows the card matches a stored record; it can't prove the card is genuine or that the applicant owns it. It's a simulated identity check for this project, not official authentication. In tests on two real cards (an old laminated one and a smart card, the latter only 490 × 301 pixels) and a drawn smart card, each in 13 versions (turned 90°, 180° or 270°, tilted 8°, dark, faded, glare, noisy, blurred, heavily compressed, shrunk to 400 px, skewed), 37 of 39 verified, usually in about a second; the two refused were heavily blurred versions of the small photos. None of the 78 checks against a wrong name or a wrong date of birth was accepted.

### Setting up Firebase Phone Authentication (real SMS to your phone)

1. At [console.firebase.google.com](https://console.firebase.google.com), create a project.
2. **Upgrade to the Blaze plan** (Spark, the free plan, can't send phone-auth SMS). A billing account with an international card is required; the first 10 SMS a day aren't billed, then about $0.19 per SMS to Bangladesh. Setting a budget alert in Google Cloud Billing is a good idea.
3. **Authentication → Sign-in method → Phone → Enable.** Check **Authentication → Settings → Authorized domains** lists `localhost` (it does by default).
4. **Project settings → General → Your apps → Add app → Web**, then copy `apiKey` and `projectId` into the server's `.env` as `FIREBASE_API_KEY` and `FIREBASE_PROJECT_ID` (see `.env.example`).
5. Restart the server. Its start-up log says *Phone verification for sign-up: Firebase Phone Authentication*.

Tip: under **Phone → Phone numbers for testing** you can add test numbers with fixed codes that never send an SMS, which is free.

## Payment flow

Bills are paid online through **[SSLCommerz](https://developer.sslcommerz.com/doc/v4/)**, Bangladesh's payment gateway (cards, mobile banking and more):

1. On **Pay bill**, the customer picks a bill and clicks **Pay with SSLCommerz**. The server records the payment, taking the amount from the bill (never from the app; after the due date, the amount including the late fee), and asks SSLCommerz for a secure checkout page, which opens in the browser.
2. After paying, SSLCommerz sends the browser back to the server. The server doesn't trust that redirect: it asks SSLCommerz directly, server to server, whether the transaction is valid, and checks that the transaction ID, amount and currency match its own record.
3. Only then is the bill marked **Paid**, automatically. The app is watching and shows the result straight away. Payments SSLCommerz rates as risky are still paid (the money was verified), and the flag is kept in the activity log.

There is no manual confirmation: neither customers nor admins can mark a bill paid by hand. A bill becomes **Paid** only when the server has verified the payment with SSLCommerz.

**Trying it for free (sandbox):** set the `SSLCOMMERZ_*` values in `.env` (see `.env.example`). The public test store `testbox` / `qwerty` works for learning, or [register your own sandbox store](https://developer.sslcommerz.com/registration/). On the sandbox checkout page, use test card **4111 1111 1111 1111**, expiry **12/26**, CVV **111**, OTP **111111**. No real money moves.

## Troubleshooting

- **"Can't reach the server"**: start the server first, and check `EBS_API_URL` in the app's `.env`.
- **Server says it can't connect to the database:** it prints the reason and a fix at start-up. Two common causes:
  - Using Supabase's direct address (`db.<project>.supabase.co`). It only works over IPv6, so use the **Session pooler** connection string instead.
  - Leftover `SUPABASE_DB_*` environment variables in Windows. These override `.env`; the start-up log shows where each setting came from.
- **"Missing SUPABASE_DB_URL"** (server): the server couldn't find `.env`. Run it from `release/server` or the project folder, or set the variables directly.
- **"Your session has expired"**: sessions last 8 hours and end when the server restarts. Log out and log in again.
- **"Too many failed logins"**: wait 15 minutes, or restart the server.
- **"No tariff is set up"**: run `database/migration-v4-bd-billing.sql` in the Supabase SQL Editor.
- **"has no earlier reading on file"**: the meter's first bill needs the previous reading typed in (from the meter's last paper bill). After that it's filled in from the last bill and can't be changed.
- **"doesn't match any customer's meter"**: the customer may have been added before meter numbers were recorded. Select them on the Customers page and click **Meter no. on meter** to enter the number printed on their meter.
- **"The meter number in the photo doesn't match"** / **"Couldn't find the meter's display"**: retake the photo straight on, close enough that the display and the "No:" serial fill most of the frame, in focus and without glare.

## Version 2 changes

This version fixes security issues, bugs and design problems found in a review of the original. Among them: open admin sign-up, account takeover through the customer sign-up, SQL injection, plain-text passwords, leaked database connections, a frozen UI, and the database password being shipped inside the app. See [docs/ISSUES-CHECKLIST.md](docs/ISSUES-CHECKLIST.md) for the full list and what's still open.
