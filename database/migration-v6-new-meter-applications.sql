-- New meter applications: phone (OTP) and NID verified sign-up, and applications for a new meter.
-- Run it in the Supabase SQL Editor after migration-v5-meter-photos.sql.
-- Safe to run more than once. It only adds tables, columns and sample rows; nothing is deleted.

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

-- ---- Applicant accounts -----------------------------------------------------
-- People who have verified their phone and NID but don't have a meter yet.
alter table login drop constraint if exists login_role_check;
alter table login add constraint login_role_check check ("user" in ('Admin', 'Customer', 'Applicant'));
alter table login add column if not exists phone  varchar(20);
alter table login add column if not exists nid_no varchar(17);
create unique index if not exists login_nid_key on login (nid_no) where nid_no is not null;

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

-- Only the backend server may touch these tables (see schema.sql).
alter table nid_registry      enable row level security;
alter table meter_application enable row level security;
