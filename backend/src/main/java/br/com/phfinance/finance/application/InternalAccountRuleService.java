package br.com.phfinance.finance.application;

import br.com.phfinance.finance.domain.InternalAccountRule;
import br.com.phfinance.finance.domain.InternalAccountRuleType;
import br.com.phfinance.finance.infra.InternalAccountRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InternalAccountRuleService {

    private final InternalAccountRuleRepository ruleRepository;

    public InternalAccountRuleService(InternalAccountRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Transactional(readOnly = true)
    public List<InternalAccountRuleDTO> findAll() {
        return ruleRepository.findAll().stream()
                .map(InternalAccountRuleDTO::from)
                .toList();
    }

    public InternalAccountRuleDTO create(String identifier, InternalAccountRuleType type) {
        InternalAccountRule rule = new InternalAccountRule();
        rule.setIdentifier(identifier);
        rule.setType(type);
        InternalAccountRule saved = ruleRepository.save(rule);
        return InternalAccountRuleDTO.from(saved);
    }

    public void delete(UUID ruleId) {
        InternalAccountRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + ruleId));
        ruleRepository.delete(rule);
    }
}
