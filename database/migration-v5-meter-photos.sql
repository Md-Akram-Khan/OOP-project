-- Meter photos: bills are made from a photo of the meter, read by the server.
-- Run it in the Supabase SQL Editor after migration-v4-bd-billing.sql.
-- Safe to run more than once. It only adds a column and a table; nothing is deleted.

-- The serial number printed on the physical meter (e.g. "E-756216"). The photo's
-- serial must match it. Stored as typed; compared as letters and digits only.
alter table meter_info add column if not exists serial_no varchar(30);
create unique index if not exists meter_info_serial_key
    on meter_info (upper(regexp_replace(serial_no, '[^A-Za-z0-9]', '', 'g')))
    where serial_no is not null;

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

-- Only the backend server may touch it (see schema.sql).
alter table meter_photo enable row level security;
