Electricity Billing System
==========================

A Java Swing desktop app (Java 17+) backed by Supabase PostgreSQL.

Code layout (src/electricity/billing/system)
--------------------------------------------
  model/     Customer, Bill, Meter, TaxRates, UserAccount, and the Role / BillStatus / BillType enums
  db/        Database (connections and transactions) plus one DAO per table. All SQL uses PreparedStatement.
  service/   Business rules: AuthService, CustomerService, BillingService
  billing/   Tariff interface with NormalTariff and IndustrialTariff (polymorphic pricing)
  security/  PasswordHasher (salted PBKDF2)
  *.java     Swing windows. BaseFrame holds the shared look and runs database work off the UI thread.

Database setup
--------------
New Supabase project:
  Run Electricity-Billing-System/supabase-schema.sql in the Supabase SQL Editor.

Existing database made with the old schema:
  Run Electricity-Billing-System/supabase-migration-v2.sql once in the SQL Editor.
  It runs in one transaction, so if anything fails nothing is changed.

Connection settings
-------------------
Copy .env.example (in the top-level folder) to .env and fill in:

  SUPABASE_DB_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
  SUPABASE_DB_USER=postgres
  SUPABASE_DB_PASSWORD=<your Supabase database password>

Environment variables with the same names override the file. Never commit .env.

Running
-------
Open Electricity-Billing-System in NetBeans and run Main.java, or use the JAR in ../JarApplication.

First run and accounts
----------------------
* The first admin is created from the Signup screen. "Admin" is only offered there
  while no admin exists. After that, admins add other admins from the dashboard.
* An admin adds each customer (Add New Customer). The app generates a unique meter
  number. The customer then signs up with that meter number and the email the admin
  entered. Each meter can be claimed once.
* Passwords are stored as salted PBKDF2 hashes. Accounts created before this change
  are upgraded automatically the next time they log in.

Billing and payments
--------------------
* Bill = units x cost per unit (x the industrial multiplier for Industrial meters)
         + meter rent + service charge + service tax + fixed tax.
  Rates live in the single row of the tax table.
* There is one bill per meter per month per year.
* Payment flow: the customer pays on Paytm and clicks "I've Paid". The bill becomes
  "Pending Verification". An admin confirms it in Deposit Details ("Mark Selected as
  Paid"). Cash payments can be confirmed the same way.

Old MySQL data
--------------
The .ibd files in ../legacy-mysql-data are raw MySQL 8 tablespaces from the original
version. See the README in that folder.
