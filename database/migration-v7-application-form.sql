-- Migration v7: the full new-connection application form, field for field with DPDC's online form
-- (docs/DPDC-new-connection-form-fields.txt): consumer type (personal or organization), parents, spouse
-- and gender or the organization's name, separate mailing and connection addresses (section, block,
-- thana, post code, telephone, mobile), NOCS, connection type, tariff category, number of meters and
-- existing meters and load, and the declaration.
--
-- Existing columns keep their meaning: house, road, area, district, division and email are the
-- connection address; post_office is kept for applications made before this form.
-- Safe to run more than once. Run after migration-v6-new-meter-applications.sql.

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
