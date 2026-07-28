ALTER TABLE driver_assignments
    ADD CONSTRAINT uk_assignment_id_tenant UNIQUE (id, tenant_id);

ALTER TABLE driver_assignment_history
    ADD CONSTRAINT fk_assignment_history_assignment_tenant
        FOREIGN KEY (assignment_id, tenant_id)
        REFERENCES driver_assignments (id, tenant_id);

ALTER TABLE assignment_notification_outbox
    ADD CONSTRAINT fk_assignment_notification_assignment_tenant
        FOREIGN KEY (assignment_id, tenant_id)
        REFERENCES driver_assignments (id, tenant_id);
