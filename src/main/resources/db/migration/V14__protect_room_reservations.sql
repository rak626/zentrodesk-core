CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservations
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_reservation_dates CHECK (check_in < check_out);

ALTER TABLE rooms
    ADD CONSTRAINT uk_room_id_property UNIQUE (id, property_id);

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservation_room_property
        FOREIGN KEY (room_id, property_id)
        REFERENCES rooms (id, property_id);

ALTER TABLE reservations
    ADD CONSTRAINT ex_reservation_room_dates
        EXCLUDE USING gist (
            room_id WITH =,
            daterange(check_in, check_out, '[)') WITH &&
        )
        WHERE (active = TRUE AND status IN ('RESERVED', 'CHECKED_IN'));

CREATE INDEX idx_reservation_property_dates
    ON reservations (property_id, check_in, check_out)
    WHERE active = TRUE AND status IN ('RESERVED', 'CHECKED_IN');

CREATE INDEX idx_reservation_room_dates
    ON reservations (room_id, check_in, check_out)
    WHERE active = TRUE AND status IN ('RESERVED', 'CHECKED_IN');
