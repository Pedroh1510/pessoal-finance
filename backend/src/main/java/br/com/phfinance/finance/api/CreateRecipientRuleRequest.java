package br.com.phfinance.finance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateRecipientRuleRequest(@NotBlank String recipientPattern, @NotNull UUID categoryId) {}
