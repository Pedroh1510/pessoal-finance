package br.com.phfinance.finance.application;

import br.com.phfinance.finance.domain.RecipientCategoryRule;
import br.com.phfinance.finance.infra.RecipientCategoryRuleRepository;
import br.com.phfinance.shared.category.Category;
import br.com.phfinance.shared.category.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecipientRuleService {

    private final RecipientCategoryRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;

    public RecipientRuleService(
            RecipientCategoryRuleRepository ruleRepository,
            CategoryRepository categoryRepository) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipientCategoryRuleDTO> findAll() {
        return ruleRepository.findAll().stream()
                .map(RecipientCategoryRuleDTO::from)
                .toList();
    }

    public RecipientCategoryRuleDTO create(String recipientPattern, UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + categoryId));

        RecipientCategoryRule rule = new RecipientCategoryRule();
        rule.setRecipientPattern(recipientPattern);
        rule.setCategory(category);

        RecipientCategoryRule saved = ruleRepository.save(rule);
        return RecipientCategoryRuleDTO.from(saved);
    }

    public void delete(UUID ruleId) {
        RecipientCategoryRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + ruleId));
        ruleRepository.delete(rule);
    }
}
