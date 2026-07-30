ALTER TABLE quotations
    ADD COLUMN series_id UUID,
    ADD COLUMN revision_number INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN revised_from_id UUID REFERENCES quotations(id),
    ADD COLUMN superseded BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE quotations
SET series_id = id;

ALTER TABLE quotations
    ALTER COLUMN series_id SET NOT NULL,
    ALTER COLUMN series_id SET DEFAULT gen_random_uuid(),
    DROP CONSTRAINT uk_quotation_tenant_number,
    ADD CONSTRAINT uk_quotation_tenant_number_revision
        UNIQUE (tenant_id, quotation_number, revision_number),
    ADD CONSTRAINT ck_quotation_revision_number CHECK (revision_number > 0);

CREATE INDEX idx_quotation_series
    ON quotations (tenant_id, series_id);

ALTER TABLE quotation_items
    ADD COLUMN pricing_basis VARCHAR(30) NOT NULL DEFAULT 'FIXED',
    ADD COLUMN chargeable_people INTEGER,
    ADD COLUMN chargeable_people_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN vehicle_capacity INTEGER,
    ADD COLUMN vehicle_count INTEGER,
    ADD COLUMN vehicle_count_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_quotation_item_pricing_basis
        CHECK (pricing_basis IN ('PER_PERSON', 'PER_VEHICLE', 'FIXED')),
    ADD CONSTRAINT ck_quotation_item_chargeable_people
        CHECK (chargeable_people IS NULL OR chargeable_people > 0),
    ADD CONSTRAINT ck_quotation_item_vehicle_capacity
        CHECK (vehicle_capacity IS NULL OR vehicle_capacity > 0),
    ADD CONSTRAINT ck_quotation_item_vehicle_count
        CHECK (vehicle_count IS NULL OR vehicle_count > 0);
