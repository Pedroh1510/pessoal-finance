package br.com.phfinance.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Value object representing a transaction parsed from a bank statement PDF.
 * Amount is ALWAYS positive — TransactionType determines direction.
 * No JPA annotations: this is a pure domain value object.
 */
public record RawTransaction(
        LocalDateTime date,
        BigDecimal amount,
        String recipient,
        String description,
        TransactionType type,
        String rawText
) {
    public RawTransaction {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be non-null and non-negative");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }
}
