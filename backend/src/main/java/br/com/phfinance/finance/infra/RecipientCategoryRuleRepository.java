package br.com.phfinance.finance.infra;

import br.com.phfinance.finance.domain.RecipientCategoryRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientCategoryRuleRepository extends JpaRepository<RecipientCategoryRule, UUID> {}
