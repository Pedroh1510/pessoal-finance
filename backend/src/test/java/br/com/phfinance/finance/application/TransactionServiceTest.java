package br.com.phfinance.finance.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.phfinance.finance.domain.BankAccount;
import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.Transaction;
import br.com.phfinance.finance.domain.TransactionType;
import br.com.phfinance.finance.infra.TransactionRepository;
import br.com.phfinance.shared.category.Category;
import br.com.phfinance.shared.category.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, categoryRepository);
    }

    @Test
    @DisplayName("categorize throws EntityNotFoundException when transaction not found")
    void categorize_throwsEntityNotFoundException_whenTransactionNotFound() {
        UUID transactionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.categorize(transactionId, categoryId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(transactionId.toString());
    }

    @Test
    @DisplayName("categorize throws EntityNotFoundException when category not found")
    void categorize_throwsEntityNotFoundException_whenCategoryNotFound() {
        UUID transactionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Transaction tx = buildTransaction(transactionId);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(tx));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.categorize(transactionId, categoryId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(categoryId.toString());
    }

    @Test
    @DisplayName("categorize sets category on transaction and saves")
    void categorize_setCategoryAndSaves_whenBothFound() {
        UUID transactionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Transaction tx = buildTransaction(transactionId);
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Alimentação");
        category.setColor("#FF0000");

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(tx));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);

        transactionService.categorize(transactionId, categoryId);

        verify(transactionRepository).save(tx);
        org.assertj.core.api.Assertions.assertThat(tx.getCategory()).isEqualTo(category);
    }

    @Test
    @DisplayName("findAll calls repository with null search when search is blank")
    void findAll_nullSearch_callsRepository() {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(transactionRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Transaction>>any(),
                org.mockito.ArgumentMatchers.eq(pageable)))
            .thenReturn(org.springframework.data.domain.Page.empty());

        transactionService.findAll(null, null, null, null, null, pageable);

        verify(transactionRepository).findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Transaction>>any(),
                org.mockito.ArgumentMatchers.eq(pageable));
    }

    @Test
    @DisplayName("findAll calls repository with non-null search term")
    void findAll_withSearch_callsRepository() {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 20);
        when(transactionRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Transaction>>any(),
                org.mockito.ArgumentMatchers.eq(pageable)))
            .thenReturn(org.springframework.data.domain.Page.empty());

        transactionService.findAll(null, null, null, null, "mercado", pageable);

        verify(transactionRepository).findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Transaction>>any(),
                org.mockito.ArgumentMatchers.eq(pageable));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Transaction buildTransaction(UUID id) {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setBankName(BankName.NUBANK);

        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setAccount(account);
        tx.setDate(OffsetDateTime.now());
        tx.setAmount(BigDecimal.TEN);
        tx.setType(TransactionType.EXPENSE);
        return tx;
    }
}
