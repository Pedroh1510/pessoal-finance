package br.com.phfinance.finance.infra;

import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.Transaction;
import br.com.phfinance.finance.domain.TransactionType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class TransactionSpecifications {

    private TransactionSpecifications() {}

    public static Specification<Transaction> withFilters(
            BankName bank,
            UUID categoryId,
            TransactionType type,
            OffsetDateTime from,
            OffsetDateTime to,
            String search) {

        return Specification
                .where(hasBankName(bank))
                .and(hasCategoryId(categoryId))
                .and(hasType(type))
                .and(dateFrom(from))
                .and(dateTo(to))
                .and(hasSearch(search));
    }

    private static Specification<Transaction> hasBankName(BankName bank) {
        if (bank == null) return null;
        return (root, query, cb) -> cb.equal(root.get("account").get("bankName"), bank);
    }

    private static Specification<Transaction> hasCategoryId(UUID categoryId) {
        if (categoryId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    private static Specification<Transaction> hasType(TransactionType type) {
        if (type == null) return null;
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    private static Specification<Transaction> dateFrom(OffsetDateTime from) {
        if (from == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    private static Specification<Transaction> dateTo(OffsetDateTime to) {
        if (to == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to);
    }

    private static Specification<Transaction> hasSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String escaped = search.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return (root, query, cb) -> {
            jakarta.persistence.criteria.Predicate matchRecipient = cb.and(
                    cb.isNotNull(root.get("recipient")),
                    cb.like(cb.lower(root.get("recipient")), pattern, '\\'));
            jakarta.persistence.criteria.Predicate matchDescription = cb.and(
                    cb.isNotNull(root.get("description")),
                    cb.like(cb.lower(root.get("description")), pattern, '\\'));
            return cb.or(matchRecipient, matchDescription);
        };
    }
}
