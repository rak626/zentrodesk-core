set search_path to zentro_desk;

insert into leads (
    id, created_at, updated_at, tenant_id, assigned_user_id, name, phone, email, source, destination, travel_date, adults, children, budget, status, remarks, active
) values
    ('30000000-0000-0000-0000-000000000001', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000202', 'Ankit Verma', '+919777000001', 'ankit.verma@example.test', 'Website', 'Gangtok and Pelling', current_date + 30, 2, 1, 65000.00, 'QUALIFIED', 'Wants a six day family itinerary.', true),
    ('30000000-0000-0000-0000-000000000002', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000202', 'Sneha Kapoor', '+919777000002', 'sneha.kapoor@example.test', 'WhatsApp', 'Darjeeling', current_date + 45, 2, 0, 42000.00, 'CONTACTED', 'Asked for hotel and vehicle quote.', true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    assigned_user_id = excluded.assigned_user_id,
    name = excluded.name,
    phone = excluded.phone,
    email = excluded.email,
    source = excluded.source,
    destination = excluded.destination,
    travel_date = excluded.travel_date,
    adults = excluded.adults,
    children = excluded.children,
    budget = excluded.budget,
    status = excluded.status,
    remarks = excluded.remarks,
    active = excluded.active;

insert into quotations (
    id, created_at, updated_at, tenant_id, lead_id, customer_id, quotation_number, travel_start, travel_end, adults, children, subtotal, discount, tax, total, valid_until, status, notes, active
) values
    ('30000000-0000-0000-0000-000000000101', current_timestamp, current_timestamp, '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'QT-DEV-001', current_date + 30, current_date + 35, 2, 1, 63000.00, 2000.00, 3000.00, 64000.00, current_date + 10, 'SENT', 'Sample family tour quotation.', true)
on conflict on constraint uk_quotation_tenant_number do update set
    updated_at = excluded.updated_at,
    lead_id = excluded.lead_id,
    customer_id = excluded.customer_id,
    travel_start = excluded.travel_start,
    travel_end = excluded.travel_end,
    adults = excluded.adults,
    children = excluded.children,
    subtotal = excluded.subtotal,
    discount = excluded.discount,
    tax = excluded.tax,
    total = excluded.total,
    valid_until = excluded.valid_until,
    status = excluded.status,
    notes = excluded.notes,
    active = excluded.active;

insert into quotation_items (
    id, created_at, updated_at, quotation_id, type, description, unit, quantity, unit_price, amount, remarks, active
) values
    ('30000000-0000-0000-0000-000000000201', current_timestamp, current_timestamp, '30000000-0000-0000-0000-000000000101', 'HOTEL', 'Five nights family room package', 'night', 5, 6500.00, 32500.00, null, true),
    ('30000000-0000-0000-0000-000000000202', current_timestamp, current_timestamp, '30000000-0000-0000-0000-000000000101', 'VEHICLE', 'Private SUV with driver', 'day', 6, 4200.00, 25200.00, null, true),
    ('30000000-0000-0000-0000-000000000203', current_timestamp, current_timestamp, '30000000-0000-0000-0000-000000000101', 'PERMIT', 'Restricted area permit support', 'person', 3, 1766.67, 5300.00, null, true)
on conflict (id) do update set
    updated_at = excluded.updated_at,
    quotation_id = excluded.quotation_id,
    type = excluded.type,
    description = excluded.description,
    unit = excluded.unit,
    quantity = excluded.quantity,
    unit_price = excluded.unit_price,
    amount = excluded.amount,
    remarks = excluded.remarks,
    active = excluded.active;
