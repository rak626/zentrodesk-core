package com.rakesh.zentrodesk.common.search;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.UUID;

public final class SearchSpecificationBuilder {

    private SearchSpecificationBuilder() {
    }

    public static <T> Specification<T> textContains(String value, Collection<String> fields) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(value) || fields == null || fields.isEmpty()) {
                return cb.conjunction();
            }
            String pattern = "%" + value.trim().toLowerCase() + "%";
            return cb.or(fields.stream()
                    .map(field -> cb.like(cb.lower(asString(root, field)), pattern))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public static <T> Specification<T> uuidEquals(String field, UUID value) {
        return (root, query, cb) -> value == null
                ? cb.conjunction()
                : cb.equal(path(root, field), value);
    }

    public static <T> Specification<T> booleanEquals(String field, Boolean value) {
        return (root, query, cb) -> value == null
                ? cb.conjunction()
                : cb.equal(path(root, field), value);
    }

    private static <T> Expression<String> asString(Root<T> root, String field) {
        return path(root, field).as(String.class);
    }

    private static <T> Path<?> path(Root<T> root, String field) {
        Path<?> path = root;
        for (String part : field.split("\\.")) {
            path = path.get(part);
        }
        return path;
    }
}
