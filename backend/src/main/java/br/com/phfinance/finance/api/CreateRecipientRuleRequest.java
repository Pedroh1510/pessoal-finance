package br.com.phfinance.finance.api;

import java.util.UUID;

public record CreateRecipientRuleRequest(String recipientPattern, UUID categoryId) {}
