create schema if not exists zentro_desk;
set search_path to zentro_desk;

create table tenants (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    name varchar(150) not null,
    code varchar(50) not null unique,
    email varchar(150),
    phone varchar(20),
    address varchar(255),
    city varchar(100),
    state varchar(100),
    country varchar(100),
    timezone varchar(50),
    currency varchar(10),
    active boolean not null
);

create table roles (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    name varchar(100) not null,
    description varchar(255),
    active boolean not null
);

create table role_permissions (
    role_id uuid not null references roles(id) on delete cascade,
    permission varchar(100) not null,
    primary key (role_id, permission)
);

create table users (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    role_id uuid not null references roles(id),
    first_name varchar(100) not null,
    last_name varchar(100),
    email varchar(150) not null,
    phone varchar(20),
    password_hash varchar(255) not null,
    last_login_at timestamp(6) with time zone,
    active boolean not null,
    constraint uk_user_tenant_email unique (tenant_id, email)
);

create table refresh_tokens (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    user_id uuid not null references users(id),
    token varchar(512) not null unique,
    expires_at timestamp(6) with time zone not null,
    revoked boolean not null,
    revoked_at timestamp(6) with time zone
);

create table customers (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    name varchar(150) not null,
    phone varchar(20),
    email varchar(150),
    address varchar(255),
    city varchar(100),
    state varchar(100),
    country varchar(100),
    notes text,
    active boolean not null,
    constraint uk_customer_tenant_phone unique (tenant_id, phone)
);

create table leads (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    assigned_user_id uuid references users(id),
    name varchar(150) not null,
    phone varchar(20),
    email varchar(150),
    source varchar(100),
    destination varchar(150),
    travel_date date,
    adults integer,
    children integer,
    budget numeric(12, 2),
    status varchar(30) not null,
    remarks text,
    active boolean not null
);

create table quotations (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    lead_id uuid references leads(id),
    customer_id uuid references customers(id),
    quotation_number varchar(30) not null,
    travel_start date,
    travel_end date,
    adults integer,
    children integer,
    subtotal numeric(12, 2),
    discount numeric(12, 2),
    tax numeric(12, 2),
    total numeric(12, 2) not null,
    valid_until date,
    status varchar(30) not null,
    notes varchar(1000),
    active boolean not null,
    constraint uk_quotation_tenant_number unique (tenant_id, quotation_number)
);

create table quotation_items (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    quotation_id uuid not null references quotations(id),
    type varchar(30) not null,
    description varchar(200) not null,
    unit varchar(100),
    quantity integer not null,
    unit_price numeric(12, 2) not null,
    amount numeric(12, 2) not null,
    remarks text,
    active boolean not null
);

create table drivers (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    name varchar(150) not null,
    phone varchar(20),
    license_number varchar(100),
    vehicle_number varchar(100),
    vehicle_type varchar(100),
    status varchar(30) not null,
    active boolean not null
);

create table bookings (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    customer_id uuid not null references customers(id),
    quotation_id uuid references quotations(id),
    driver_id uuid references drivers(id),
    booking_number varchar(30) not null,
    travel_start date,
    travel_end date,
    status varchar(30) not null,
    notes text,
    active boolean not null,
    constraint uk_booking_number unique (booking_number)
);

create table booking_guests (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    booking_id uuid not null references bookings(id),
    name varchar(150) not null,
    gender varchar(10),
    date_of_birth date,
    phone varchar(20),
    identity_type varchar(50),
    identity_number varchar(100),
    primary_guest boolean not null,
    active boolean not null
);

create table payments (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    booking_id uuid not null references bookings(id),
    amount numeric(12, 2) not null,
    method varchar(30) not null,
    status varchar(30) not null,
    transaction_reference varchar(100),
    paid_at timestamp(6) with time zone,
    remarks varchar(500),
    active boolean not null
);

create table properties (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    name varchar(200) not null,
    type varchar(30) not null,
    address varchar(255),
    city varchar(100),
    state varchar(100),
    country varchar(100),
    phone varchar(20),
    email varchar(150),
    contact_person varchar(100),
    status varchar(30) not null,
    active boolean not null
);

create table rooms (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    property_id uuid not null references properties(id),
    name varchar(100) not null,
    type varchar(30) not null,
    capacity integer not null,
    base_price numeric(12, 2) not null,
    status varchar(30) not null,
    active boolean not null,
    constraint uk_room_property_name unique (property_id, name)
);

create table reservations (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    booking_id uuid not null references bookings(id),
    property_id uuid not null references properties(id),
    room_id uuid not null references rooms(id),
    check_in date not null,
    check_out date not null,
    status varchar(30) not null,
    active boolean not null
);

create table templates (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    name varchar(100) not null,
    type varchar(20) not null,
    content text not null,
    status varchar(20) not null,
    active boolean not null
);

create table conversation_messages (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    customer_id uuid references customers(id),
    phone varchar(20) not null,
    direction varchar(20) not null,
    message text not null,
    provider_message_id varchar(100),
    status varchar(20) not null,
    active boolean not null
);

create table notifications (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    user_id uuid not null references users(id),
    title varchar(200) not null,
    message text,
    status varchar(20) not null,
    active boolean not null
);

create table campaigns (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    name varchar(150) not null,
    type varchar(20) not null,
    template_id uuid references templates(id),
    scheduled_at timestamp(6) with time zone,
    status varchar(20) not null,
    active boolean not null
);

create table tasks (
    id uuid primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    tenant_id uuid not null references tenants(id),
    user_id uuid not null references users(id),
    title varchar(200) not null,
    description text,
    due_at timestamp(6) with time zone,
    status varchar(30) not null,
    active boolean not null
);

create index idx_tenant_code on tenants(code);
create index idx_role_tenant on roles(tenant_id);
create index idx_role_name on roles(name);
create index idx_role_permission_role on role_permissions(role_id);
create index idx_user_tenant on users(tenant_id);
create index idx_user_role on users(role_id);
create index idx_user_email on users(email);
create index idx_refresh_token_user on refresh_tokens(user_id);
create index idx_refresh_token_token on refresh_tokens(token);
create index idx_customer_tenant on customers(tenant_id);
create index idx_customer_phone on customers(phone);
create index idx_customer_email on customers(email);
create index idx_lead_tenant on leads(tenant_id);
create index idx_lead_assigned_user on leads(assigned_user_id);
create index idx_lead_phone on leads(phone);
create index idx_lead_status on leads(status);
create index idx_quotation_tenant on quotations(tenant_id);
create index idx_quotation_customer on quotations(customer_id);
create index idx_quotation_lead on quotations(lead_id);
create index idx_quotation_number on quotations(quotation_number);
create index idx_quotation_status on quotations(status);
create index idx_qitem_quotation on quotation_items(quotation_id);
create index idx_qitem_type on quotation_items(type);
create index idx_driver_tenant on drivers(tenant_id);
create index idx_driver_phone on drivers(phone);
create index idx_driver_status on drivers(status);
create index idx_booking_tenant on bookings(tenant_id);
create index idx_booking_customer on bookings(customer_id);
create index idx_booking_driver on bookings(driver_id);
create index idx_booking_status on bookings(status);
create index idx_booking_start on bookings(travel_start);
create index idx_booking_guest_booking on booking_guests(booking_id);
create index idx_payment_booking on payments(booking_id);
create index idx_payment_status on payments(status);
create index idx_payment_paid_at on payments(paid_at);
create index idx_property_tenant on properties(tenant_id);
create index idx_property_city on properties(city);
create index idx_property_type on properties(type);
create index idx_property_status on properties(status);
create index idx_room_property on rooms(property_id);
create index idx_room_type on rooms(type);
create index idx_room_status on rooms(status);
create index idx_reservation_booking on reservations(booking_id);
create index idx_reservation_property on reservations(property_id);
create index idx_reservation_room on reservations(room_id);
create index idx_reservation_status on reservations(status);
create index idx_reservation_checkin on reservations(check_in);
create index idx_template_tenant on templates(tenant_id);
create index idx_template_type on templates(type);
create index idx_template_status on templates(status);
create index idx_conversation_tenant on conversation_messages(tenant_id);
create index idx_conversation_customer on conversation_messages(customer_id);
create index idx_conversation_status on conversation_messages(status);
create index idx_notification_user on notifications(user_id);
create index idx_notification_status on notifications(status);
create index idx_campaign_tenant on campaigns(tenant_id);
create index idx_campaign_type on campaigns(type);
create index idx_campaign_status on campaigns(status);
create index idx_campaign_scheduled on campaigns(scheduled_at);
create index idx_task_tenant on tasks(tenant_id);
create index idx_task_user on tasks(user_id);
create index idx_task_due_at on tasks(due_at);
create index idx_task_status on tasks(status);
