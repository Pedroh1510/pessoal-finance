package br.com.phfinance.finance.application;

import br.com.phfinance.finance.domain.InternalAccountRule;
import br.com.phfinance.finance.domain.InternalAccountRuleType;
import java.util.UUID;

public record InternalAccountRuleDTO(UUID id, String identifier, InternalAccountRuleType type) {

    public static InternalAccountRuleDTO from(InternalAccountRule rule) {
        return new InternalAccountRuleDTO(rule.getId(), rule.getIdentifier(), rule.getType());
    }
}
