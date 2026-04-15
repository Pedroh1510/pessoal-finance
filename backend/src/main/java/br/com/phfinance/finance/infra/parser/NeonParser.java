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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Neon bank statement text into RawTransaction objects.
 *
 * <p>Neon PDF text structure (table rows):
 * <pre>
 * Oliveira SAO PAULO BR   14/03/2026   13 06   R$ 21,97   R$ 1.260,65   -
 * PIX enviado para Jacir Inacio Da Silva   14/03/2026   17 23   R$ 30,00   R$ 1.230,65   -
 * PIX recebido de Pedro Henrique Martins da Silva   01/04/2026   07 14   R$ 1.500,00   R$ 1.692,88   -
 * PIX devolvido por ARCOS DOURADOS COMERCIO   17/03/2026   20 55   R$ 52,30   R$ 532,82   -
 * Tarifa Saque   23/03/2026   07 03   R$ 7,90   R$ 375,31   -
 * </pre>
 */
@Component
public class NeonParser implements BankStatementParser {

    private static final Logger log = LoggerFactory.getLogger(NeonParser.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Matches a Neon table row.
     *
     * <p>In the real PDF, fields are separated by single spaces and/or null bytes (char 0x00).
     * Format: description DATE HH\x00MM \x00R$ amount R$ balance [-|+]
     * group 1 = description, group 2 = date, group 3 = hour, group 4 = minute, group 5 = amount
     */
    private static final Pattern ROW_PATTERN = Pattern.compile(
            "^(.+?)\\s+(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2})[\\s\u0000]+(\\d{2})[\\s\u0000]+R\\$\\s+([\\d.]+,\\d{2})\\s+R\\$\\s+[\\d.,]"
    );

    @Override
    public List<RawTransaction> parse(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return List.of();
        }

        List<RawTransaction> transactions = new ArrayList<>();
        String[] lines = extractedText.split("\\r?\\n");

        for (String line : lines) {
            Matcher m = ROW_PATTERN.matcher(line.trim());
            if (!m.find()) {
                continue;
            }

            try {
                String description = m.group(1).trim();
                LocalDate date = LocalDate.parse(m.group(2), DATE_FORMATTER);
                int hour = Integer.parseInt(m.group(3));
                int minute = Integer.parseInt(m.group(4));
                BigDecimal amount = parseAmount(m.group(5));
                TransactionType type = detectType(description);
                String recipient = extractRecipient(description);

                transactions.add(new RawTransaction(
                        LocalDateTime.of(date, LocalTime.of(hour, minute)),
                        amount,
                        recipient,
                        description,
                        type,
                        line.trim()
                ));
            } catch (Exception e) {
                log.warn("NeonParser: failed to parse line: {}", line, e);
            }
        }

        return List.copyOf(transactions);
    }

    private TransactionType detectType(String description) {
        if (description.startsWith("PIX recebido de")) return TransactionType.INCOME;
        if (description.startsWith("PIX devolvido por")) return TransactionType.INCOME;
        return TransactionType.EXPENSE;
    }

    /**
     * Extracts recipient name from description.
     * "PIX enviado para X" → "X"
     * "PIX recebido de X" → "X"
     * "PIX devolvido por X" → "X"
     * else → description as-is
     */
    private String extractRecipient(String description) {
        if (description.startsWith("PIX enviado para ")) {
            return description.substring("PIX enviado para ".length()).trim();
        }
        if (description.startsWith("PIX recebido de ")) {
            return description.substring("PIX recebido de ".length()).trim();
        }
        if (description.startsWith("PIX devolvido por ")) {
            return description.substring("PIX devolvido por ".length()).trim();
        }
        return description;
    }

    private BigDecimal parseAmount(String raw) {
        // Format: "1.260,65" → "1260.65"
        String normalized = raw.replace(".", "").replace(",", ".");
        return new BigDecimal(normalized);
    }
}
