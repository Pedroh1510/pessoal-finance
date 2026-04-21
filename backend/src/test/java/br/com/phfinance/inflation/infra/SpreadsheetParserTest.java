package br.com.phfinance.inflation.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpreadsheetParserTest {

    private SpreadsheetParser parser;

    private static final String CHAVE_A = "35260348564323000134650290000032581020295871";
    private static final String CHAVE_B = "35260348564323000134650290000032581020295872";

    @BeforeEach
    void setUp() {
        parser = new SpreadsheetParser();
    }

    @Test
    @DisplayName("parse returns rows from valid spreadsheet")
    void parse_validSpreadsheet_returnsRows() throws Exception {
        byte[] xlsx = buildXlsx(new Object[][]{
            {"Data", "Código", "Descrição", "NCM", "Quantidade", "Valor Unitário", "Valor Total Item", "Emitente", "Chave"},
            {"29/03/2026", "12403", "MILHO PARA PIPOCA 400G", "10059010", 1.5, 8.90, 13.35, "MERCADO X LTDA", CHAVE_A},
            {"29/03/2026", "70087", "PAO DE FORMA 400G",      "19059010", 1.0, 7.99,  7.99, "MERCADO X LTDA", CHAVE_A},
        });

        var rows = parser.parse(xlsx);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).ncm()).isEqualTo("10059010");
        assertThat(rows.get(0).description()).isEqualTo("MILHO PARA PIPOCA 400G");
        assertThat(rows.get(0).quantity()).isEqualByComparingTo("1.5");
        assertThat(rows.get(0).unitPrice()).isEqualByComparingTo("8.90");
        assertThat(rows.get(0).totalPrice()).isEqualByComparingTo("13.35");
        assertThat(rows.get(0).emitente()).isEqualTo("MERCADO X LTDA");
        assertThat(rows.get(0).chave()).isEqualTo(CHAVE_A);
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 29));
        assertThat(rows.get(1).ncm()).isEqualTo("19059010");
    }

    @Test
    @DisplayName("parse ignores empty rows")
    void parse_withEmptyRows_skipsBlankRows() throws Exception {
        byte[] xlsx = buildXlsx(new Object[][]{
            {"Data", "Código", "Descrição", "NCM", "Quantidade", "Valor Unitário", "Valor Total Item", "Emitente", "Chave"},
            {"29/03/2026", "12403", "MILHO 400G", "10059010", 1.0, 8.90, 8.90, "MERCADO X LTDA", CHAVE_A},
            {},
            {"29/03/2026", "70087", "PAO 400G",   "19059010", 1.0, 7.99, 7.99, "MERCADO X LTDA", CHAVE_B},
        });

        var rows = parser.parse(xlsx);
        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("parse header detection is case-insensitive and accent-agnostic")
    void parse_caseInsensitiveHeader_works() throws Exception {
        byte[] xlsx = buildXlsx(new Object[][]{
            {"DATA", "CODIGO", "DESCRICAO", "NCM", "QUANTIDADE", "VALOR UNITARIO", "VALOR TOTAL ITEM", "EMITENTE", "CHAVE"},
            {"29/03/2026", "12403", "MILHO 400G", "10059010", 1.0, 8.90, 8.90, "MERCADO X LTDA", CHAVE_A},
        });

        var rows = parser.parse(xlsx);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).ncm()).isEqualTo("10059010");
        assertThat(rows.get(0).unitPrice()).isEqualByComparingTo("8.90");
    }

    @Test
    @DisplayName("parse throws when NCM column is missing")
    void parse_missingNcmColumn_throwsException() throws Exception {
        byte[] xlsx = buildXlsx(new Object[][]{
            {"Data", "Descrição", "Quantidade"},
            {"29/03/2026", "MILHO 400G", 1.0},
        });

        assertThatThrownBy(() -> parser.parse(xlsx))
            .isInstanceOf(SpreadsheetParseException.class)
            .hasMessageContaining("ncm");
    }

    @Test
    @DisplayName("parse throws on invalid bytes")
    void parse_invalidBytes_throwsException() {
        assertThatThrownBy(() -> parser.parse("not-a-spreadsheet".getBytes()))
            .isInstanceOf(SpreadsheetParseException.class);
    }

    @Test
    @DisplayName("parse calculates totalPrice from quantity x unitPrice when column is absent")
    void parse_missingTotalPriceColumn_calculatesTotalPrice() throws Exception {
        byte[] xlsx = buildXlsx(new Object[][]{
            {"Data", "Código", "Descrição", "NCM", "Quantidade", "Valor Unitário", "Emitente", "Chave"},
            {"29/03/2026", "12403", "MILHO 400G", "10059010", 2.0, 8.90, "MERCADO X LTDA", CHAVE_A},
        });

        var rows = parser.parse(xlsx);
        assertThat(rows.get(0).totalPrice()).isEqualByComparingTo("17.80");
    }

    @Test
    @DisplayName("parse skips rows with blank chave")
    void parse_blankChave_skipsRow() throws Exception {
        byte[] xlsx = buildXlsx(new Object[][]{
            {"Data", "Código", "Descrição", "NCM", "Quantidade", "Valor Unitário", "Valor Total Item", "Emitente", "Chave"},
            {"29/03/2026", "12403", "MILHO 400G", "10059010", 1.0, 8.90, 8.90, "MERCADO X LTDA", ""},
        });

        var rows = parser.parse(xlsx);
        assertThat(rows).isEmpty();
    }

    private byte[] buildXlsx(Object[][] data) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Produtos");
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < data[r].length; c++) {
                    Cell cell = row.createCell(c);
                    Object val = data[r][c];
                    if (val instanceof String s) cell.setCellValue(s);
                    else if (val instanceof Double d) cell.setCellValue(d);
                    else if (val instanceof BigDecimal bd) cell.setCellValue(bd.doubleValue());
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
