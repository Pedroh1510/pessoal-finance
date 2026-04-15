package br.com.phfinance.finance.api;

import br.com.phfinance.finance.domain.InternalAccountRuleType;

public record CreateInternalAccountRuleRequest(String identifier, InternalAccountRuleType type) {}
