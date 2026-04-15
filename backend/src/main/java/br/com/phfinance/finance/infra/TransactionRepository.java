package br.com.phfinance.finance.infra;

import br.com.phfinance.finance.domain.BankAccount;
import br.com.phfinance.finance.domain.Transaction;
import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.TransactionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    @Query("SELECT t FROM Transaction t WHERE t.account != :account " +
           "AND ABS(t.amount - :amount) <= 0.01 " +
           "AND t.date BETWEEN :from AND :to")
    List<Transaction> findPotentialTransferMatch(
        @Param("account") BankAccount account,
        @Param("amount") BigDecimal amount,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );

    Page<Transaction> findByAccountBankNameAndDateBetween(
        BankName bankName, OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
