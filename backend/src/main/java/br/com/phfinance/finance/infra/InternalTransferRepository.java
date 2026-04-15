package br.com.phfinance.finance.infra;

import br.com.phfinance.finance.domain.InternalTransfer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalTransferRepository extends JpaRepository<InternalTransfer, UUID> {}
