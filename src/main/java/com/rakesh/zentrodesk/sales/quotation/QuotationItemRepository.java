package com.rakesh.zentrodesk.sales.quotation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuotationItemRepository extends JpaRepository<QuotationItem, UUID> {

    List<QuotationItem> findByQuotationIdAndActiveTrueOrderByPosition(UUID quotationId);
}
