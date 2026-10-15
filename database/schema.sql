-- Electricity Billing System: schema for a NEW, empty Supabase database.
-- Already have the old tables? Run supabase-migration-v2.sql instead.

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
    days       integer not null default 30
);

-- Exactly one row of rates.
create table if not exists tax (
    id                    integer primary key default 1 check (id = 1),
    cost_per_unit         numeric(10, 2) not null,
    meter_rent            numeric(10, 2) not null,
    service_charge        numeric(10, 2) not null,
    service_tax           numeric(10, 2) not null,
    fixed_tax             numeric(10, 2) not null,
    -- industrial meters pay cost_per_unit x this
    industrial_multiplier numeric(4, 2)  not null default 1.50
);

insert into tax (id, cost_per_unit, meter_rent, service_charge, service_tax, fixed_tax)
values (1, 9, 47, 22, 57, 18)
on conflict (id) do nothing;

create table if not exists bill (
    meter_no  varchar(20) not null references customer (meter_no) on delete cascade,
    month     varchar(10) not null check (month in ('January', 'February', 'March', 'April', 'May', 'June',
                                                   'July', 'August', 'September', 'October', 'November', 'December')),
    year      integer not null,
    units     integer not null check (units >= 0),
    totalbill numeric(12, 2) not null,
    status    varchar(20) not null default 'Not Paid'
              check (status in ('Not Paid', 'Pending Verification', 'Paid')),
    primary key (meter_no, year, month)
);
