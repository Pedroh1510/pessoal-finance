package br.com.phfinance.finance.infra;

import br.com.phfinance.finance.domain.BankAccount;
import br.com.phfinance.finance.domain.BankName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    Optional<BankAccount> findByBankName(BankName bankName);
}
