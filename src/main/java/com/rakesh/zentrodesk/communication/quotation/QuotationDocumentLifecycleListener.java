package com.rakesh.zentrodesk.communication.quotation;

import com.rakesh.zentrodesk.sales.quotation.QuotationChangedEvent;
import com.rakesh.zentrodesk.sales.quotation.QuotationSendRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class QuotationDocumentLifecycleListener {

    private final QuotationDocumentService documentService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void snapshot(QuotationChangedEvent event) {
        documentService.queueSnapshot(event.tenantId(), event.quotationId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void requestSend(QuotationSendRequestedEvent event) {
        documentService.requestSend(event.tenantId(), event.quotationId());
    }
}
