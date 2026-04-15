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
class NeonParserTest {

    @Mock
    private Tesseract tesseract;

    private NeonParser parser;
    private PdfExtractor pdfExtractor;

    private static final Path NEON_PDF_1 = Path.of(
            "/home/pedro/www/ph/finance/exemplos/extratos/neon/Statement6391180046951199824747284281849d1b2df.pdf");
    private static final Path NEON_PDF_2 = Path.of(
            "/home/pedro/www/ph/finance/exemplos/extratos/neon/Statement639118004774443044474728428182e93f958.pdf");
    private static final Path NEON_PDF_3 = Path.of(
            "/home/pedro/www/ph/finance/exemplos/extratos/neon/Statement639118004825800713474728428184ee9f5b9.pdf");

    @BeforeEach
    void setUp() {
        parser = new NeonParser();
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
    @DisplayName("parse header-only text returns empty list")
    void parse_headerOnly_returnsEmpty() {
        String header = "Extrato por período\nCliente Agência bancária Conta\nDescrição Data Hora Valor Saldo Cartão";
        assertThat(parser.parse(header)).isEmpty();
    }

    // --- PIX expense ---

    @Test
    @DisplayName("parse PIX enviado para as EXPENSE")
    void parse_pixEnviado_isExpense() {
        String line = "PIX enviado para Jacir Inacio Da Silva   14/03/2026   17 23   R$ 30,00   R$ 1.230,65   -";

        List<RawTransaction> result = parser.parse(line);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(tx.recipient()).isEqualTo("Jacir Inacio Da Silva");
        assertThat(tx.date().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(tx.date().getHour()).isEqualTo(17);
        assertThat(tx.date().getMinute()).isEqualTo(23);
    }

    // --- PIX received ---

    @Test
    @DisplayName("parse PIX recebido de as INCOME")
    void parse_pixRecebido_isIncome() {
        String line = "PIX recebido de Pedro Henrique Martins da Silva   01/04/2026   07 14   R$ 1.500,00   R$ 1.692,88   -";

        List<RawTransaction> result = parser.parse(line);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.INCOME);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(tx.recipient()).isEqualTo("Pedro Henrique Martins da Silva");
    }

    // --- PIX devolvido ---

    @Test
    @DisplayName("parse PIX devolvido por as INCOME (refund)")
    void parse_pixDevolvido_isIncome() {
        String line = "PIX devolvido por ARCOS DOURADOS COMERCIO   17/03/2026   20 55 R$ 52,30   R$ 532,82   -";

        List<RawTransaction> result = parser.parse(line);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.INCOME);
        assertThat(tx.recipient()).isEqualTo("ARCOS DOURADOS COMERCIO");
    }

    // --- Generic expense (card purchase) ---

    @Test
    @DisplayName("parse card purchase as EXPENSE with description as recipient")
    void parse_cardPurchase_isExpenseWithDescriptionAsRecipient() {
        String line = "Oliveira SAO PAULO BR   14/03/2026   13 06   R$ 21,97   R$ 1.260,65   -";

        List<RawTransaction> result = parser.parse(line);

        assertThat(result).hasSize(1);
        RawTransaction tx = result.get(0);
        assertThat(tx.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.recipient()).isEqualTo("Oliveira SAO PAULO BR");
    }

    // --- Tarifa ---

    @Test
    @DisplayName("parse Tarifa Saque as EXPENSE")
    void parse_tarifaSaque_isExpense() {
        String line = "Tarifa Saque   23/03/2026   07 03   R$ 7,90   R$ 375,31   -";

        List<RawTransaction> result = parser.parse(line);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(result.get(0).amount()).isEqualByComparingTo(new BigDecimal("7.90"));
    }

    // --- Multiple rows ---

    @Test
    @DisplayName("parse multiple rows correctly")
    void parse_multipleRows_parsesAll() {
        String text = """
                PIX enviado para Jacir Inacio Da Silva   14/03/2026   17 23   R$ 30,00   R$ 1.230,65   -
                PIX recebido de Pedro Henrique Martins da Silva   01/04/2026   07 14   R$ 1.500,00   R$ 1.692,88   -
                Tarifa Saque   23/03/2026   07 03   R$ 7,90   R$ 375,31   -
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).hasSize(3);
    }

    // --- All amounts positive ---

    @Test
    @DisplayName("all parsed amounts are positive")
    void parse_allAmountsPositive() {
        String text = """
                PIX enviado para Jacir Inacio Da Silva   14/03/2026   17 23   R$ 30,00   R$ 1.230,65   -
                PIX recebido de Pedro Henrique Martins da Silva   01/04/2026   07 14   R$ 1.500,00   R$ 1.692,88   -
                """;

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx ->
                assertThat(tx.amount()).isGreaterThan(BigDecimal.ZERO)
        );
    }

    // --- Real PDF 1 ---

    @Test
    @DisplayName("parse real Neon PDF 1 returns non-empty list")
    void parse_realNeonPdf1_returnsNonEmptyList() throws IOException {
        assumeTrue(Files.exists(NEON_PDF_1), "Neon PDF 1 not found, skipping");
        byte[] bytes = Files.readAllBytes(NEON_PDF_1);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("real Neon PDF 1: all amounts positive")
    void parse_realNeonPdf1_allAmountsPositive() throws IOException {
        assumeTrue(Files.exists(NEON_PDF_1), "Neon PDF 1 not found, skipping");
        byte[] bytes = Files.readAllBytes(NEON_PDF_1);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx ->
                assertThat(tx.amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO)
        );
    }

    @Test
    @DisplayName("real Neon PDF 1: all dates in valid range")
    void parse_realNeonPdf1_validDates() throws IOException {
        assumeTrue(Files.exists(NEON_PDF_1), "Neon PDF 1 not found, skipping");
        byte[] bytes = Files.readAllBytes(NEON_PDF_1);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).allSatisfy(tx -> {
            int year = tx.date().getYear();
            assertThat(year).isBetween(2024, 2027);
        });
    }

    @Test
    @DisplayName("real Neon PDF 1: contains at least one EXPENSE")
    void parse_realNeonPdf1_containsExpenses() throws IOException {
        assumeTrue(Files.exists(NEON_PDF_1), "Neon PDF 1 not found, skipping");
        byte[] bytes = Files.readAllBytes(NEON_PDF_1);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).anyMatch(tx -> tx.type() == TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("real Neon PDF 1: contains at least one INCOME")
    void parse_realNeonPdf1_containsIncome() throws IOException {
        assumeTrue(Files.exists(NEON_PDF_1), "Neon PDF 1 not found, skipping");
        byte[] bytes = Files.readAllBytes(NEON_PDF_1);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).anyMatch(tx -> tx.type() == TransactionType.INCOME);
    }

    @Test
    @DisplayName("real Neon PDF 1: all transaction types are valid")
    void parse_realNeonPdf1_allTypesValid() throws IOException {
        assumeTrue(Files.exists(NEON_PDF_1), "Neon PDF 1 not found, skipping");
        byte[] bytes = Files.readAllBytes(NEON_PDF_1);
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

    // --- Real PDF 2 ---

    @Test
    @DisplayName("parse real Neon PDF 2 returns non-empty list")
    void parse_realNeonPdf2_returnsNonEmptyList() throws IOException {
        assumeTrue(Files.exists(NEON_PDF_2), "Neon PDF 2 not found, skipping");
        byte[] bytes = Files.readAllBytes(NEON_PDF_2);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).isNotEmpty();
    }

    // --- Real PDF 3 ---

    @Test
    @DisplayName("parse real Neon PDF 3 returns non-empty list")
    void parse_realNeonPdf3_returnsNonEmptyList() throws IOException {
        assumeTrue(Files.exists(NEON_PDF_3), "Neon PDF 3 not found, skipping");
        byte[] bytes = Files.readAllBytes(NEON_PDF_3);
        String text = pdfExtractor.extractText(bytes);

        List<RawTransaction> result = parser.parse(text);

        assertThat(result).isNotEmpty();
    }
}
