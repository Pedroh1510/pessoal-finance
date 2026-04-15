package br.com.phfinance.finance.infra.parser;

import br.com.phfinance.finance.domain.RawTransaction;
import br.com.phfinance.finance.domain.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Banco Inter bank statement text into RawTransaction objects.
 *
 * <p>Inter PDF text structure:
 * <pre>
 * 18 de Março de 2026 Saldo do dia: R$ 10,71
 * Pix enviado: "Cp :00000000-MDSMP PAROQUIA CRISTO REDENTOR" -R$ 10,00 R$ 10,71
 * 22 de Março de 2026 Saldo do dia: -R$ 0,00
 * Pix recebido: "Cp :18236120-Pedro Henrique Martins da Silva" R$ 1.500,00 R$ 1.500,00
 * Pagamento efetuado: "Pagamento fatura cartao Inter" -R$ 1.978,10 -R$ 483,10
 * </pre>
 */
@Component
public class InterParser implements BankStatementParser {

    private static final Logger log = LoggerFactory.getLogger(InterParser.class);

    private static final Map<String, Integer> MONTH_MAP = Map.ofEntries(
            Map.entry("janeiro", 1), Map.entry("fevereiro", 2), Map.entry("março", 3),
            Map.entry("abril", 4), Map.entry("maio", 5), Map.entry("junho", 6),
            Map.entry("julho", 7), Map.entry("agosto", 8), Map.entry("setembro", 9),
            Map.entry("outubro", 10), Map.entry("novembro", 11), Map.entry("dezembro", 12)
    );

    /**
     * Matches a date line: "18 de Março de 2026 Saldo do dia: ..."
     * The first date line may be prefixed with "Valor Saldo por transação".
     * group 1 = day, group 2 = month name, group 3 = year
     */
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+(\\w+)\\s+de\\s+(\\d{4})\\s+Saldo do dia",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    /**
     * Matches a transaction line: type: "description" ±R$ amount R$ balance
     * group 1 = type prefix, group 2 = description in quotes, group 3 = sign+amount
     */
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
            "^([^:\"]+):\\s*\"([^\"]+)\"\\s+(-?)R\\$\\s+([\\d.]+,\\d{2})"
    );

    /**
     * Matches Inter "Cp" prefix in description: "Cp :XXXXXXXX-NAME"
     * group 1 = the human-readable name after the dash
     */
    private static final Pattern CP_PREFIX = Pattern.compile("^Cp\\s*:\\s*\\d+-(.+)$");

    @Override
    public List<RawTransaction> parse(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return List.of();
        }

        List<RawTransaction> transactions = new ArrayList<>();
        String[] lines = extractedText.split("\\r?\\n");

        LocalDate currentDate = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();

            Matcher dateMatcher = DATE_PATTERN.matcher(line);
            if (dateMatcher.find()) {
                currentDate = parseDate(dateMatcher);
                continue;
            }

            if (currentDate == null) {
                continue;
            }

            Matcher txMatcher = TRANSACTION_PATTERN.matcher(line);
            if (!txMatcher.find()) {
                continue;
            }

            try {
                String typeKeyword = txMatcher.group(1).trim();
                String description = txMatcher.group(2).trim();
                boolean negative = "-".equals(txMatcher.group(3));
                BigDecimal amount = parseAmount(txMatcher.group(4));

                TransactionType type = detectType(typeKeyword, negative);
                String recipient = extractRecipient(description);

                transactions.add(new RawTransaction(
                        LocalDateTime.of(currentDate, LocalTime.MIDNIGHT),
                        amount,
                        recipient,
                        description,
                        type,
                        line
                ));
            } catch (Exception e) {
                log.warn("InterParser: failed to parse line: {}", line, e);
            }
        }

        return List.copyOf(transactions);
    }

    private LocalDate parseDate(Matcher m) {
        int day = Integer.parseInt(m.group(1));
        String monthName = m.group(2).toLowerCase();
        Integer month = MONTH_MAP.get(monthName);
        if (month == null) {
            log.warn("InterParser: unknown month name: {}", monthName);
            return null;
        }
        int year = Integer.parseInt(m.group(3));
        return LocalDate.of(year, month, day);
    }

    private TransactionType detectType(String typeKeyword, boolean negative) {
        String lower = typeKeyword.toLowerCase();
        if (lower.contains("recebido") || lower.contains("recebida")) {
            return TransactionType.INCOME;
        }
        if (lower.contains("devolvido") || lower.contains("devolução")) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    /**
     * Extracts the human-readable recipient name from the description field.
     * Inter descriptions have format: "Cp :CNPJ-NAME"
     * We strip the "Cp :XXXXXXXX-" prefix to get just the name.
     */
    private String extractRecipient(String description) {
        // Format: "Cp :00000000-MDSMP PAROQUIA CRISTO REDENTOR" or "Cp :18236120-Pedro Henrique"
        Matcher m = CP_PREFIX.matcher(description);
        if (m.matches()) {
            return m.group(1).trim();
        }
        return description;
    }

    private BigDecimal parseAmount(String raw) {
        // Format: "1.500,00" → "1500.00"
        String normalized = raw.replace(".", "").replace(",", ".");
        return new BigDecimal(normalized);
    }
}
