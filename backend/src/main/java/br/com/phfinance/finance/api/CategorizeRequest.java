package br.com.phfinance.finance.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CategorizeRequest(@NotNull UUID categoryId) {}
