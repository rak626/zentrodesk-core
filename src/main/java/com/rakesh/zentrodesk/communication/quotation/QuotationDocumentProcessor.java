package com.rakesh.zentrodesk.communication.quotation;

import com.rakesh.zentrodesk.common.observability.SensitiveValueSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QuotationDocumentProcessor {

    private final QuotationDocumentRepository documentRepository;
    private final QuotationPdfRenderer pdfRenderer;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.quotation.pdf-processing-enabled:true}")
    private boolean processingEnabled;

    @Value("${app.quotation.pdf-max-attempts:5}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${app.quotation.pdf-processing-interval:2000}")
    public void generatePending() {
        if (!processingEnabled) {
            return;
        }
        documentRepository
                .findTop20ByGenerationStatusAndNextGenerationAttemptAtLessThanEqualOrderByCreatedAt(
                        QuotationDocumentGenerationStatus.PENDING,
                        Instant.now()
                )
                .stream()
                .map(QuotationDocument::getId)
                .forEach(this::generateOne);
    }

    public void generatePendingNow() {
        documentRepository
                .findTop20ByGenerationStatusAndNextGenerationAttemptAtLessThanEqualOrderByCreatedAt(
                        QuotationDocumentGenerationStatus.PENDING,
                        Instant.now()
                )
                .stream()
                .map(QuotationDocument::getId)
                .forEach(this::generateOne);
    }

    private void generateOne(UUID id) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                documentRepository.findLockedById(id)
                        .filter(document ->
                                document.getGenerationStatus() == QuotationDocumentGenerationStatus.PENDING)
                        .filter(document -> !document.getNextGenerationAttemptAt().isAfter(Instant.now()))
                        .ifPresent(this::attemptGeneration)
        );
    }

    private void attemptGeneration(QuotationDocument document) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> model = objectMapper.readValue(document.getModelSnapshot(), Map.class);
            byte[] content = pdfRenderer.render(
                    document.getHtmlSnapshot(),
                    document.getStyleSnapshot(),
                    model
            );
            document.setContent(content);
            document.setContentLength((long) content.length);
            document.setContentSha256(sha256(content));
            document.setGenerationStatus(QuotationDocumentGenerationStatus.GENERATED);
            document.setGeneratedAt(Instant.now());
            document.setLastError(null);
            if (document.isSendRequested()
                    && document.getDeliveryStatus() == QuotationDocumentDeliveryStatus.PENDING) {
                eventPublisher.publishEvent(new QuotationDocumentReadyEvent(document.getId(), Instant.now()));
            }
        } catch (Exception exception) {
            failOrRetry(document, exception);
        }
    }

    private void failOrRetry(QuotationDocument document, Exception exception) {
        int attempts = document.getGenerationAttempts() + 1;
        document.setGenerationAttempts(attempts);
        document.setLastError(SensitiveValueSanitizer.sanitize(
                SensitiveValueSanitizer.safeErrorType(exception) + ": " + exception.getMessage()
        ));
        if (attempts >= maxAttempts) {
            document.setGenerationStatus(QuotationDocumentGenerationStatus.FAILED);
            if (document.isSendRequested()) {
                document.setDeliveryStatus(QuotationDocumentDeliveryStatus.FAILED);
            }
            return;
        }
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        document.setNextGenerationAttemptAt(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS));
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
