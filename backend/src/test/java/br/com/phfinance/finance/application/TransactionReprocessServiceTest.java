package br.com.phfinance.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.phfinance.finance.domain.BankAccount;
import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.InternalAccountRule;
import br.com.phfinance.finance.domain.InternalTransferDetector;
import br.com.phfinance.finance.domain.RecipientCategoryRule;
import br.com.phfinance.finance.domain.Transaction;
import br.com.phfinance.finance.domain.TransactionType;
import br.com.phfinance.finance.infra.InternalAccountRuleRepository;
import br.com.phfinance.finance.infra.RecipientCategoryRuleRepository;
import br.com.phfinance.finance.infra.TransactionRepository;
import br.com.phfinance.shared.category.Category;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionReprocessServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private RecipientCategoryRuleRepository recipientRuleRepository;
    @Mock private InternalAccountRuleRepository internalAccountRuleRepository;
    @Mock private InternalTransferDetector internalTransferDetector;

    private TransactionReprocessService service;

    @BeforeEach
    void setUp() {
        service = new TransactionReprocessService(
            transactionRepository, recipientRuleRepository,
            internalAccountRuleRepository, internalTransferDetector);
    }

    @Test
    @DisplayName("reprocess assigns category when recipient matches rule")
    void reprocess_assignsCategory_whenRecipientMatchesRule() {
        Category category = buildCategory("Alimentação");
        RecipientCategoryRule rule = new RecipientCategoryRule();
        rule.setRecipientPattern("supermercado");
        rule.setCategory(category);

        Transaction tx = buildTransaction(null, TransactionType.EXPENSE, "Supermercado XYZ");

        when(recipientRuleRepository.findAll()).thenReturn(List.of(rule));
        when(internalAccountRuleRepository.findAll()).thenReturn(List.of());
        when(transactionRepository.findByCategoryIsNull()).thenReturn(List.of(tx));

        ReprocessResult result = service.reprocess();

        assertThat(result.categorized()).isEqualTo(1);
        assertThat(result.typeChanged()).isEqualTo(0);
        assertThat(tx.getCategory()).isEqualTo(category);
    }

    @Test
    @DisplayName("reprocess changes type to INTERNAL_TRANSFER when detector matches")
    void reprocess_changesType_whenDetectorMatches() {
        InternalAccountRule rule = new InternalAccountRule();
        rule.setIdentifier("poupança");

        Transaction tx = buildTransaction(null, TransactionType.EXPENSE, "Conta Poupança Inter");

        when(recipientRuleRepository.findAll()).thenReturn(List.of());
        when(internalAccountRuleRepository.findAll()).thenReturn(List.of(rule));
        when(transactionRepository.findByTypeIn(any())).thenReturn(List.of(tx));
        when(internalTransferDetector.matchesInternalAccountRule(tx, List.of(rule))).thenReturn(true);

        ReprocessResult result = service.reprocess();

        assertThat(result.typeChanged()).isEqualTo(1);
        assertThat(tx.getType()).isEqualTo(TransactionType.INTERNAL_TRANSFER);
    }

    @Test
    @DisplayName("reprocess returns zero counts when no rules exist")
    void reprocess_returnsZeroCounts_whenNoRulesExist() {
        when(recipientRuleRepository.findAll()).thenReturn(List.of());
        when(internalAccountRuleRepository.findAll()).thenReturn(List.of());

        ReprocessResult result = service.reprocess();

        assertThat(result.categorized()).isEqualTo(0);
        assertThat(result.typeChanged()).isEqualTo(0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Transaction buildTransaction(Category category, TransactionType type, String recipient) {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setBankName(BankName.NUBANK);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setAccount(account);
        tx.setDate(OffsetDateTime.now());
        tx.setAmount(BigDecimal.TEN);
        tx.setType(type);
        tx.setRecipient(recipient);
        tx.setCategory(category);
        return tx;
    }

    private Category buildCategory(String name) {
        Category c = new Category();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setColor("#FF0000");
        return c;
    }
}
