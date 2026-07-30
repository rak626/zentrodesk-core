package com.rakesh.zentrodesk.communication.quotation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuotationTemplateDefaults {

    public static final String NAME = "ZentroDesk default quotation";

    public static final String HTML = """
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <meta charset="UTF-8" />
                <title>Quotation {{quotation.number}}</title>
            </head>
            <body>
                <header>
                    <div class="brand">{{tenant.name}}</div>
                    <div>{{tenant.address}}</div>
                    <div>{{tenant.phone}} {{tenant.email}}</div>
                </header>
                <h1>Quotation</h1>
                <section class="summary">
                    <div><strong>Quotation:</strong> {{quotation.number}}</div>
                    <div><strong>Prepared for:</strong> {{customer.name}}</div>
                    <div><strong>Travel:</strong> {{quotation.travelStart}} to {{quotation.travelEnd}}</div>
                    <div><strong>Valid until:</strong> {{quotation.validUntil}}</div>
                </section>
                <table>
                    <thead>
                        <tr>
                            <th>Description</th>
                            <th>Qty</th>
                            <th>Unit price</th>
                            <th>Amount</th>
                        </tr>
                    </thead>
                    <tbody>
                        {{#items}}
                        <tr>
                            <td>{{description}}</td>
                            <td>{{quantity}}</td>
                            <td>{{unitPrice}}</td>
                            <td>{{amount}}</td>
                        </tr>
                        {{/items}}
                    </tbody>
                </table>
                <section class="totals">
                    <div>Subtotal: {{quotation.subtotal}}</div>
                    <div>Discount: {{quotation.discount}}</div>
                    <div>Tax: {{quotation.tax}}</div>
                    <div class="grand-total">Total: {{tenant.currency}} {{quotation.total}}</div>
                </section>
                <section class="notes">
                    <strong>Notes and terms</strong>
                    <p>{{quotation.notes}}</p>
                </section>
            </body>
            </html>
            """;

    public static final String STYLE = """
            @page { size: A4; margin: 20mm; }
            body { font-family: sans-serif; color: #1f2937; font-size: 10pt; }
            header { border-bottom: 2px solid #2563eb; padding-bottom: 10px; margin-bottom: 24px; }
            .brand { font-size: 20pt; font-weight: bold; color: #1d4ed8; }
            h1 { font-size: 22pt; margin: 0 0 18px 0; }
            .summary { margin-bottom: 20px; line-height: 1.6; }
            table { width: 100%; border-collapse: collapse; }
            th { background: #eff6ff; text-align: left; }
            th, td { border: 1px solid #d1d5db; padding: 8px; }
            th:nth-child(n+2), td:nth-child(n+2) { text-align: right; }
            .totals { margin-top: 18px; text-align: right; line-height: 1.7; }
            .grand-total { font-size: 13pt; font-weight: bold; }
            .notes { margin-top: 28px; }
            """;

    private QuotationTemplateDefaults() {
    }

    public static Map<String, Object> sampleModel() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("tenant", map(
                "name", "Sample Travel",
                "address", "123 Main Street",
                "phone", "+91 90000 00000",
                "email", "hello@example.com",
                "currency", "INR"
        ));
        model.put("customer", map("name", "Sample Customer"));
        model.put("quotation", map(
                "number", "Q-2026-000001",
                "travelStart", LocalDate.of(2026, 8, 10),
                "travelEnd", LocalDate.of(2026, 8, 14),
                "validUntil", LocalDate.of(2026, 8, 5),
                "subtotal", new BigDecimal("25000.00"),
                "discount", new BigDecimal("1000.00"),
                "tax", new BigDecimal("4320.00"),
                "total", new BigDecimal("28320.00"),
                "notes", "Thank you for considering this quotation."
        ));
        model.put("items", List.of(
                map(
                        "description", "Hotel accommodation",
                        "quantity", 4,
                        "unitPrice", new BigDecimal("5000.00"),
                        "amount", new BigDecimal("20000.00")
                ),
                map(
                        "description", "Airport transfer",
                        "quantity", 1,
                        "unitPrice", new BigDecimal("5000.00"),
                        "amount", new BigDecimal("5000.00")
                )
        ));
        return model;
    }

    public static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index].toString(), values[index + 1]);
        }
        return result;
    }
}
