set search_path to zentro_desk;

insert into bookings (
    id, created_at, updated_at, tenant_id, customer_id, quotation_id, driver_id, booking_number, travel_start, travel_end, status, notes, active
) values
    ('60000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000101', null, 'BK-DEV-001', current_date + 30, current_date + 35, 'CONFIRMED', 'Sample confirmed family booking.', true)
on conflict on constraint uk_booking_number do update set
    updated_at = excluded.updated_at,
    tenant_id = excluded.tenant_id,
    customer_id = excluded.customer_id,
    quotation_id = excluded.quotation_id,
    driver_id = excluded.driver_id,
    travel_start = excluded.travel_start,
    travel_end = excluded.travel_end,
    status = excluded.status,
    notes = excluded.notes,
    active = excluded.active;

insert into booking_guests (
    id, created_at, updated_at, booking_id, name, gender, date_of_birth, phone, identity_type, identity_number, primary_guest, active
) values
    ('60000000-0000-0000-0000-000000000101', current_timestamp, current_timestamp, '60000000-0000-0000-0000-000000000001', 'Rahul Sharma', 'MALE', date '1988-04-12', '+919888000001', 'AADHAAR', 'XXXX-XXXX-0001', true, true),
    ('60000000-0000-0000-0000-000000000102', current_timestamp, current_timestamp, '60000000-0000-0000-0000-000000000001', 'Neha Sharma', 'FEMALE', date '1990-08-21', null, 'AADHAAR', 'XXXX-XXXX-0002', false, true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    booking_id = excluded.booking_id,
    name = excluded.name,
    gender = excluded.gender,
    date_of_birth = excluded.date_of_birth,
    phone = excluded.phone,
    identity_type = excluded.identity_type,
    identity_number = excluded.identity_number,
    primary_guest = excluded.primary_guest,
    active = excluded.active;

insert into payments (
    id, created_at, updated_at, booking_id, amount, method, status, transaction_reference, paid_at, remarks, active
) values
    ('60000000-0000-0000-0000-000000000201', current_timestamp, current_timestamp, '60000000-0000-0000-0000-000000000001', 15000.00, 'UPI', 'PARTIAL', 'UPI-DEV-001', current_timestamp, 'Advance payment.', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    booking_id = excluded.booking_id,
    amount = excluded.amount,
    method = excluded.method,
    status = excluded.status,
    transaction_reference = excluded.transaction_reference,
    paid_at = excluded.paid_at,
    remarks = excluded.remarks,
    active = excluded.active;
