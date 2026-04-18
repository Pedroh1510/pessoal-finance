package br.com.phfinance.finance.domain;

import br.com.phfinance.finance.infra.TransactionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InternalTransferDetector {

    /**
     * Rule-based check: returns true if any internal_account_rule identifier appears
     * (case-insensitive substring) in the transaction's recipient, description, or rawText.
     */
    public boolean matchesInternalAccountRule(Transaction tx, List<InternalAccountRule> rules) {
        return rules.stream().anyMatch(rule -> matchesAnyField(tx, rule.getIdentifier().toLowerCase()));
    }

    private boolean matchesAnyField(Transaction tx, String identifierLower) {
        if (tx.getRecipient() != null && tx.getRecipient().toLowerCase().contains(identifierLower)) {
            return true;
        }
        if (tx.getDescription() != null && tx.getDescription().toLowerCase().contains(identifierLower)) {
            return true;
        }
        return tx.getRawText() != null && tx.getRawText().toLowerCase().contains(identifierLower);
    }

    /**
     * Auto-detection: finds a matching transaction in the repository — same amount (±0.01),
     * from a different account, within a ±1-minute window of the candidate's date.
     *
     * @return the first matching transaction, or Optional.empty() if none found
     */
    public Optional<Transaction> findAutoMatch(
        Transaction candidate,
        TransactionRepository repository
    ) {
        OffsetDateTime from = candidate.getDate().minusMinutes(1);
        OffsetDateTime to = candidate.getDate().plusMinutes(1);
        List<Transaction> matches = repository.findPotentialTransferMatch(
            candidate.getAccount(), candidate.getAmount(), from, to);
        return matches.stream().findFirst();
    }
}
