package br.com.phfinance.inflation.infra;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class SpreadsheetParser {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<ParsedRow> parse(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerIndex = findHeaderRowIndex(sheet);
            if (headerIndex < 0) {
                throw new SpreadsheetParseException("No header row with ncm column found");
            }
            Map<String, Integer> columns = mapColumns(sheet.getRow(headerIndex));
            validateRequiredColumns(columns);

            List<ParsedRow> rows = new ArrayList<>();
            for (int i = headerIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;
                ParsedRow parsed = parseRow(row, columns);
                if (parsed != null) rows.add(parsed);
            }
            return rows;
        } catch (SpreadsheetParseException e) {
            throw e;
        } catch (IOException e) {
            throw new SpreadsheetParseException("Failed to parse spreadsheet file", e);
        } catch (Exception e) {
            throw new SpreadsheetParseException("Unexpected error: " + e.getMessage(), e);
        }
    }

    private int findHeaderRowIndex(Sheet sheet) {
        int lastRow = Math.min(sheet.getLastRowNum(), 10);
        for (int i = 0; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row != null && rowHasNcmCell(row)) return i;
        }
        return -1;
    }

    private boolean rowHasNcmCell(Row row) {
        for (Cell cell : row) {
            if (normalize(cellString(cell)).equals("ncm")) return true;
        }
        return false;
    }

    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> cols = new HashMap<>();
        for (Cell cell : headerRow) {
            String h = normalize(cellString(cell));
            int idx = cell.getColumnIndex();
            if (h.equals("ncm")) {
                cols.put("ncm", idx);
            } else if (h.startsWith("descri") || h.equals("produto")) {
                cols.put("description", idx);
            } else if (h.startsWith("quant") || h.equals("qtd") || h.equals("qtde")) {
                cols.put("quantity", idx);
            } else if (h.equals("data")) {
                cols.put("date", idx);
            } else if (h.equals("emitente")) {
                cols.put("emitente", idx);
            } else if (h.equals("chave")) {
                cols.put("chave", idx);
            } else if (h.startsWith("cod")) {
                cols.put("productCode", idx);
            } else if (h.contains("unitario") || (h.contains("unit") && h.contains("valor"))
                    || (h.contains("unit") && h.contains("vlr"))) {
                cols.put("unitPrice", idx);
            } else if (h.contains("total") && !h.contains("unit")) {
                cols.put("totalPrice", idx);
            }
        }
        return cols;
    }

    private void validateRequiredColumns(Map<String, Integer> cols) {
        for (String required : List.of("ncm", "description", "unitPrice", "chave", "emitente", "date")) {
            if (!cols.containsKey(required)) {
                throw new SpreadsheetParseException(
                    "Required column '" + required + "' not found in header row");
            }
        }
    }

    private ParsedRow parseRow(Row row, Map<String, Integer> cols) {
        String ncm = normalize(cellString(row, cols.get("ncm")));
        if (ncm.isBlank()) return null;

        String chave = cellString(row, cols.get("chave")).replaceAll("\\s", "");
        if (chave.isBlank()) return null;

        LocalDate date = parseDate(row, cols.get("date"));
        if (date == null) return null;

        String emitente = cellString(row, cols.get("emitente"));
        String description = cellString(row, cols.get("description"));
        String productCode = cols.containsKey("productCode")
            ? cellString(row, cols.get("productCode")) : "";

        BigDecimal quantity = cols.containsKey("quantity")
            ? cellDecimal(row, cols.get("quantity")) : BigDecimal.ONE;
        BigDecimal unitPrice = cellDecimal(row, cols.get("unitPrice"));
        BigDecimal totalPrice = cols.containsKey("totalPrice")
            ? cellDecimal(row, cols.get("totalPrice"))
            : quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

        return new ParsedRow(chave, date, emitente, productCode, ncm, description,
            quantity, unitPrice, totalPrice);
    }

    private LocalDate parseDate(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String s = cellString(cell);
        try {
            return LocalDate.parse(s, BR_DATE);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private String cellString(Row row, Integer colIndex) {
        if (colIndex == null || colIndex < 0) return "";
        return cellString(row.getCell(colIndex));
    }

    private String cellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) && !Double.isInfinite(d)
                    ? String.valueOf((long) d)
                    : String.valueOf(d);
            }
            case FORMULA -> {
                if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
                yield cell.getRichStringCellValue().getString().trim();
            }
            default -> "";
        };
    }

    private BigDecimal cellDecimal(Row row, Integer colIndex) {
        if (colIndex == null || colIndex < 0) return BigDecimal.ZERO;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return BigDecimal.ZERO;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue())
                .setScale(4, RoundingMode.HALF_UP);
            case STRING -> {
                String s = cell.getStringCellValue().replace(",", ".").replaceAll("[^0-9.]", "");
                yield s.isBlank() ? BigDecimal.ZERO
                    : new BigDecimal(s).setScale(4, RoundingMode.HALF_UP);
            }
            default -> BigDecimal.ZERO;
        };
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !cellString(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("[^\\p{ASCII}]", "")
            .toLowerCase()
            .trim();
    }
}
