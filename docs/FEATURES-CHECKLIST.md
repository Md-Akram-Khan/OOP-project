# Bangladesh Features Checklist

Features from the three research PDFs, and whether they are in the app.

**Key:** ✅ Integrated · 🟡 Partly integrated · ❌ Not integrated (reason given)

**Summary:** 17 integrated · 2 partly integrated · 11 not integrated (including your two proposed features: meter photo readings and new meter sign-up)

The feature codes (C1–C10, A1–A10, L1–L8) are the ones used in *Electricity Billing App Research & Feature Blueprint*.

---

## Integrated ✅

| # | Feature | Code | What the app does now | Why it matters in Bangladesh |
|---|---|---|---|---|
| 1 | **BERC slab tariff engine** | C4 | Bills are priced band by band with the June 2026 BERC residential (LT-A) rates: Tk 5.26 for 0–75 units, Tk 8.50 for 76–200, and so on up to Tk 17.35 above 600. Rates are stored in the database (`tariff_plan` / `tariff_slab`) with an effective date. Each bill keeps the plan it was priced with. | Every Bangladeshi household bill uses this telescopic (slab) tariff. The old flat Tk 9 rate overcharged a 50-unit household by about 74%. |
| 2 | **Lifeline rate** | C4 | A household that uses 50 units or fewer in a month pays Tk 4.63 for every unit. | Protects the poorest households. BERC kept this rate when it raised the others in June 2026. |
| 3 | **Demand charge, 5% VAT, meter rent** | C4 | Demand charge = sanctioned load (kW) × Tk 42. VAT is 5% of energy plus demand charge. Meter rent is Tk 10. The total is rounded up to whole taka. | This is how a real bill is built. It reproduces the September 2026 Magura Palli Bidyut bill to the taka: 235 units → **Tk 1,963**. |
| 4 | **Customer categories** | C4 | *Residential* (slab tariff) and *Commercial & industrial* (flat Tk 10.55 per unit). Each customer has a sanctioned load in kW. | Commercial and industrial connections are not billed on household slabs. |
| 5 | **Meter readings** | C3 | The admin enters the current reading. The previous reading is filled in from the last bill, and units = current − previous. A reading lower than the previous one is refused. | About 80% of meters (mostly rural BREB connections) are read by hand once a month. |
| 6 | **Due date and late fee** | C5 | Each bill has a due date (15 days) and two totals: by the due date and after it, which adds a 5% surcharge (for Magura, Tk 1,963 and **Tk 2,056**). After the due date the bill shows *Overdue*, and online payment charges the higher amount. | Every Bangladeshi bill prints these two amounts. |
| 7 | **Bill checker** | A5 | Before saving a bill it checks for: zero units, usage 3× the meter's average or far below it, a home load over 15 kW, a demand charge larger than the energy charge, a bill 10× the last one, and a previous reading that doesn't match the last bill. The admin must confirm before a flagged bill is saved. | The Magura bill first came out at Tk 506.8 crore because a clerk typed the meter seal number as the load. The checker flags exactly that case (tested). |
| 8 | **"Why is this bill different?"** | A6 | The invoice page compares a bill with the previous one. The change is split into a usage effect and a price effect (in taka, adding up exactly). It also notes a move into a dearer slab or the loss of the lifeline rate. | "Why is my bill so high?" is the most common question customers ask. |
| 9 | **Slab alerts and forecast** | A3 | The *Usage planner* takes today's meter reading and projects the month. It shows the bill, which slab the month will land in, how many units are left before the next slab, and a daily budget to stay below it. The home page warns when the last bill reached a dear slab. | Above 400 units the rate jumps from Tk 9.62 to Tk 15.01, about 56% more per unit. |
| 10 | **Appliance calculator** | A9 | The customer enters fans, lights, fridge, AC, pump and so on, with quantity and hours per day. It shows monthly units, the bill, the slab, and which appliances cost the most. | This gives most of what NILM promises, without any sensors. |
| 11 | **Complaints and service requests** | C8 | Customers open complaints by category (wrong bill, meter problem, outage, connection, other) and get a ticket number (CMP-00001). Admins reply and set *Open*, *In progress* or *Resolved*; resolving requires a reply. The page lists the hotlines: DPDC 16116, DESCO 16120, BPDB 16200. | Customers can track a complaint instead of phoning the office. |
| 12 | **Notification centre** | C7 | Customers are notified of new bills, payments received, complaint replies and high usage. Reminders appear 5 days before the due date and once a bill is overdue. The sidebar shows an unread count. | Matches the alerts DESCO's app already sends; reminders help customers avoid the late fee. |
| 13 | **Admin reports** | C9 | Shows billed vs collected this year, the collection rate, arrears by age (1–30, 31–60, 61–90, 90+ days), collection rate per month, and the top defaulters. Bills can be exported to CSV for Excel, with protection against spreadsheet formula injection. | Arrears aging is how distributors chase unpaid bills. |
| 14 | **Audit log** | C10 | Every change is recorded with who made it and when: customer, meter, bill, payment (admin or SSLCommerz), complaint, and admin sign-ins. Entries are read-only. | People need to be able to trust a system that handles money. |
| 15 | **Tariff page** | C4 | Admins can see every plan's slabs, demand charge, VAT, late fee, meter rent and source. | BERC changes rates every year or two, so they must be easy to check. |
| 16 | **Meter photo readings** (proposed by you) | — | The admin uploads a photo of the meter. The server reads the LCD (seven-segment digits), or the number wheels of an older electromechanical meter, and checks the meter's serial number in the photo against the one on file, offline in plain Java. Readings can't be typed. The photo is stored with the bill; the same photo can't be reused; the previous reading always comes from the last bill. | Stops arbitrary or wrong readings being keyed in, the cause of cases like the Magura bill. Customers can see the photo behind their bill. |
| 17 | **New meter sign-up and applications** (proposed by you) | — | "I need a new meter" sign-up: phone OTP (demo mode shows the code; real SMS via a gateway URL in .env), NID card checked against a dummy NID database from photos of both sides (the front's number, name and date of birth must match one record; one account per NID), then an Applicant account that applies for a meter. Admins review, approve/reject with a note, and connect the installed meter, which creates the customer and converts the login. | Most Bangladeshi households still apply for connections in person. Verified online applications save trips, and the NID check mirrors the documents utilities ask for. The NID check is simulated: it can't prove a card is genuine. |

## Partly integrated 🟡

| # | Feature | Code | What exists | What's missing and why |
|---|---|---|---|---|
| 16 | **Mobile wallet payments (bKash, Nagad, Rocket)** | L2 | Already supported through SSLCommerz, which offers bKash, Nagad, Rocket and cards on its checkout page. | No direct bKash or Nagad API. That needs a merchant agreement with each wallet, and SSLCommerz already covers it. |
| 17 | **Printable invoice** | C5 | The invoice now prints like a Bangladeshi bill: readings, each slab, demand charge, VAT, meter rent, both totals and the tariff name. | No QR code or PDF file. Java would need an extra library, and *Print → Save as PDF* already works. |

## Not integrated ❌

| # | Feature | Code | Why not |
|---|---|---|---|
| 18 | **Bangla language** | L6 | It needs every screen translated (about 400 texts) and a Bengali font, because Inter has no Bengali letters. This is a large job of its own and should be the next step. |
| 19 | **Prepaid meters and recharge tokens** | L1, L3, L4 | Only distributors can create real 20-digit STS tokens, because they hold the vending keys. A simulated version is possible but would not be real. |
| 20 | **Several meters on one login** | C1 | Needs the database changed from one login per meter to many. Postponed. |
| 21 | **Multiple distributors (BPDB, DESCO, DPDC, BREB, NESCO, WZPDCL)** | L5 | The app models one utility. Tariffs are already per category and date, so a distributor column could be added later. |
| 22 | **SMS and push notifications** | C7 | SMS needs a paid local gateway. Notifications are shown inside the app instead. |
| 23 | **Smart meters (IoT, MQTT, DLMS)** | — | Needs real hardware and a distributor's permission, and about 80% of meters aren't connected at all. |
| 24 | **AI electricity-theft detection** | — | Needs data from millions of meters and confirmed theft cases. The rule-based bill checker covers the realistic part. |
| 25 | **NILM (appliance detection from meter data)** | — | Needs high-frequency sensor data. The appliance calculator gives the same insight from what the customer types in. |
| 26 | **Time-of-day rates and net metering (rooftop solar)** | — | Needs peak and off-peak readings, or import and export registers, from special meters. |
| 27 | **Blockchain or peer-to-peer energy trading** | — | Research topics, not in use in Bangladesh. |
| 28 | **Kafka, Kubernetes, microservices** | — | The blueprint itself recommends a "modular monolith" to start with, which is what this project is. |

---

## Data and caveats

- **Rates:** The BERC June 2026 figures are taken from the research PDF, which cites the Daily Star and the Financial Express. Check them against the official BERC order before real use.
- **Tk 42/kW demand charge and 5% late fee:** Inferred from the Magura bill. They reproduce it exactly (Tk 1,963 / Tk 2,056).
- **Commercial plan:** The flat Tk 10.55 is the medium-voltage rate. Its demand charge and meter rent are placeholders copied from the residential plan.
- **Where rates live:** All rates are in the `tariff_plan` and `tariff_slab` tables. To bring in a new BERC order, add a plan with a new `effective_from` date.

## Testing

| Test | Result |
|---|---|
| Tariff engine, bill checker, explainer and slab guard (unit tests, including the Magura golden case) | ✅ 37 / 37 |
| v4 features end to end: real server and client against a temporary database schema. Covers readings, the Magura bill, checker, overdue, explainer, forecast, complaints, notifications, audit and access rules | ✅ 39 / 39 |
| Earlier API suite, updated for meter readings | ✅ all passed |
| SSLCommerz payment suite (real sandbox plus tampering checks) | ✅ 25 / 25 |
