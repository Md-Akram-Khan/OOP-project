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
    "user"   varchar(20) not null,
    -- applicants (phone and NID verified, no meter yet) sign up with these
    phone    varchar(20),
    nid_no   varchar(17),
    constraint login_role_check check ("user" in ('Admin', 'Customer', 'Applicant'))
);
create unique index if not exists login_nid_key on login (nid_no) where nid_no is not null;
create unique index if not exists login_username_key on login (lower(username)) where username <> '';
create unique index if not exists login_customer_meter_key on login (meter_no) where meter_no is not null;

create table if not exists meter_info (
    meter_no   varchar(20) primary key references customer (meter_no) on delete cascade,
    meter_type varchar(20) not null,
    bill_type  varchar(20) not null check (bill_type in ('Normal', 'Industrial')),
    days       integer not null default 30,
    -- approved load; the monthly demand charge is this many kW x the tariff's rate
    sanctioned_load_kw numeric(7, 2) not null default 2.00,
    -- serial printed on the physical meter (e.g. E-756216); meter photos must show it
    serial_no varchar(30)
);
create unique index if not exists meter_info_serial_key
    on meter_info (upper(regexp_replace(serial_no, '[^A-Za-z0-9]', '', 'g')))
    where serial_no is not null;

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
-- The photo each bill was read from, kept as evidence. The same photo can't be
-- used for two bills (sha256 is unique).
create table if not exists meter_photo (
    meter_no     varchar(20) not null,
    year         integer not null,
    month        varchar(10) not null,
    image        bytea not null,
    content_type varchar(30) not null,
    sha256       char(64) not null unique,
    display_text varchar(20) not null,
    serial_seen  varchar(40),
    uploaded_by  varchar(60) not null,
    uploaded_at  timestamptz not null default now(),
    primary key (meter_no, year, month),
    foreign key (meter_no, year, month) references bill (meter_no, year, month) on delete cascade
);

-- ---- Dummy NID authority database -----------------------------------------
-- A stand-in for the Election Commission's NID service, which needs authorised
-- access. These are manually stored sample records for demonstration only.
create table if not exists nid_registry (
    nid_no         varchar(17) primary key,
    name_en        varchar(60) not null,
    name_bn        varchar(60) not null,
    father_bn      varchar(80),
    mother_bn      varchar(80),
    date_of_birth  date not null,
    address_bn     varchar(300),
    birth_place_bn varchar(60),
    blood_group    varchar(4),
    issue_date     date
);

insert into nid_registry (nid_no, name_en, name_bn, father_bn, mother_bn, date_of_birth, address_bn, birth_place_bn,
                          blood_group, issue_date)
values ('3767242658', 'Tajul Islam', 'তাজুল ইসলাম', 'মোঃ আবুল হোসেন ভূইয়া', 'মোসাঃ আয়েশা বেগম', date '1990-12-01',
        'বাসা/হোল্ডিং: #১০৯, গ্রাম/রাস্তা: পালপাড়া, পালপাড়া, ডাকঘর: চাঁদপুর, চাঁদপুর সদর-৩৬৪০', 'চাঁদপুর সদর', 'B+',
        date '2010-10-21'),
       -- Fictional sample people, so the check has more than one record to choose from.
       ('5823904417', 'Nasrin Sultana', 'নাসরিন সুলতানা', 'মোঃ আব্দুল করিম', 'মোসাঃ রহিমা খাতুন', date '1995-01-30',
        'বাসা: ১২, রোড: ৫, ধানমন্ডি, ঢাকা-১২০৫', 'ঢাকা', 'O+', date '2013-06-10'),
       ('2914406683', 'Tanvir Ahmed', 'তানভীর আহমেদ', 'মোঃ নজরুল ইসলাম', 'মোসাঃ সালমা বেগম', date '2000-11-05',
        'গ্রাম: কাশিপুর, ডাকঘর: বরিশাল সদর-৮২০০', 'বরিশাল', 'A+', date '2018-02-14'),
       ('1967242658', 'Kamal Hossain', 'কামাল হোসেন', 'মোঃ জয়নাল আবেদীন', 'মোসাঃ ফাতেমা বেগম', date '1975-07-02',
        'বাসা: ৪৫, আগ্রাবাদ, চট্টগ্রাম-৪১০০', 'চট্টগ্রাম', 'AB+', date '2008-09-01')
on conflict (nid_no) do nothing;

-- ---- Applications for a new meter -------------------------------------------
create table if not exists meter_application (
    id             bigint generated always as identity primary key,
    login_id       bigint not null references login (id) on delete cascade,
    applicant_name varchar(50) not null,
    nid_no         varchar(17) not null,
    phone          varchar(20) not null,
    email          varchar(100) not null,
    house          varchar(60) not null,
    road           varchar(80) not null,
    area           varchar(60) not null,
    post_office    varchar(60) not null,
    district       varchar(30) not null,
    division       varchar(30) not null,
    bill_type      varchar(20) not null check (bill_type in ('Normal', 'Industrial')),
    load_kw        numeric(7, 2) not null check (load_kw > 0),
    phase          varchar(10) not null check (phase in ('SINGLE', 'THREE')),
    ownership      varchar(10) not null check (ownership in ('OWNER', 'TENANT')),
    notes          varchar(500),
    status         varchar(20) not null default 'SUBMITTED'
                   check (status in ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'CONNECTED')),
    admin_note     varchar(500),
    meter_no       varchar(20) references customer (meter_no) on delete set null,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);
create index if not exists meter_application_login_idx on meter_application (login_id, created_at desc);

-- The full application form (migration v7): consumer type, parents or organization, mailing and
-- connection addresses, NOCS, connection type, tariff category, meters and existing load, declaration.
alter table meter_application add column if not exists consumer_type      varchar(12) not null default 'PERSONAL';
alter table meter_application add column if not exists date_of_birth      date;
alter table meter_application add column if not exists father_name        varchar(80);
alter table meter_application add column if not exists mother_name        varchar(80);
alter table meter_application add column if not exists spouse_name        varchar(80);
alter table meter_application add column if not exists gender             varchar(10);
alter table meter_application add column if not exists organization_name  varchar(50);

alter table meter_application add column if not exists m_house            varchar(60);
alter table meter_application add column if not exists m_road             varchar(80);
alter table meter_application add column if not exists m_section          varchar(40);
alter table meter_application add column if not exists m_block            varchar(40);
alter table meter_application add column if not exists m_thana            varchar(40);
alter table meter_application add column if not exists m_district         varchar(30);
alter table meter_application add column if not exists m_division         varchar(30);
alter table meter_application add column if not exists m_post_code        varchar(4);
alter table meter_application add column if not exists m_email            varchar(100);
alter table meter_application add column if not exists m_telephone        varchar(11);
alter table meter_application add column if not exists m_mobile           varchar(11);

alter table meter_application add column if not exists section            varchar(40);
alter table meter_application add column if not exists block              varchar(40);
alter table meter_application add column if not exists thana              varchar(40);
alter table meter_application add column if not exists post_code          varchar(4);
alter table meter_application add column if not exists telephone          varchar(11);
alter table meter_application add column if not exists mobile             varchar(11);
alter table meter_application add column if not exists nocs               varchar(60);

alter table meter_application add column if not exists connection_type    varchar(10) not null default 'PERMANENT';
alter table meter_application add column if not exists tariff_category    varchar(6);
alter table meter_application add column if not exists meter_count        integer not null default 1;
alter table meter_application add column if not exists different_owners   boolean;
alter table meter_application add column if not exists existing_meters    integer;
alter table meter_application add column if not exists existing_load_kw   numeric(9, 2);
alter table meter_application add column if not exists existing_customer_no varchar(20);
alter table meter_application add column if not exists declared_at        timestamptz;

-- Road and post office are no longer required (DPDC asks for road optionally, and a post code instead).
alter table meter_application alter column road        drop not null;
alter table meter_application alter column post_office drop not null;

alter table meter_application drop constraint if exists meter_application_consumer_type_check;
alter table meter_application add constraint meter_application_consumer_type_check
    check (consumer_type in ('PERSONAL', 'ORGANIZATION'));
alter table meter_application drop constraint if exists meter_application_gender_check;
alter table meter_application add constraint meter_application_gender_check
    check (gender is null or gender in ('MALE', 'FEMALE', 'THIRD'));
alter table meter_application drop constraint if exists meter_application_connection_type_check;
alter table meter_application add constraint meter_application_connection_type_check
    check (connection_type in ('PERMANENT', 'TEMPORARY'));
alter table meter_application drop constraint if exists meter_application_meter_count_check;
alter table meter_application add constraint meter_application_meter_count_check
    check (meter_count between 1 and 999);
alter table meter_application drop constraint if exists meter_application_existing_check;
alter table meter_application add constraint meter_application_existing_check
    check ((existing_meters is null or existing_meters between 0 and 999)
       and (existing_load_kw is null or existing_load_kw >= 0));

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
alter table meter_photo  enable row level security;
alter table nid_registry      enable row level security;
alter table meter_application enable row level security;
