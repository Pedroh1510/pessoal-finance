package br.com.phfinance.inflation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.phfinance.inflation.domain.MarketItem;
import br.com.phfinance.inflation.domain.MarketPurchase;
import br.com.phfinance.inflation.infra.MarketItemRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketComparisonServiceTest {

    @Mock MarketItemRepository itemRepository;
    MarketComparisonService service;

    @BeforeEach
    void setUp() {
        service = new MarketComparisonService(itemRepository);
    }

    @Test
    @DisplayName("getComparison returns price evolution for NCM")
    void getComparison_returnsNcmWithPricePoints() {
        MarketPurchase purchase1 = buildPurchase(LocalDate.of(2024, 1, 10), "Mercado A");
        MarketPurchase purchase2 = buildPurchase(LocalDate.of(2025, 1, 10), "Mercado B");
        MarketItem item1 = buildItem(purchase1, "02013000", "CARNE", new BigDecimal("42.00"));
        MarketItem item2 = buildItem(purchase2, "02013000", "CARNE", new BigDecimal("58.50"));

        when(itemRepository.findForComparison("02013000",
            LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 31)))
            .thenReturn(List.of(item1, item2));

        NcmComparisonDTO result = service.getComparison(
            "02013000", YearMonth.of(2024, 1), YearMonth.of(2025, 1));

        assertThat(result.ncm()).isEqualTo("02013000");
        assertThat(result.description()).isEqualTo("CARNE");
        assertThat(result.prices()).hasSize(2);
        assertThat(result.prices().get(0).period()).isEqualTo("2024-01");
        assertThat(result.prices().get(0).unitPrice()).isEqualByComparingTo("42.00");
        assertThat(result.prices().get(0).emitente()).isEqualTo("Mercado A");
        assertThat(result.prices().get(1).period()).isEqualTo("2025-01");
        assertThat(result.prices().get(1).unitPrice()).isEqualByComparingTo("58.50");
    }

    @Test
    @DisplayName("getComparison with no data returns empty prices list")
    void getComparison_noData_returnsEmptyPrices() {
        when(itemRepository.findForComparison("00000000",
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31)))
            .thenReturn(List.of());

        NcmComparisonDTO result = service.getComparison(
            "00000000", YearMonth.of(2024, 1), YearMonth.of(2024, 3));

        assertThat(result.prices()).isEmpty();
        assertThat(result.description()).isEmpty();
    }

    @Test
    @DisplayName("getItems with NCM filter returns matching items")
    void getItems_withNcmFilter_returnsMatchingItems() {
        MarketPurchase purchase = buildPurchase(LocalDate.of(2025, 3, 1), "Super X");
        MarketItem item = buildItem(purchase, "02013000", "CARNE", new BigDecimal("55.00"));

        when(itemRepository.findFiltered("02013000", null, null)).thenReturn(List.of(item));

        List<MarketItemDTO> result = service.getItems("02013000", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ncm()).isEqualTo("02013000");
        assertThat(result.get(0).period()).isEqualTo("2025-03");
        assertThat(result.get(0).emitente()).isEqualTo("Super X");
        assertThat(result.get(0).unitPrice()).isEqualByComparingTo("55.00");
    }

    @Test
    @DisplayName("getItems with period filter applies from/to date range")
    void getItems_withPeriodFilter_appliesDateRange() {
        when(itemRepository.findFiltered(null,
            LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)))
            .thenReturn(List.of());

        service.getItems(null, YearMonth.of(2025, 3));

        org.mockito.Mockito.verify(itemRepository)
            .findFiltered(null, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
    }

    private MarketPurchase buildPurchase(LocalDate date, String emitente) {
        MarketPurchase p = new MarketPurchase();
        p.setDate(date);
        p.setEmitente(emitente);
        p.setChave("00000000000000000000000000000000000000000001");
        p.setFileName("test.xls");
        return p;
    }

    private MarketItem buildItem(MarketPurchase purchase, String ncm,
                                  String description, BigDecimal unitPrice) {
        MarketItem item = new MarketItem();
        item.setPurchase(purchase);
        item.setNcm(ncm);
        item.setDescription(description);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(unitPrice);
        item.setTotalPrice(unitPrice);
        return item;
    }
}
