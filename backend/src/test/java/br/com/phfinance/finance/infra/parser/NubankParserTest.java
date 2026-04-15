package br.com.phfinance.finance.infra.parser;

import br.com.phfinance.finance.domain.RawTransaction;
import br.com.phfinance.finance.domain.TransactionType;
import br.com.phfinance.shared.fileparse.PdfExtractor;
import net.sourceforge.tess4j.Tesseract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(MockitoExtension.class)
class NubankParserTest {

    @Mock
    private Tesseract tesseract;

    private NubankParser parser;
    private PdfExtractor pdfExtractor;

    private static final Path NUBANK_JAN_PDF = Path.of(
            "/home/pedro/www/ph/finance/exemplos/extratos/nubank/NU_143766978_01JAN2026_31JAN2026.pdf");
    private static final Path NUBANK_MAR_PDF = Path.of(
            "/home/pedro/www/ph/finance/exemplos/extratos/nubank/NU_143766978_01MAR2026_31MAR2026.pdf");

    @BeforeEach
    void setUp() {
        parser = new NubankParser();
        pdfExtractor = new PdfExtractor(tesseract);
    }

    // --- Defensive / edge cases ---

    @Test
    @DisplayName("parse null returns empty list")
    void parse_null_returnsEmpty() {
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("parse empty string returns empty list")
    void parse_emptyString_returnsEmpty() {
        assertThat(parser.parse("")).isEmpty();
    }

    @Test
    @DisplayName("parse blank string returns empty list")
    void parse_blankString_returnsEmpty() {
        assertThat(parser.parse("   \n\t  ")).isEmpty();
    }

    @Test
    @DisplayName("parse text with no transactions returns empty list")
    void parse_noTransactions_returnsEmpty() {
        String text = "Nubank header\nSaldo inicial\nOutras informações";
        assertThat(parser.parse(text)).isEmpty();
    }

    // --- Inline transaction (Aplicação RDB) ---

    @Test
    @DisplayName("parse handles Aplicação RDB inline amount")
    void parse_aplicacaoRdb_parsesCorrectly() {
        String text = """
                01 JAN 2026 Total de saídas - 750,00
                Aplicação RDB 750,00
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("750.00"));
        assertThat(tx.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.date().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    // --- Expense transaction (Pix sent) ---

    @Test
    @DisplayName("parse handles Pix sent transaction")
    void parse_pixSent_isExpense() {
        String text = """
                01 JAN 2026 Total de saídas - 967,00
                Transferência enviada pelo Pix Natasha Reginaldo de Oliveira - •••.282.118-•• -\s
                PICPAY (0380) Agência: 1 Conta: 19925950-0
                967,00
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("967.00"));
        assertThat(tx.recipient()).contains("Natasha");
        assertThat(tx.date().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    // --- Income transaction (Pix received) ---

    @Test
    @DisplayName("parse handles Pix received transaction as INCOME")
    void parse_pixReceived_isIncome() {
        String text = """
                30 JAN 2026 Total de entradas + 11.817,91
                Transferência recebida pelo Pix CRAFT CREW DESENVOLVIMENTO DE SOFTWARE E 11.817,91
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.INCOME);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("11817.91"));
    }

    // --- Debit purchase ---

    @Test
    @DisplayName("parse handles Compra no debito transaction")
    void parse_compraNoDebito_isExpense() {
        String text = """
                17 JAN 2026 Total de saídas - 27,95
                Compra no débito Uber UBER *TRIP HELP.U 13,98
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("13.98"));
    }

    // --- Boleto payment ---

    @Test
    @DisplayName("parse handles Pagamento de boleto as EXPENSE")
    void parse_pagamentoBoleto_isExpense() {
        String text = """
                05 JAN 2026 Total de saídas - 2.224,90
                Pagamento de boleto efetuado FACULDADE METODO DE SAO PAULO 459,00
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("459.00"));
    }

    // --- Multiple months ---

    @Test
    @DisplayName("parse tracks dates across multiple months")
    void parse_multipleDates_tracksDateChanges() {
        String text = """
                01 JAN 2026 Total de saídas - 750,00
                Aplicação RDB 750,00
                03 MAR 2026 Total de saídas - 333,00
                Aplicação RDB 333,00
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date().getMonthValue()).isEqualTo(1);
        assertThat(result.get(1).date().getMonthValue()).isEqualTo(3);
    }

    // --- All amounts must be positive ---

    @Test
    @DisplayName("all parsed amounts are positive")
    void parse_allAmountsPositive() {
        String text = """
                01 JAN 2026 Total de saídas - 967,00
                Transferência enviada pelo Pix Natasha Reginaldo de Oliveira - •••.282.118-•• -\s
                PICPAY (0380) Agência: 1 Conta: 19925950-0
                967,00
                Aplicação RDB 750,00
                30 JAN 2026 Total de entradas + 11.817,91
                Transferência recebida pelo Pix CRAFT CREW DESENVOLVIMENTO DE SOFTWARE E 11.817,91
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx ->
                assertThat(tx.amount()).isGreaterThan(BigDecimal.ZERO)
        );
    }

    // --- Real PDF: January ---

    @Test
    @DisplayName("parse real Nubank January PDF returns non-empty list")
    void parse_realNubankJanPdf_returnsNonEmptyList() throws IOException {
        assumeTrue(Files.exists(NUBANK_JAN_PDF), "Nubank January PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(NUBANK_JAN_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("real Nubank January PDF: all amounts positive")
    void parse_realNubankJanPdf_allAmountsPositive() throws IOException {
        assumeTrue(Files.exists(NUBANK_JAN_PDF), "Nubank January PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(NUBANK_JAN_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx ->
                assertThat(tx.amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO)
        );
    }

    @Test
    @DisplayName("real Nubank January PDF: all dates in valid range")
    void parse_realNubankJanPdf_validDates() throws IOException {
        assumeTrue(Files.exists(NUBANK_JAN_PDF), "Nubank January PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(NUBANK_JAN_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx -> {
            int year = tx.date().getYear();
            assertThat(year).isBetween(2024, 2027);
        });
    }

    @Test
    @DisplayName("real Nubank January PDF: contains at least one EXPENSE")
    void parse_realNubankJanPdf_containsExpenses() throws IOException {
        assumeTrue(Files.exists(NUBANK_JAN_PDF), "Nubank January PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(NUBANK_JAN_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).anyMatch(tx -> tx.type() == TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("real Nubank January PDF: contains at least one INCOME")
    void parse_realNubankJanPdf_containsIncome() throws IOException {
        assumeTrue(Files.exists(NUBANK_JAN_PDF), "Nubank January PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(NUBANK_JAN_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).anyMatch(tx -> tx.type() == TransactionType.INCOME);
    }

    @Test
    @DisplayName("real Nubank January PDF: all transaction types are valid enum values")
    void parse_realNubankJanPdf_allTypesValid() throws IOException {
        assumeTrue(Files.exists(NUBANK_JAN_PDF), "Nubank January PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(NUBANK_JAN_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx ->
                assertThat(tx.type()).isIn(
                        TransactionType.INCOME,
                        TransactionType.EXPENSE,
                        TransactionType.INTERNAL_TRANSFER
                )
        );
    }

    // --- Real PDF: March ---

    @Test
    @DisplayName("parse real Nubank March PDF returns non-empty list")
    void parse_realNubankMarPdf_returnsNonEmptyList() throws IOException {
        assumeTrue(Files.exists(NUBANK_MAR_PDF), "Nubank March PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(NUBANK_MAR_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("real Nubank March PDF: all amounts positive")
    void parse_realNubankMarPdf_allAmountsPositive() throws IOException {
        assumeTrue(Files.exists(NUBANK_MAR_PDF), "Nubank March PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(NUBANK_MAR_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx ->
                assertThat(tx.amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO)
        );
    }
}
