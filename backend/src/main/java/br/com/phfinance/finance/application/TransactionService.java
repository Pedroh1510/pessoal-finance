package br.com.phfinance.finance.application;

import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.Transaction;
import br.com.phfinance.finance.domain.TransactionType;
import br.com.phfinance.finance.infra.TransactionRepository;
import br.com.phfinance.finance.infra.TransactionSpecifications;
import br.com.phfinance.shared.category.Category;
import br.com.phfinance.shared.category.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    public TransactionDTO findById(UUID id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + id));
        return TransactionDTO.from(tx);
    }

    public Page<TransactionDTO> findAll(
            YearMonth month,
            UUID categoryId,
            BankName bank,
            TransactionType type,
            Pageable pageable) {

        OffsetDateTime from = null;
        OffsetDateTime to = null;

        if (month != null) {
            from = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            to = month.atEndOfMonth().atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
        }

        Specification<Transaction> spec =
                TransactionSpecifications.withFilters(bank, categoryId, type, from, to);

        return transactionRepository.findAll(spec, pageable).map(TransactionDTO::from);
    }

    @Transactional
    public void categorize(UUID transactionId, UUID categoryId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Transaction not found: " + transactionId));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Category not found: " + categoryId));

        tx.setCategory(category);
        transactionRepository.save(tx);
    }
}
