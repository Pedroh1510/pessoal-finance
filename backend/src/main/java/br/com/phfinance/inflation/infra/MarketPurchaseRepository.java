package br.com.phfinance.inflation.infra;

import br.com.phfinance.inflation.domain.MarketPurchase;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketPurchaseRepository extends JpaRepository<MarketPurchase, UUID> {
    boolean existsByChave(String chave);
}
