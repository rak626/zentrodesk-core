package com.rakesh.zentrodesk.communication.quotation;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class SafeHtmlTemplateRenderer {

    private static final Pattern SECTION = Pattern.compile(
            "\\{\\{#([a-zA-Z][a-zA-Z0-9_.]*)}}([\\s\\S]*?)\\{\\{/\\1}}"
    );
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_.]*)}}");
    private static final Pattern UNSAFE_CONTENT = Pattern.compile(
            "(?i)<\\s*(script|iframe|object|embed)|javascript:|file:|"
                    + "(src|href)\\s*=\\s*[\"']?\\s*https?:|@import|url\\s*\\(|<!entity|<\\?xml-stylesheet"
    );

    String render(String template, Map<String, Object> model) {
        validateSafety(template);
        String rendered = expandSections(template, model);
        Matcher variables = VARIABLE.matcher(rendered);
        StringBuilder result = new StringBuilder();
        while (variables.find()) {
            Object value = resolve(variables.group(1), model, model);
            variables.appendReplacement(result, Matcher.quoteReplacement(escape(value)));
        }
        variables.appendTail(result);
        return result.toString();
    }

    void validateSafety(String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Quotation template content is required");
        }
        if (content.length() > 200_000) {
            throw new BadRequestException("Quotation template content is too large");
        }
        if (UNSAFE_CONTENT.matcher(content).find()) {
            throw new BadRequestException("Quotation template contains unsupported external or executable content");
        }
    }

    private String expandSections(String template, Map<String, Object> root) {
        String result = template;
        for (int pass = 0; pass < 10; pass++) {
            Matcher sections = SECTION.matcher(result);
            if (!sections.find()) {
                return result;
            }
            StringBuilder expanded = new StringBuilder();
            do {
                Object sectionValue = resolve(sections.group(1), root, root);
                String replacement = renderSection(sections.group(2), sectionValue, root);
                sections.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
            } while (sections.find());
            sections.appendTail(expanded);
            result = expanded.toString();
        }
        throw new BadRequestException("Quotation template sections are nested too deeply");
    }

    private String renderSection(String body, Object value, Map<String, Object> root) {
        if (!(value instanceof List<?> values)) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> rawContext)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) rawContext;
            Matcher variables = VARIABLE.matcher(body);
            StringBuilder rendered = new StringBuilder();
            while (variables.find()) {
                Object resolved = resolve(variables.group(1), context, root);
                variables.appendReplacement(rendered, Matcher.quoteReplacement(escape(resolved)));
            }
            variables.appendTail(rendered);
            result.append(rendered);
        }
        return result.toString();
    }

    private Object resolve(String path, Map<String, Object> context, Map<String, Object> root) {
        Object value = resolveFrom(path, context);
        return value == null && context != root ? resolveFrom(path, root) : value;
    }

    private Object resolveFrom(String path, Map<String, Object> values) {
        Object current = values;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
