package com.rakesh.zentrodesk.sales.customer.api;

import java.util.UUID;

public record CustomerLookupResponse(UUID id, String name, String city) {
}
