package br.com.phfinance.inflation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.phfinance.inflation.domain.MarketPurchase;
import br.com.phfinance.inflation.infra.MarketPurchaseRepository;
import br.com.phfinance.inflation.infra.ParsedRow;
import br.com.phfinance.inflation.infra.SpreadsheetParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketUploadServiceTest {

    @Mock SpreadsheetParser spreadsheetParser;
    @Mock MarketPurchaseRepository purchaseRepository;

    MarketUploadService service;

    private static final String CHAVE_A = "35260348564323000134650290000032581020295871";
    private static final String CHAVE_B = "35260348564323000134650290000032581020295872";
    private static final LocalDate DATE = LocalDate.of(2026, 3, 29);

    @BeforeEach
    void setUp() {
        service = new MarketUploadService(spreadsheetParser, purchaseRepository);
    }

    @Test
    @DisplayName("upload groups rows by chave and saves one purchase per chave")
    void upload_multipleRowsSameChave_savesOnePurchase() {
        byte[] bytes = "file".getBytes();
        List<ParsedRow> rows = List.of(
            new ParsedRow(CHAVE_A, DATE, "MERCADO X", "12403", "10059010", "MILHO",
                new BigDecimal("1.5"), new BigDecimal("8.90"), new BigDecimal("13.35")),
            new ParsedRow(CHAVE_A, DATE, "MERCADO X", "70087", "19059010", "PAO",
                new BigDecimal("1.0"), new BigDecimal("7.99"), new BigDecimal("7.99"))
        );
        when(spreadsheetParser.parse(bytes)).thenReturn(rows);
        when(purchaseRepository.existsByChave(CHAVE_A)).thenReturn(false);
        when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InflationUploadResult result = service.upload(bytes, "2603_produtos.xls");

        assertThat(result.purchasesCreated()).isEqualTo(1);
        assertThat(result.purchasesSkipped()).isEqualTo(0);
        assertThat(result.itemsImported()).isEqualTo(2);

        ArgumentCaptor<MarketPurchase> captor = ArgumentCaptor.forClass(MarketPurchase.class);
        verify(purchaseRepository).save(captor.capture());
        MarketPurchase saved = captor.getValue();
        assertThat(saved.getChave()).isEqualTo(CHAVE_A);
        assertThat(saved.getEmitente()).isEqualTo("MERCADO X");
        assertThat(saved.getDate()).isEqualTo(DATE);
        assertThat(saved.getFileName()).isEqualTo("2603_produtos.xls");
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getItems().get(0).getNcm()).isEqualTo("10059010");
    }

    @Test
    @DisplayName("upload skips purchase when chave already exists in database")
    void upload_existingChave_skipsPurchase() {
        byte[] bytes = "file".getBytes();
        List<ParsedRow> rows = List.of(
            new ParsedRow(CHAVE_A, DATE, "MERCADO X", "12403", "10059010", "MILHO",
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN)
        );
        when(spreadsheetParser.parse(bytes)).thenReturn(rows);
        when(purchaseRepository.existsByChave(CHAVE_A)).thenReturn(true);

        InflationUploadResult result = service.upload(bytes, "file.xls");

        assertThat(result.purchasesCreated()).isEqualTo(0);
        assertThat(result.purchasesSkipped()).isEqualTo(1);
        assertThat(result.itemsImported()).isEqualTo(0);
        verify(purchaseRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("upload handles two different chaves in the same file")
    void upload_twoDifferentChaves_savesTwoPurchases() {
        byte[] bytes = "file".getBytes();
        List<ParsedRow> rows = List.of(
            new ParsedRow(CHAVE_A, DATE, "MERCADO X", "12403", "10059010", "MILHO",
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN),
            new ParsedRow(CHAVE_B, DATE, "MERCADO Y", "70087", "19059010", "PAO",
                BigDecimal.ONE, new BigDecimal("7.99"), new BigDecimal("7.99"))
        );
        when(spreadsheetParser.parse(bytes)).thenReturn(rows);
        when(purchaseRepository.existsByChave(CHAVE_A)).thenReturn(false);
        when(purchaseRepository.existsByChave(CHAVE_B)).thenReturn(false);
        when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InflationUploadResult result = service.upload(bytes, "file.xls");

        assertThat(result.purchasesCreated()).isEqualTo(2);
        assertThat(result.itemsImported()).isEqualTo(2);
        verify(purchaseRepository, times(2)).save(any());
    }
}
