package br.com.phfinance.finance.infra;

import br.com.phfinance.finance.domain.InternalAccountRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalAccountRuleRepository extends JpaRepository<InternalAccountRule, UUID> {}
