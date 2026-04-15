package br.com.phfinance.finance.domain;

import br.com.phfinance.finance.infra.TransactionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InternalTransferDetector {

    /**
     * Rule-based check: returns true if the transaction's recipient contains any
     * internal_account_rule identifier (case-insensitive substring match).
     */
    public boolean matchesInternalAccountRule(Transaction tx, List<InternalAccountRule> rules) {
        if (tx.getRecipient() == null) {
            return false;
        }
        String recipientLower = tx.getRecipient().toLowerCase();
        return rules.stream()
            .anyMatch(rule -> recipientLower.contains(rule.getIdentifier().toLowerCase()));
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
