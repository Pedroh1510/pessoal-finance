package br.com.phfinance.shared.category.api;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(@NotBlank String name, @NotBlank String color) {}
