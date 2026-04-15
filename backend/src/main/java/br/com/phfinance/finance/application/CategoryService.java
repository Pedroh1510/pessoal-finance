package br.com.phfinance.finance.application;

import br.com.phfinance.shared.category.Category;
import br.com.phfinance.shared.category.CategoryRepository;
import br.com.phfinance.shared.exception.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDTO> findAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryDTO::from)
                .toList();
    }

    public CategoryDTO create(String name, String color) {
        Category category = new Category();
        category.setName(name);
        category.setColor(color);
        category.setSystem(false);
        Category saved = categoryRepository.save(category);
        return CategoryDTO.from(saved);
    }

    public CategoryDTO update(UUID id, String name, String color) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
        category.setName(name);
        category.setColor(color);
        Category saved = categoryRepository.save(category);
        return CategoryDTO.from(saved);
    }

    public void delete(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
        if (category.isSystem()) {
            throw new BusinessException("Cannot delete system category");
        }
        categoryRepository.delete(category);
    }
}
