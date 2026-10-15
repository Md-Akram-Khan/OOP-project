# Issues Checklist

Status of every issue found in the project review, plus a few more found while fixing them.

**Key:** ✅ Solved · ⚠️ Solved in code, needs an action from you · ❌ Not solved (reason given)

**Summary:** 31 solved · 2 need your action · 3 not solved (36 issues)

---

## Security

| # | Issue | Status | What changed |
|---|---|---|---|
| 1 | Anyone could create an **Admin** account from the public signup form | ✅ | Signup offers "Admin" only while no admin exists (first-time set-up). After that, only a logged-in admin can add admins (new **Add Admin** button). |
| 2 | **Account takeover**: knowing a meter number was enough to reset that customer's username and password | ✅ | Customers must give their meter number **and** the email on file. The SQL only updates a login that hasn't been claimed (`coalesce(username,'') = ''`), so an existing account can't be overwritten. |
| 3 | Signup said "Account Created Successfully" even when nothing was saved | ✅ | Checks the number of rows updated and shows a clear error. |
| 4 | **SQL injection** in about 12 places (string-built queries) | ✅ | All SQL now lives in the `db/` DAOs and uses `PreparedStatement`. Names like `O'Brien` work. |
| 5 | **Plain-text passwords** | ⚠️ | Salted PBKDF2 hashing (`security/PasswordHasher`). Your 2 existing accounts are still plain text until each one logs in once; the app re-hashes them automatically then. |
| 6 | MySQL root password written in `JarApplication/README.txt` | ⚠️ | Removed from the README, and the old zips containing it were purged from the GitHub history. **Change that password** if you use it anywhere else. |
| 7 | *(Found during fixes)* Customer signup auto-filled the customer's name when you typed any meter number, so anyone could look up names | ✅ | Removed. Customers no longer see a name until they've proved who they are. |
| 8 | *(Found during fixes)* **Every copy of the app contains the database password**, so a customer could connect to Supabase directly and bypass all the rules above | ❌ | Architectural. Fixing it needs a backend API (or Supabase Auth with row-level security) between the app and the database. That's a project-sized change, beyond a bug fix. |

## Bugs

| # | Issue | Status | What changed |
|---|---|---|---|
| 9 | Closing windows never exited the app (JVM kept running) | ✅ | Every window uses `DISPOSE_ON_CLOSE`. Closing the dashboard closes all its windows; Log out does the same. |
| 10 | A new database connection on every query, never closed | ✅ | `Database.withConnection` / `inTransaction` with try-with-resources. One connection per action, always closed. |
| 11 | Database calls froze the UI | ✅ | `BaseFrame.runAsync` runs database work on a `SwingWorker` and disables buttons meanwhile, which also stops double submits. |
| 12 | Bill marked **Paid before any payment** | ✅ | New flow: customer pays on Paytm, clicks **I've Paid**, bill becomes **Pending Verification**, admin confirms in Deposit Details (**Mark Selected as Paid**). |
| 13 | Bills had **no year** (Jan 2025 and Jan 2026 collided) | ✅ | `year` column plus Year pickers in Calculate / Pay / Generate / Deposit. |
| 14 | The same month could be billed twice; Generate Bill showed only the first | ✅ | Primary key `(meter_no, year, month)` and a friendly "already has a bill" message. |
| 15 | Random meter numbers could repeat, and nothing enforced uniqueness | ✅ | `SecureRandom` 6-digit number, existence check, primary key and retry. The number is assigned on save. |
| 16 | Customer and login inserts weren't in a transaction | ✅ | `CustomerService.create` saves both in one transaction. |
| 17 | Pay Bill kept showing the previous month's figures | ✅ | Clears immediately, shows "No bill for this month", ignores stale results. |
| 18 | Calculate Bill didn't show the first customer's name and address | ✅ | Loads them as soon as the meter list arrives. |
| 19 | "Industial" typo | ✅ | Fixed in the UI. The migration fixes existing rows, and old values are still read correctly. |
| 20 | Bill type didn't affect the price | ✅ | `Tariff` interface with `NormalTariff` and `IndustrialTariff`. Industrial = cost per unit × `industrial_multiplier`. The **1.5 default is my assumption**; change it in the `tax` table. |
| 21 | Meter type (Electric / Solar / Smart) doesn't affect the price | ❌ | Left as information only. Pricing solar or smart meters differently is a business rule I'd be inventing. Easy to add as another `Tariff` once you decide the rule. |
| 22 | `service_tax` is added as a flat amount, not a percentage | ❌ | **Your decision.** Treating the stored 57 as 57% would be wrong, so it stays a flat Rs. 57 and is labelled as such. Switching to a percentage needs a new rate value. |
| 23 | Font name "Senserif" typo | ✅ | Generate Bill now uses a monospaced font so the columns line up. |
| 24 | Notepad and Calculator buttons only worked on Windows | ✅ | Picks the right app for Windows, macOS or Linux, and shows a message if it's missing. Only tested on Windows. |
| 25 | *(Found during fixes)* Splash screen changed windows from a background thread | ✅ | Uses a Swing `Timer`. |
| 26 | *(Found during fixes)* No input validation: empty customers, bad emails and phone numbers were accepted | ✅ | `CustomerService.validate` lists every problem at once. Usernames are 3-30 characters; passwords need at least 8 plus a confirm field. |
| 27 | *(Found during fixes)* Multiple rows in `tax` would have been added together | ✅ | Migration enforces exactly one tax row. |
| 28 | *(Found during fixes)* Errors were only printed to the console, and the user saw nothing | ✅ | Every failure shows a dialog. |

## OOP design

| # | Issue | Status | What changed |
|---|---|---|---|
| 29 | No domain classes; data passed around as raw strings and `ResultSet`s | ✅ | `model/`: `Customer`, `Bill`, `Meter`, `TaxRates`, `UserAccount` (records) and the `Role`, `BillStatus`, `BillType` enums. |
| 30 | No interfaces or polymorphism of your own | ✅ | `billing/Tariff` interface with two implementations, picked at runtime by bill type. |
| 31 | SQL mixed into button handlers; weak encapsulation | ✅ | Layers: windows call `service/`, which calls `db/` DAOs. Fields are `private final`. |
| 32 | Heavy duplication (month list ×4, button styling ×15, connect-and-query ×20) | ✅ | `BaseFrame` (shared window set-up, `darkButton`, `monthChoice`, `yearChoice`, `icon`, `runAsync`) and `Tables`. |

## Project folder

| # | Issue | Status | What changed |
|---|---|---|---|
| 33 | 43 JVM crash logs (`hs_err_pid*.log`, `replay_pid*.log`) | ✅ | Removed during the folder clean-up, and `.gitignore`d. |
| 34 | The packaged JAR was out of date (MySQL driver, old code) | ✅ | Rebuilt from the new source with the PostgreSQL driver; now in `release/`. |
| 35 | `.ibd` files couldn't be restored the way the README said, and were packed into the JAR | ✅ | Removed from the source tree and the JAR, then deleted at your request. The old data was **not** recovered. |
| 36 | README referenced a missing `.env.example` | ✅ | Added `.env.example`; both READMEs rewritten. |

---

## Database migration: applied ✅

- File: `database/migration-v2.sql`
- First dry-run against your real data inside a transaction and rolled back (17 / 17 checks passed), then **applied with your go-ahead on 2026-10-16**.
- Afterwards: all 14 keys and constraints are present, and every existing row was kept (login 2, customer 1, meter_info 1, tax 1, bill 0).
- For a brand-new Supabase project, run `database/schema.sql` instead.

## Testing

| Test | Result |
|---|---|
| Compiles with `javac --release 17 -Xlint:all` | ✅ No errors; only harmless serialization warnings |
| Logic tests: tariffs, password hashing, validation, months | ✅ 16 / 16 |
| Database dry run: migration + every DAO, rolled back | ✅ 17 / 17 |
| Rebuilt JAR, read-only run against the live migrated database | ✅ Admin, customers, meters, tax rates and bills all load |
| Clicking through every window by hand | ❌ Not done: I can't operate the GUI. Please test login, signup, add customer, calculate, pay and confirm. |

## Project layout

The folder was reorganised after these fixes (NetBeans project at the top level, SQL in `database/`, runnable JAR in `release/`). See the README for the full layout.
