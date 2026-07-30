package com.rakesh.zentrodesk.communication.quotation;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationPdfRendererTest {

    private final SafeHtmlTemplateRenderer templateRenderer = new SafeHtmlTemplateRenderer();
    private final QuotationPdfRenderer pdfRenderer = new QuotationPdfRenderer(templateRenderer);

    @Test
    void rendersDefaultTemplateAsPdf() {
        byte[] pdf = pdfRenderer.render(
                QuotationTemplateDefaults.HTML,
                QuotationTemplateDefaults.STYLE,
                QuotationTemplateDefaults.sampleModel()
        );

        assertTrue(pdf.length > 1_000);
        assertArrayEquals("%PDF".getBytes(), java.util.Arrays.copyOf(pdf, 4));
    }

    @Test
    void expandsItemsAndEscapesTenantData() {
        String html = """
                <html><head></head><body>
                {{tenant.name}}
                {{#items}}<span>{{description}}</span>{{/items}}
                </body></html>
                """;

        String rendered = templateRenderer.render(html, Map.of(
                "tenant", Map.of("name", "<Agency>"),
                "items", List.of(
                        Map.of("description", "Hotel & breakfast"),
                        Map.of("description", "Transfer")
                )
        ));

        assertTrue(rendered.contains("&lt;Agency&gt;"));
        assertTrue(rendered.contains("Hotel &amp; breakfast"));
        assertTrue(rendered.contains("<span>Transfer</span>"));
    }

    @Test
    void rejectsExternalResources() {
        assertThrows(
                BadRequestException.class,
                () -> pdfRenderer.render(
                        "<html><head></head><body><img src=\"https://example.com/logo.png\" /></body></html>",
                        "",
                        Map.of()
                )
        );
    }
}
