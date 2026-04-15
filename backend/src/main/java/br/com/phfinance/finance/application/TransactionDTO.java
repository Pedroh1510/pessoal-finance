package br.com.phfinance.finance.application;

import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.Transaction;
import br.com.phfinance.finance.domain.TransactionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionDTO(
        UUID transactionId,
        OffsetDateTime date,
        BigDecimal amount,
        String recipient,
        String description,
        UUID categoryId,
        String categoryName,
        TransactionType type,
        BankName bankName) {

    public static TransactionDTO from(Transaction tx) {
        return new TransactionDTO(
                tx.getId(),
                tx.getDate(),
                tx.getAmount(),
                tx.getRecipient(),
                tx.getDescription(),
                tx.getCategory() != null ? tx.getCategory().getId() : null,
                tx.getCategory() != null ? tx.getCategory().getName() : null,
                tx.getType(),
                tx.getAccount().getBankName());
    }
}
