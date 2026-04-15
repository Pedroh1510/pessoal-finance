package br.com.phfinance.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.phfinance.shared.category.Category;
import br.com.phfinance.shared.category.CategoryRepository;
import br.com.phfinance.shared.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    @DisplayName("delete throws BusinessException when category isSystem = true")
    void delete_throwsBusinessException_whenCategoryIsSystem() {
        UUID id = UUID.randomUUID();
        Category systemCategory = new Category();
        systemCategory.setId(id);
        systemCategory.setName("Alimentação");
        systemCategory.setColor("#FF0000");
        systemCategory.setSystem(true);

        when(categoryRepository.findById(id)).thenReturn(Optional.of(systemCategory));

        assertThatThrownBy(() -> categoryService.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot delete system category");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete succeeds and calls repository when isSystem = false")
    void delete_succeeds_whenCategoryIsNotSystem() {
        UUID id = UUID.randomUUID();
        Category userCategory = new Category();
        userCategory.setId(id);
        userCategory.setName("Viagens");
        userCategory.setColor("#00FF00");
        userCategory.setSystem(false);

        when(categoryRepository.findById(id)).thenReturn(Optional.of(userCategory));

        categoryService.delete(id);

        verify(categoryRepository).delete(userCategory);
    }

    @Test
    @DisplayName("create sets name, color, isSystem=false and returns DTO")
    void create_setsFieldsCorrectly() {
        String name = "Transporte";
        String color = "#0000FF";

        Category saved = new Category();
        saved.setId(UUID.randomUUID());
        saved.setName(name);
        saved.setColor(color);
        saved.setSystem(false);

        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryDTO result = categoryService.create(name, color);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(name);
        assertThat(captor.getValue().getColor()).isEqualTo(color);
        assertThat(captor.getValue().isSystem()).isFalse();

        assertThat(result.name()).isEqualTo(name);
        assertThat(result.color()).isEqualTo(color);
        assertThat(result.isSystem()).isFalse();
    }
}
