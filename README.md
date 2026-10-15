# Electricity Billing System

A Java Swing desktop app for an electricity provider, built as a second-year university OOP project. Admins manage customers, meters and monthly bills; customers view, generate and pay their bills. Data is stored in PostgreSQL (Supabase).

## Features

| Admin | Customer |
|---|---|
| Add customers (unique meter number generated automatically) | Sign up with meter number + registered email |
| Set up each meter's type and tariff | View and update their profile |
| Calculate monthly bills | View bill history |
| Search all bills and confirm payments | Generate a printable bill for any month |
| Add other admins | Pay online, then report the payment for confirmation |

## OOP design

```
src/electricity/billing/system/
├── model/      Customer, Bill, Meter, TaxRates, UserAccount (records)
│               Role, BillStatus, BillType (enums)
├── billing/    Tariff (interface) ← NormalTariff, IndustrialTariff
├── db/         Database + one DAO per table (PreparedStatement only)
├── service/    AuthService, CustomerService, BillingService
├── security/   PasswordHasher (salted PBKDF2)
└── *.java      Swing windows, all extending BaseFrame
```

- **Encapsulation:** immutable records for domain data; windows never touch SQL.
- **Abstraction and layering:** UI → services (business rules) → DAOs (SQL) → database.
- **Inheritance:** every window extends `BaseFrame`, which holds the shared layout, styling and background-task handling.
- **Polymorphism:** `Tariff.forBillType(...)` returns the pricing strategy for a meter; adding a new tariff means adding one class.

**Bill formula:** units × cost per unit (× industrial multiplier for Industrial meters) + meter rent + service charge + service tax + fixed tax.

## Setup

**Requirements:** JDK 17+, a free [Supabase](https://supabase.com) project, and NetBeans (optional).

1. **Database.** In the Supabase SQL Editor, run:
   - [`supabase-schema.sql`](FullProject/Electricity-Billing-System/supabase-schema.sql) for a new database, or
   - [`supabase-migration-v2.sql`](FullProject/Electricity-Billing-System/supabase-migration-v2.sql) to upgrade a database made with the original schema.
2. **Connection.** Copy [`.env.example`](.env.example) to `.env` in the repo root and fill in your Supabase JDBC URL, user and password. Never commit `.env`.
3. **Run** either way:
   - NetBeans: open `FullProject/Electricity-Billing-System` and run `Main.java`.
   - JAR: `cd JarApplication`, then `java -jar Electricity_Billing_System.jar`.
4. **First login.** On the Signup screen, create the first admin ("Admin" is only offered while no admin exists). Add customers from the admin dashboard and give each one their meter number so they can sign up.

## Payment flow

Customer clicks **Pay Now** (opens Paytm) → clicks **I've Paid** → bill becomes *Pending Verification* → admin confirms it in **Deposit Details** → *Paid*.

## Version 2 changes

This version fixes security issues, bugs and design problems found in a review of the original. Among them: open admin sign-up, account takeover through the customer sign-up, SQL injection, plain-text passwords, leaked database connections and a frozen UI. See [ISSUES-CHECKLIST.md](ISSUES-CHECKLIST.md) for the full list and what's still open.

**Known limitation:** as a desktop app talking straight to the database, every copy of the app needs the database credentials. A production version would put an API server between the app and the database.
