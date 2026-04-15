package br.com.phfinance.shared.category.api;

import br.com.phfinance.finance.application.CategoryDTO;
import br.com.phfinance.finance.application.CategoryService;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryDTO> findAll() {
        return categoryService.findAll();
    }

    @PostMapping
    public ResponseEntity<CategoryDTO> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryDTO dto = categoryService.create(request.name(), request.color());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public CategoryDTO update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.update(id, request.name(), request.color());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        categoryService.delete(id);
    }
}
