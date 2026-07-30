package com.rakesh.zentrodesk.sales.quotation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuotationItemResourceRepository extends JpaRepository<QuotationItemResource, UUID> {

    List<QuotationItemResource> findByQuotationItemIdAndActiveTrueOrderByCreatedAt(UUID quotationItemId);
}
