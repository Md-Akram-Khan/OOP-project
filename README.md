# Electricity Billing System

A Java Swing desktop app for an electricity provider, built as a second-year university OOP project. Admins manage customers, meters and monthly bills; customers view, generate and pay their bills. Data is stored in PostgreSQL (Supabase), behind a small backend API.

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
│   ├── *.java        Swing windows, all extending BaseFrame                 (app)
│   ├── client/       ApiClient + AuthClient, CustomerClient, BillingClient   (app)
│   ├── server/       ApiServer, Router, Endpoints, SessionStore, LoginThrottle (server)
│   ├── service/      AuthService, CustomerService, BillingService            (server)
│   ├── db/           Database + one DAO per table, PreparedStatement only   (server)
│   ├── security/     PasswordHasher, salted PBKDF2                           (server)
│   ├── billing/      Tariff interface ← NormalTariff, IndustrialTariff       (server)
│   ├── model/        Customer, Bill, Meter, TaxRates, UserAccount, enums     (shared)
│   └── common/       Api request/response records, Json, Config, Validation  (shared)
├── src/icon/         Images used by the windows
├── lib/              Gson (JSON) and the PostgreSQL JDBC driver
├── database/         schema.sql (new database), migration-v2.sql (upgrade)
├── scripts/          build-release.ps1
├── release/          client/ and server/, each a ready-to-run JAR with its lib/
├── docs/             ISSUES-CHECKLIST.md
└── nbproject/, build.xml, manifest.mf    NetBeans project files
```

## OOP design

- **Encapsulation:** immutable records for domain data; windows never touch SQL or HTTP directly.
- **Abstraction and layering:** windows → API clients → HTTP → endpoints → services (business rules) → DAOs (SQL) → database.
- **Inheritance:** every window extends `BaseFrame`, which holds the shared layout, styling and background-task handling.
- **Polymorphism:** `Tariff.forBillType(...)` returns the pricing strategy for a meter; adding a new tariff means adding one class.

**Bill formula:** units × cost per unit (× industrial multiplier for Industrial meters) + meter rent + service charge + service tax + fixed tax.

## Setup

**Requirements:** JDK 17+, and a free [Supabase](https://supabase.com) project.

1. **Database.** In the Supabase SQL Editor, run:
   - [`database/schema.sql`](database/schema.sql) for a new database, or
   - [`database/migration-v2.sql`](database/migration-v2.sql) to upgrade a database made with the original schema.
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

Customer clicks **Pay Now** (opens Paytm) → clicks **I've Paid** → bill becomes *Pending Verification* → admin confirms it in **Deposit Details** → *Paid*.

## Troubleshooting

- **"Can't reach the server"**: start the server first, and check `EBS_API_URL` in the app's `.env`.
- **"Missing SUPABASE_DB_URL"** (server): the server couldn't find `.env`. Run it from `release/server` or the project folder, or set the variables directly.
- **"Your session has expired"**: sessions last 8 hours and end when the server restarts. Log out and log in again.
- **"Too many failed logins"**: wait 15 minutes, or restart the server.

## Version 2 changes

This version fixes security issues, bugs and design problems found in a review of the original. Among them: open admin sign-up, account takeover through the customer sign-up, SQL injection, plain-text passwords, leaked database connections, a frozen UI, and the database password being shipped inside the app. See [docs/ISSUES-CHECKLIST.md](docs/ISSUES-CHECKLIST.md) for the full list and what's still open.
