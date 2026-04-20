package br.com.phfinance.inflation.infra;

import br.com.phfinance.inflation.domain.MarketItem;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketItemRepository extends JpaRepository<MarketItem, UUID> {

    @Query("""
        SELECT mi FROM MarketItem mi JOIN FETCH mi.purchase mp
        WHERE mi.ncm = :ncm
          AND mp.date >= :fromDate
          AND mp.date <= :toDate
        ORDER BY mp.date
    """)
    List<MarketItem> findForComparison(
        @Param("ncm") String ncm,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate
    );

    @Query("""
        SELECT mi FROM MarketItem mi JOIN FETCH mi.purchase mp
        WHERE (:ncm IS NULL OR mi.ncm = :ncm)
          AND (:fromDate IS NULL OR mp.date >= :fromDate)
          AND (:toDate IS NULL OR mp.date <= :toDate)
        ORDER BY mp.date DESC, mi.ncm
    """)
    List<MarketItem> findFiltered(
        @Param("ncm") String ncm,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate
    );
}
