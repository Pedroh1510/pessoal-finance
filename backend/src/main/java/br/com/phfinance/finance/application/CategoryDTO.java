package br.com.phfinance.finance.application;

import br.com.phfinance.shared.category.Category;
import java.util.UUID;

public record CategoryDTO(UUID id, String name, String color, boolean isSystem) {

    public static CategoryDTO from(Category category) {
        return new CategoryDTO(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.isSystem());
    }
}
