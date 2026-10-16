-- Adds online payments (SSLCommerz). Safe to run more than once; it only adds a table.
-- Run it in the Supabase SQL Editor after migration-v2.sql.

create table if not exists payment (
    -- our own random reference, sent to SSLCommerz as tran_id
    tran_id      varchar(40) primary key,
    meter_no     varchar(20) not null,
    month        varchar(10) not null,
    year         integer not null,
    -- the bill total when the payment started; the gateway must report exactly this
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

-- Only the backend server may read or write payments.
alter table payment enable row level security;
