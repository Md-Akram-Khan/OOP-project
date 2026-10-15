-- Upgrades a database created with the ORIGINAL supabase-schema.sql to the v2 schema.
-- Run it once in the Supabase SQL Editor. It runs in a single transaction:
-- if any step fails, nothing is changed.
--
-- What it does:
--   * adds primary keys, foreign keys and uniqueness rules (no more duplicate
--     meters, usernames or bills)
--   * stores numbers as numbers instead of text
--   * adds a year to bills, so January 2025 and January 2026 no longer collide
--   * fixes the "Industial" typo and adds the industrial rate multiplier
--   * adds the "Pending Verification" bill status
-- Existing plain-text passwords are re-hashed automatically by the app the
-- next time each user logs in.

begin;

-- customer: one row per meter -------------------------------------------------
alter table customer alter column meter_no set not null;
alter table customer add constraint customer_pkey primary key (meter_no);
alter table customer
    alter column name    type varchar(50),
    alter column address type varchar(100),
    alter column email   type varchar(100);

-- login -----------------------------------------------------------------------
update login set meter_no = null where meter_no = '';
update login set "user" = 'Customer' where coalesce("user", '') = '' and meter_no is not null;
update login set username = '' where username is null;
update login set password = '' where password is null;
alter table login add column id bigint generated always as identity primary key;
alter table login
    alter column "user" set not null,
    alter column username set not null,
    alter column username set default '',
    alter column password set not null,
    alter column password set default '',
    alter column name type varchar(50);
alter table login add constraint login_role_check check ("user" in ('Admin', 'Customer'));
alter table login add constraint login_customer_fk
    foreign key (meter_no) references customer (meter_no) on delete cascade;
create unique index login_username_key on login (lower(username)) where username <> '';
create unique index login_customer_meter_key on login (meter_no) where meter_no is not null;

-- meter_info ------------------------------------------------------------------
update meter_info set bill_type = 'Industrial' where bill_type = 'Industial';
alter table meter_info alter column meter_no set not null;
alter table meter_info add constraint meter_info_pkey primary key (meter_no);
alter table meter_info add constraint meter_info_customer_fk
    foreign key (meter_no) references customer (meter_no) on delete cascade;
alter table meter_info alter column days type integer using days::integer;
alter table meter_info
    alter column meter_type set not null,
    alter column bill_type set not null,
    alter column days set not null,
    alter column days set default 30;
alter table meter_info add constraint meter_info_bill_type_check check (bill_type in ('Normal', 'Industrial'));

-- tax: exactly one row of numeric rates ---------------------------------------
alter table tax add column id integer not null default 1;
alter table tax add constraint tax_pkey primary key (id);
alter table tax add constraint tax_single_row check (id = 1);
alter table tax
    alter column cost_per_unit  type numeric(10, 2) using cost_per_unit::numeric,
    alter column meter_rent     type numeric(10, 2) using meter_rent::numeric,
    alter column service_charge type numeric(10, 2) using service_charge::numeric,
    alter column service_tax    type numeric(10, 2) using service_tax::numeric,
    alter column fixed_tax      type numeric(10, 2) using fixed_tax::numeric;
alter table tax
    alter column cost_per_unit set not null,
    alter column meter_rent set not null,
    alter column service_charge set not null,
    alter column service_tax set not null,
    alter column fixed_tax set not null;
alter table tax add column industrial_multiplier numeric(4, 2) not null default 1.50;

-- bill: one bill per meter per month per year ---------------------------------
alter table bill add column year integer;
update bill set year = extract(year from current_date)::integer where year is null;
alter table bill
    alter column meter_no set not null,
    alter column month set not null,
    alter column month type varchar(10),
    alter column year set not null,
    alter column units type integer using units::integer,
    alter column units set not null,
    alter column totalbill type numeric(12, 2) using totalbill::numeric,
    alter column totalbill set not null,
    alter column status set not null,
    alter column status set default 'Not Paid';
alter table bill add constraint bill_pkey primary key (meter_no, year, month);
alter table bill add constraint bill_customer_fk
    foreign key (meter_no) references customer (meter_no) on delete cascade;
alter table bill add constraint bill_month_check check (month in ('January', 'February', 'March', 'April',
    'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'));
alter table bill add constraint bill_units_check check (units >= 0);
alter table bill add constraint bill_status_check check (status in ('Not Paid', 'Pending Verification', 'Paid'));

commit;
