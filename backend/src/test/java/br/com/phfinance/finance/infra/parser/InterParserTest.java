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
class InterParserTest {

    @Mock
    private Tesseract tesseract;

    private InterParser parser;
    private PdfExtractor pdfExtractor;

    private static final Path INTER_PDF = Path.of(
            "/home/pedro/www/ph/finance/exemplos/extratos/inter/Extrato-14-03-2026-a-14-04-2026-PDF.pdf");

    @BeforeEach
    void setUp() {
        parser = new InterParser();
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
    @DisplayName("parse text with no transactions returns empty list")
    void parse_noTransactions_returnsEmpty() {
        String text = "Banco Inter\nSolicitado em: 14/04/2026\nSaldo total R$ 6,90";
        assertThat(parser.parse(text)).isEmpty();
    }

    // --- Pix enviado (expense) ---

    @Test
    @DisplayName("parse Pix enviado as EXPENSE")
    void parse_pixEnviado_isExpense() {
        String text = """
                18 de Março de 2026 Saldo do dia: R$ 10,71
                Pix enviado: "Cp :00000000-MDSMP PAROQUIA CRISTO REDENTOR" -R$ 10,00 R$ 10,71
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(tx.date().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 18));
        assertThat(tx.recipient()).contains("PAROQUIA CRISTO REDENTOR");
    }

    // --- Pix recebido (income) ---

    @Test
    @DisplayName("parse Pix recebido as INCOME")
    void parse_pixRecebido_isIncome() {
        String text = """
                1 de Abril de 2026 Saldo do dia: R$ 1.500,00
                Pix recebido: "Cp :18236120-Pedro Henrique Martins da Silva" R$ 1.500,00 R$ 1.500,00
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.INCOME);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(tx.recipient()).contains("Pedro Henrique Martins da Silva");
        assertThat(tx.date().toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    // --- Pagamento efetuado (expense) ---

    @Test
    @DisplayName("parse Pagamento efetuado as EXPENSE")
    void parse_pagamentoEfetuado_isExpense() {
        String text = """
                7 de Abril de 2026 Saldo do dia: R$ 16,90
                Pagamento efetuado: "Pagamento fatura cartao Inter" -R$ 1.978,10 -R$ 483,10
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("1978.10"));
    }

    // --- Multiple transactions same day ---

    @Test
    @DisplayName("parse multiple transactions on same day")
    void parse_multipleTransactionsSameDay_parsesAll() {
        String text = """
                7 de Abril de 2026 Saldo do dia: R$ 16,90
                Pagamento efetuado: "Pagamento fatura cartao Inter" -R$ 1.978,10 -R$ 483,10
                Pix recebido: "Cp :18236120-Pedro Henrique Martins da Silva" R$ 500,00 R$ 16,90
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(result.get(1).type()).isEqualTo(TransactionType.INCOME);
    }

    // --- Date changes across entries ---

    @Test
    @DisplayName("parse tracks date changes correctly")
    void parse_dateChanges_usesCorrectDate() {
        String text = """
                18 de Março de 2026 Saldo do dia: R$ 10,71
                Pix enviado: "Cp :00000000-MDSMP PAROQUIA CRISTO REDENTOR" -R$ 10,00 R$ 10,71
                1 de Abril de 2026 Saldo do dia: R$ 1.500,00
                Pix recebido: "Cp :18236120-Pedro Henrique Martins da Silva" R$ 1.500,00 R$ 1.500,00
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date().getMonthValue()).isEqualTo(3);
        assertThat(result.get(1).date().getMonthValue()).isEqualTo(4);
    }

    // --- All amounts positive ---

    @Test
    @DisplayName("all parsed amounts are positive")
    void parse_allAmountsPositive() {
        String text = """
                18 de Março de 2026 Saldo do dia: R$ 10,71
                Pix enviado: "Cp :00000000-MDSMP PAROQUIA CRISTO REDENTOR" -R$ 10,00 R$ 10,71
                1 de Abril de 2026 Saldo do dia: R$ 1.500,00
                Pix recebido: "Cp :18236120-Pedro Henrique Martins da Silva" R$ 1.500,00 R$ 1.500,00
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx ->
                assertThat(tx.amount()).isGreaterThan(BigDecimal.ZERO)
        );
    }

    // --- Unknown month in date line ---

    @Test
    @DisplayName("parse unknown month in date line skips transactions")
    void parse_unknownMonthInDateLine_skipsTransactions() {
        // Date line matches the DATE_PATTERN regex but uses an unrecognized month name,
        // so parseDate() returns null and the transaction block is skipped.
        String text = "18 de Inexistentembro de 2026 Saldo do dia: R$ 1.000,00\n" +
                      "Pix recebido: \"Cp :18236120-Pedro Henrique\" R$ 500,00 R$ 1.500,00";

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).isEmpty();
    }

    // --- Bad month mid-file (after valid transactions) ---

    @Test
    @DisplayName("parse unknown month after valid date returns transactions before bad date, skips after")
    void parse_unknownMonthAfterValidDate_returnsTransactionsBeforeBadDate() {
        // Valid date line → transaction (should be returned)
        // Bad month date line (DATE_PATTERN matches but month is unrecognised) → currentDate becomes null
        // Transaction after bad date line → should be skipped
        String text = """
                18 de Março de 2026 Saldo do dia: R$ 10,71
                Pix enviado: "Cp :00000000-MDSMP PAROQUIA CRISTO REDENTOR" -R$ 10,00 R$ 10,71
                5 de Inexistentembro de 2026 Saldo do dia: R$ 0,00
                Pix recebido: "Cp :18236120-Pedro Henrique Martins da Silva" R$ 500,00 R$ 500,00
                """;

        List<RawTransaction> result = parser.parse(text);

        // The valid transaction before the bad date must be present
        assertThat(result).hasSizeGreaterThanOrEqualTo(1);
        // The first (and only valid) transaction must be from the good date
        assertThat(result.get(0).date().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 18));
        // The transaction after the bad date line must NOT appear (total should be exactly 1)
        assertThat(result).hasSize(1);
    }

    // --- Real PDF ---

    @Test
    @DisplayName("parse real Inter PDF returns non-empty list")
    void parse_realInterPdf_returnsNonEmptyList() throws IOException {
        assumeTrue(Files.exists(INTER_PDF), "Inter PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(INTER_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("real Inter PDF: all amounts positive")
    void parse_realInterPdf_allAmountsPositive() throws IOException {
        assumeTrue(Files.exists(INTER_PDF), "Inter PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(INTER_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx ->
                assertThat(tx.amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO)
        );
    }

    @Test
    @DisplayName("real Inter PDF: all dates in valid range")
    void parse_realInterPdf_validDates() throws IOException {
        assumeTrue(Files.exists(INTER_PDF), "Inter PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(INTER_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx -> {
            int year = tx.date().getYear();
            assertThat(year).isBetween(2024, 2027);
        });
    }

    @Test
    @DisplayName("real Inter PDF: contains at least one EXPENSE")
    void parse_realInterPdf_containsExpenses() throws IOException {
        assumeTrue(Files.exists(INTER_PDF), "Inter PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(INTER_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).anyMatch(tx -> tx.type() == TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("real Inter PDF: contains at least one INCOME")
    void parse_realInterPdf_containsIncome() throws IOException {
        assumeTrue(Files.exists(INTER_PDF), "Inter PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(INTER_PDF);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).anyMatch(tx -> tx.type() == TransactionType.INCOME);
    }

    @Test
    @DisplayName("real Inter PDF: all transaction types are valid")
    void parse_realInterPdf_allTypesValid() throws IOException {
        assumeTrue(Files.exists(INTER_PDF), "Inter PDF not found, skipping");
        byte[] bytes = Files.readAllBytes(INTER_PDF);
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
}
