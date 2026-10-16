-- Electricity Billing System: schema for a NEW, empty Supabase database.
-- Already have the old tables? Run the migration-v*.sql files in order instead.

create table if not exists customer (
    meter_no varchar(20) primary key,
    name     varchar(50)  not null,
    address  varchar(100) not null,
    city     varchar(30)  not null,
    state    varchar(30)  not null,
    email    varchar(100) not null,
    phone    varchar(20)  not null
);

create table if not exists login (
    id       bigint generated always as identity primary key,
    -- null for admins; for customers, the meter their account belongs to
    meter_no varchar(20) references customer (meter_no) on delete cascade,
    -- '' until the customer signs up
    username varchar(30) not null default '',
    name     varchar(50),
    -- PBKDF2 hash written by the app, '' until the customer signs up
    password varchar(255) not null default '',
    "user"   varchar(20) not null check ("user" in ('Admin', 'Customer'))
);
create unique index if not exists login_username_key on login (lower(username)) where username <> '';
create unique index if not exists login_customer_meter_key on login (meter_no) where meter_no is not null;

create table if not exists meter_info (
    meter_no   varchar(20) primary key references customer (meter_no) on delete cascade,
    meter_type varchar(20) not null,
    bill_type  varchar(20) not null check (bill_type in ('Normal', 'Industrial')),
    days       integer not null default 30,
    -- approved load; the monthly demand charge is this many kW x the tariff's rate
    sanctioned_load_kw numeric(7, 2) not null default 2.00
);

-- ---- Tariffs ----------------------------------------------------------------
-- One plan per customer category and start date. A bill uses the newest plan that
-- had started by its billing month. To bring in a new BERC order, add a plan with
-- the new effective_from date; old bills keep the plan they were priced with.
create table if not exists tariff_plan (
    id                 bigint generated always as identity primary key,
    bill_type          varchar(20) not null check (bill_type in ('Normal', 'Industrial')),
    name               varchar(80) not null,
    effective_from     date not null,
    -- homes using at most lifeline_max_units in a month pay lifeline_rate for every unit
    lifeline_max_units integer check (lifeline_max_units > 0),
    lifeline_rate      numeric(8, 2),
    demand_rate_per_kw numeric(8, 2) not null check (demand_rate_per_kw >= 0),
    vat_percent        numeric(5, 2) not null check (vat_percent >= 0),
    late_fee_percent   numeric(5, 2) not null check (late_fee_percent >= 0),
    meter_rent         numeric(8, 2) not null check (meter_rent >= 0),
    due_days           integer not null default 15 check (due_days > 0),
    source             varchar(300),
    unique (bill_type, effective_from)
);

-- The bands of a plan, in order. upto_units is inclusive; the last band has none.
create table if not exists tariff_slab (
    plan_id    bigint not null references tariff_plan (id) on delete cascade,
    seq        integer not null,
    upto_units integer check (upto_units > 0),
    rate       numeric(8, 2) not null check (rate >= 0),
    primary key (plan_id, seq)
);

-- Residential (LT-A), BERC order of June 2026.
insert into tariff_plan (bill_type, name, effective_from, lifeline_max_units, lifeline_rate, demand_rate_per_kw,
                         vat_percent, late_fee_percent, meter_rent, due_days, source)
values ('Normal', 'BERC residential LT-A, June 2026', date '2026-06-01', 50, 4.63, 42, 5, 5, 10, 15,
        'Slab rates: BERC order of 3 June 2026 (Daily Star, Financial Express). Demand charge Tk 42/kW and the '
        || '5% late fee reproduce a real Sept 2026 Magura Palli Bidyut bill to the taka. Check against the BERC order.')
on conflict (bill_type, effective_from) do nothing;

insert into tariff_slab (plan_id, seq, upto_units, rate)
select p.id, s.seq, s.upto, s.rate
from tariff_plan p
cross join (values (1, 75, 5.26), (2, 200, 8.50), (3, 300, 9.10), (4, 400, 9.62), (5, 600, 15.01),
                   (6, null::integer, 17.35)) as s (seq, upto, rate)
where p.bill_type = 'Normal' and p.effective_from = date '2026-06-01'
on conflict (plan_id, seq) do nothing;

-- Commercial & industrial: one flat rate.
insert into tariff_plan (bill_type, name, effective_from, lifeline_max_units, lifeline_rate, demand_rate_per_kw,
                         vat_percent, late_fee_percent, meter_rent, due_days, source)
values ('Industrial', 'Commercial & industrial flat rate, June 2026', date '2026-06-01', null, null, 42, 5, 5, 10, 15,
        'Tk 10.55/unit is the June 2026 medium-voltage flat rate (Daily Observer). The demand charge and meter rent '
        || 'are copied from the residential plan as placeholders: check the BERC order for your category.')
on conflict (bill_type, effective_from) do nothing;

insert into tariff_slab (plan_id, seq, upto_units, rate)
select p.id, 1, null, 10.55
from tariff_plan p
where p.bill_type = 'Industrial' and p.effective_from = date '2026-06-01'
on conflict (plan_id, seq) do nothing;

create table if not exists bill (
    meter_no  varchar(20) not null references customer (meter_no) on delete cascade,
    month     varchar(10) not null check (month in ('January', 'February', 'March', 'April', 'May', 'June',
                                                   'July', 'August', 'September', 'October', 'November', 'December')),
    year      integer not null,
    units     integer not null check (units >= 0),
    totalbill numeric(12, 2) not null,
    status    varchar(20) not null default 'Not Paid'
              check (status in ('Not Paid', 'Pending Verification', 'Paid')),
    previous_reading integer,
    current_reading  integer,
    tariff_plan_id   bigint references tariff_plan (id),
    energy_charge    numeric(12, 2),
    load_kw          numeric(7, 2),
    demand_charge    numeric(12, 2),
    vat              numeric(12, 2),
    meter_rent       numeric(12, 2),
    late_fee         numeric(12, 2),
    total_after_due  numeric(12, 2),
    due_date         date,
    issued_on        date,
    -- what the bill checker flagged when the bill was made, one warning per line
    warnings         text,
    primary key (meter_no, year, month)
);

create table if not exists payment (
    tran_id      varchar(40) primary key,
    meter_no     varchar(20) not null,
    month        varchar(10) not null,
    year         integer not null,
    amount       numeric(12, 2) not null,
    currency     varchar(3) not null default 'BDT',
    status       varchar(20) not null default 'INITIATED'
                 check (status in ('INITIATED', 'PAID', 'FAILED', 'CANCELLED')),
    session_key  varchar(64),
    val_id       varchar(64),
    bank_tran_id varchar(80),
    card_type    varchar(60),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    foreign key (meter_no, year, month) references bill (meter_no, year, month) on delete cascade
);
create index if not exists payment_bill_idx on payment (meter_no, year, month);

-- ---- Complaints and service requests ----------------------------------------
create table if not exists complaint (
    id          bigint generated always as identity primary key,
    meter_no    varchar(20) not null references customer (meter_no) on delete cascade,
    category    varchar(20) not null check (category in ('BILLING', 'METER', 'OUTAGE', 'CONNECTION', 'OTHER')),
    subject     varchar(120) not null,
    details     varchar(2000) not null,
    status      varchar(20) not null default 'OPEN' check (status in ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    admin_reply varchar(2000),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index if not exists complaint_meter_idx on complaint (meter_no, created_at desc);

-- ---- Notifications for customers --------------------------------------------
create table if not exists notification (
    id         bigint generated always as identity primary key,
    meter_no   varchar(20) not null references customer (meter_no) on delete cascade,
    kind       varchar(20) not null check (kind in ('BILL', 'PAYMENT', 'COMPLAINT', 'USAGE')),
    title      varchar(120) not null,
    body       varchar(500) not null,
    created_at timestamptz not null default now(),
    read_at    timestamptz
);
create index if not exists notification_meter_idx on notification (meter_no, created_at desc);

-- ---- Audit log: who changed what --------------------------------------------
create table if not exists audit_log (
    id      bigint generated always as identity primary key,
    at      timestamptz not null default now(),
    actor   varchar(60) not null,
    action  varchar(40) not null,
    target  varchar(80),
    details varchar(500)
);
create index if not exists audit_log_at_idx on audit_log (at desc);

-- Only the backend server may touch these tables. Turning on row-level
-- security with no policies blocks Supabase's public Data API (the anon and
-- authenticated roles); the server's postgres role bypasses RLS.
alter table customer   enable row level security;
alter table login      enable row level security;
alter table meter_info enable row level security;
alter table bill       enable row level security;
alter table payment    enable row level security;
alter table tariff_plan  enable row level security;
alter table tariff_slab  enable row level security;
alter table complaint    enable row level security;
alter table notification enable row level security;
alter table audit_log    enable row level security;
