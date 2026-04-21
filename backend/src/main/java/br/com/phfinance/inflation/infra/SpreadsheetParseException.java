package br.com.phfinance.inflation.infra;

public class SpreadsheetParseException extends RuntimeException {
    public SpreadsheetParseException(String message) {
        super(message);
    }
    public SpreadsheetParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
