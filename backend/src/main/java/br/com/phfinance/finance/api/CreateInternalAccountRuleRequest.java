package br.com.phfinance.finance.api;

import br.com.phfinance.finance.domain.InternalAccountRuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInternalAccountRuleRequest(@NotBlank String identifier, @NotNull InternalAccountRuleType type) {}
