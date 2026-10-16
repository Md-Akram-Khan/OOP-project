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

| Admin | Customer |
|---|---|
| Add customers (unique meter number generated automatically) | Sign up with meter number + registered email |
| Set up each meter's type and tariff | View and update their profile |
| Calculate monthly bills | View bill history |
| Search all bills and confirm payments | Generate a printable bill for any month |
| Add other admins | Pay online, then report the payment for confirmation |

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
│   ├── service/      AuthService, CustomerService, BillingService            (server)
│   ├── db/           Database + one DAO per table, PreparedStatement only   (server)
│   ├── security/     PasswordHasher, salted PBKDF2                           (server)
│   ├── billing/      Tariff interface ← NormalTariff, IndustrialTariff       (server)
│   ├── model/        Customer, Bill, Meter, TaxRates, UserAccount, enums     (shared)
│   └── common/       Api request/response records, Json, Config, Validation  (shared)
├── lib/              FlatLaf + Inter font (UI), Gson (JSON), PostgreSQL JDBC driver
├── database/         schema.sql (new database), migration-v2.sql + migration-v3-payments.sql (upgrades)
├── scripts/          build-release.ps1
├── release/          client/ and server/, each a ready-to-run JAR with its lib/
├── docs/             ISSUES-CHECKLIST.md
└── nbproject/, build.xml, manifest.mf    NetBeans project files
```

## OOP design

- **Encapsulation:** immutable records for domain data; pages never touch SQL or HTTP directly.
- **Abstraction and layering:** windows → API clients → HTTP → endpoints → services (business rules) → DAOs (SQL) → database.
- **Inheritance:** every screen extends `Page`; reusable widgets such as `StatCard` extend `Card`.
- **Polymorphism:** `Tariff.forBillType(...)` returns the pricing strategy for a meter; adding a new tariff means adding one class.

**Bill formula:** units × cost per unit (× industrial multiplier for Industrial meters) + meter rent + service charge + service tax + fixed tax.

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
  - **Admin:** dashboard with a billing chart, customer list with search, a 2-step "new customer" wizard, live bill preview as you type, bills with filters and one-click payment confirmation, and team management
  - **Customer:** home with the latest bill and a usage chart, bill history, a printable invoice, a 2-step pay flow, and an editable profile
- **Icons:** all drawn in code (`ui/theme/Icons.java`), so they're sharp at any size.

## Setup

**Requirements:** JDK 17+, and a free [Supabase](https://supabase.com) project.

1. **Database.** In the Supabase SQL Editor, run:
   - [`database/schema.sql`](database/schema.sql) for a new database, or
   - [`database/migration-v2.sql`](database/migration-v2.sql) to upgrade a database made with the original schema, then
   - [`database/migration-v3-payments.sql`](database/migration-v3-payments.sql) to add online payments.
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

## Payment flow

Bills are paid online through **[SSLCommerz](https://developer.sslcommerz.com/doc/v4/)**, Bangladesh's payment gateway (cards, mobile banking and more):

1. On **Pay bill**, the customer picks a bill and clicks **Pay with SSLCommerz**. The server records the payment, taking the amount from the bill (never from the app), and asks SSLCommerz for a secure checkout page, which opens in the browser.
2. After paying, SSLCommerz sends the browser back to the server. The server doesn't trust that redirect: it asks SSLCommerz directly, server to server, whether the transaction is valid, and checks that the transaction ID, amount and currency match its own record.
3. Only then is the bill marked **Paid**, automatically. The app is watching and shows the result straight away. Payments SSLCommerz flags as risky go to *Pending review* for an admin.

Cash or other payments: the customer can report them, and an admin confirms them in **Bills & payments**.

**Trying it for free (sandbox):** set the `SSLCOMMERZ_*` values in `.env` (see `.env.example`). The public test store `testbox` / `qwerty` works for learning, or [register your own sandbox store](https://developer.sslcommerz.com/registration/). On the sandbox checkout page, use test card **4111 1111 1111 1111**, expiry **12/26**, CVV **111**, OTP **111111**. No real money moves.

## Troubleshooting

- **"Can't reach the server"**: start the server first, and check `EBS_API_URL` in the app's `.env`.
- **Server says it can't connect to the database:** it prints the reason and a fix at start-up. Two common causes:
  - Using Supabase's direct address (`db.<project>.supabase.co`). It only works over IPv6, so use the **Session pooler** connection string instead.
  - Leftover `SUPABASE_DB_*` environment variables in Windows. These override `.env`; the start-up log shows where each setting came from.
- **"Missing SUPABASE_DB_URL"** (server): the server couldn't find `.env`. Run it from `release/server` or the project folder, or set the variables directly.
- **"Your session has expired"**: sessions last 8 hours and end when the server restarts. Log out and log in again.
- **"Too many failed logins"**: wait 15 minutes, or restart the server.

## Version 2 changes

This version fixes security issues, bugs and design problems found in a review of the original. Among them: open admin sign-up, account takeover through the customer sign-up, SQL injection, plain-text passwords, leaked database connections, a frozen UI, and the database password being shipped inside the app. See [docs/ISSUES-CHECKLIST.md](docs/ISSUES-CHECKLIST.md) for the full list and what's still open.
