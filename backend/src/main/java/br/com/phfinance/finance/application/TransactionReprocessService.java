package br.com.phfinance.finance.application;

import br.com.phfinance.finance.domain.InternalAccountRule;
import br.com.phfinance.finance.domain.InternalTransferDetector;
import br.com.phfinance.finance.domain.RecipientCategoryRule;
import br.com.phfinance.finance.domain.Transaction;
import br.com.phfinance.finance.domain.TransactionType;
import br.com.phfinance.finance.infra.InternalAccountRuleRepository;
import br.com.phfinance.finance.infra.RecipientCategoryRuleRepository;
import br.com.phfinance.finance.infra.TransactionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransactionReprocessService {

    private final TransactionRepository transactionRepository;
    private final RecipientCategoryRuleRepository recipientRuleRepository;
    private final InternalAccountRuleRepository internalAccountRuleRepository;
    private final InternalTransferDetector internalTransferDetector;

    public TransactionReprocessService(
            TransactionRepository transactionRepository,
            RecipientCategoryRuleRepository recipientRuleRepository,
            InternalAccountRuleRepository internalAccountRuleRepository,
            InternalTransferDetector internalTransferDetector) {
        this.transactionRepository = transactionRepository;
        this.recipientRuleRepository = recipientRuleRepository;
        this.internalAccountRuleRepository = internalAccountRuleRepository;
        this.internalTransferDetector = internalTransferDetector;
    }

    public ReprocessResult reprocess() {
        List<RecipientCategoryRule> categoryRules = recipientRuleRepository.findAll();
        List<InternalAccountRule> accountRules = internalAccountRuleRepository.findAll();

        int categorized = applyMissingCategories(categoryRules);
        int typeChanged = applyInternalTransferTypes(accountRules);

        return new ReprocessResult(categorized, typeChanged);
    }

    private int applyMissingCategories(List<RecipientCategoryRule> rules) {
        if (rules.isEmpty()) return 0;
        int count = 0;
        for (Transaction tx : transactionRepository.findByCategoryIsNull()) {
            if (tx.getRecipient() == null) continue;
            String recipientLower = tx.getRecipient().toLowerCase();
            for (RecipientCategoryRule rule : rules) {
                if (recipientLower.contains(rule.getRecipientPattern().toLowerCase())) {
                    tx.setCategory(rule.getCategory());
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private int applyInternalTransferTypes(List<InternalAccountRule> rules) {
        if (rules.isEmpty()) return 0;
        int count = 0;
        for (Transaction tx : transactionRepository.findByTypeIn(
                List.of(TransactionType.INCOME, TransactionType.EXPENSE))) {
            if (internalTransferDetector.matchesInternalAccountRule(tx, rules)) {
                tx.setType(TransactionType.INTERNAL_TRANSFER);
                count++;
            }
        }
        return count;
    }
}
