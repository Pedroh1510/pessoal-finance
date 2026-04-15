package br.com.phfinance.finance.application;

import br.com.phfinance.finance.domain.RecipientCategoryRule;
import java.util.UUID;

public record RecipientCategoryRuleDTO(
        UUID id,
        String recipientPattern,
        UUID categoryId,
        String categoryName) {

    public static RecipientCategoryRuleDTO from(RecipientCategoryRule rule) {
        return new RecipientCategoryRuleDTO(
                rule.getId(),
                rule.getRecipientPattern(),
                rule.getCategory().getId(),
                rule.getCategory().getName());
    }
}
