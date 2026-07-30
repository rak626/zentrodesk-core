package com.rakesh.zentrodesk.communication.quotation;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.rakesh.zentrodesk.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class QuotationPdfRenderer {

    private final SafeHtmlTemplateRenderer templateRenderer;

    public byte[] render(String htmlTemplate, String style, Map<String, Object> model) {
        templateRenderer.validateSafety(style == null ? " " : style);
        String html = templateRenderer.render(htmlTemplate, model);
        String document = includeStyle(html, style);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(document, null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BadRequestException("Quotation template could not be rendered as PDF");
        }
    }

    public void validate(String htmlTemplate, String style) {
        render(htmlTemplate, style, QuotationTemplateDefaults.sampleModel());
    }

    private String includeStyle(String html, String style) {
        if (style == null || style.isBlank()) {
            return html;
        }
        String styleElement = "<style type=\"text/css\">" + style + "</style>";
        int headEnd = html.toLowerCase().indexOf("</head>");
        if (headEnd < 0) {
            throw new BadRequestException("Quotation template must contain a head element");
        }
        return html.substring(0, headEnd) + styleElement + html.substring(headEnd);
    }
}
