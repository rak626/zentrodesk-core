package com.rakesh.zentrodesk.inventory.property.api;

import com.rakesh.zentrodesk.inventory.property.PropertyStatus;
import com.rakesh.zentrodesk.inventory.property.PropertyType;

import java.time.Instant;
import java.util.UUID;

public record PropertyResponse(UUID id, String name, PropertyType type, String address, String city, String state,
                               String country, String phone, String email, String contactPerson, PropertyStatus status,
                               boolean active, Instant createdAt, Instant updatedAt) {
}
