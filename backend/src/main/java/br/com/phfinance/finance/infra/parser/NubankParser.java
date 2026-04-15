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
 * Parses Nubank bank statement text into RawTransaction objects.
 *
 * <p>Nubank PDF text structure:
 * <pre>
 * 01 JAN 2026 Total de saídas - 8.217,00
 * Transferência enviada pelo Pix Natasha Reginaldo de Oliveira - •••.282.118-•• -
 * PICPAY (0380) Agência: 1 Conta: 19925950-0
 * 967,00
 * Transferência enviada pelo Pix Pedro Henrique Martins Da Silva - •••.728.428-•• -
 * NEON PAGAMENTOS S.A. IP (0536) Agência: 655
 * Conta: 7049008-2
 * 1.500,00
 * Aplicação RDB 750,00
 * </pre>
 */
@Component
public class NubankParser implements BankStatementParser {

    private static final Logger log = LoggerFactory.getLogger(NubankParser.class);

    private static final Map<String, Integer> MONTH_MAP = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEV", 2), Map.entry("MAR", 3),
            Map.entry("ABR", 4), Map.entry("MAI", 5), Map.entry("JUN", 6),
            Map.entry("JUL", 7), Map.entry("AGO", 8), Map.entry("SET", 9),
            Map.entry("OUT", 10), Map.entry("NOV", 11), Map.entry("DEZ", 12)
    );

    /** Matches: "01 JAN 2026" at the start of a line (possibly followed by "Total de ..." summary) */
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "^(\\d{2})\\s+(JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)\\s+(\\d{4})"
    );

    /** Matches a monetary amount line: e.g. "967,00" or "11.817,91" */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "^\\d{1,3}(?:\\.\\d{3})*,\\d{2}$"
    );

    /** CPF/CNPJ suffix to strip from recipient names */
    private static final Pattern CPF_SUFFIX = Pattern.compile(
            "\\s*-\\s*[•\\d]+[.\\d•\\-]*[•\\d]+\\s*-?\\s*$"
    );

    @Override
    public List<RawTransaction> parse(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return List.of();
        }

        List<RawTransaction> transactions = new ArrayList<>();
        String[] lines = extractedText.split("\\r?\\n");

        LocalDate currentDate = null;

        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();

            // Check for new date line
            Matcher dateMatcher = DATE_PATTERN.matcher(line);
            if (dateMatcher.find()) {
                currentDate = parseDate(dateMatcher);
                i++;
                continue;
            }

            // Skip non-transaction lines when no date context yet
            if (currentDate == null) {
                i++;
                continue;
            }

            // Skip summary aggregation lines
            if (line.startsWith("Total de saídas") || line.startsWith("Total de entradas")) {
                i++;
                continue;
            }

            // Detect transaction type keyword
            TransactionType type = detectType(line);
            if (type == null) {
                i++;
                continue;
            }

            // Extract recipient from the keyword line (text after the keyword phrase)
            String recipient = extractRecipient(line, type);

            // If the line contains an amount already (e.g. "Aplicação RDB 750,00"), extract it here
            BigDecimal inlineAmount = extractInlineAmount(line);
            if (inlineAmount != null) {
                String rawText = line;
                transactions.add(new RawTransaction(
                        LocalDateTime.of(currentDate, LocalTime.MIDNIGHT),
                        inlineAmount,
                        recipient.isBlank() ? line : recipient,
                        line,
                        type,
                        rawText
                ));
                i++;
                continue;
            }

            // Collect continuation lines (bank details, account numbers) and then the amount line
            StringBuilder rawBuilder = new StringBuilder(line);
            int j = i + 1;
            BigDecimal amount = null;

            while (j < lines.length) {
                String nextLine = lines[j].trim();

                if (nextLine.isBlank()) {
                    j++;
                    continue;
                }

                // Stop if we hit a new date or a new transaction type
                if (DATE_PATTERN.matcher(nextLine).find()) {
                    break;
                }
                if (detectType(nextLine) != null) {
                    break;
                }
                if (nextLine.startsWith("Total de saídas") || nextLine.startsWith("Total de entradas")) {
                    break;
                }

                // Check if this is the amount line
                if (AMOUNT_PATTERN.matcher(nextLine).matches()) {
                    amount = parseAmount(nextLine);
                    rawBuilder.append("\n").append(nextLine);
                    j++;
                    break;
                }

                rawBuilder.append("\n").append(nextLine);
                j++;
            }

            if (amount != null) {
                String description = line;
                String resolvedRecipient = recipient.isBlank() ? description : recipient;
                transactions.add(new RawTransaction(
                        LocalDateTime.of(currentDate, LocalTime.MIDNIGHT),
                        amount,
                        resolvedRecipient,
                        description,
                        type,
                        rawBuilder.toString()
                ));
            } else {
                log.debug("NubankParser: no amount found for transaction starting at line {}: {}", i, line);
            }

            i = j;
        }

        return List.copyOf(transactions);
    }

    private LocalDate parseDate(Matcher m) {
        int day = Integer.parseInt(m.group(1));
        int month = MONTH_MAP.get(m.group(2));
        int year = Integer.parseInt(m.group(3));
        return LocalDate.of(year, month, day);
    }

    private TransactionType detectType(String line) {
        if (line.startsWith("Transferência enviada pelo Pix")) return TransactionType.EXPENSE;
        if (line.startsWith("Transferência recebida pelo Pix")) return TransactionType.INCOME;
        if (line.startsWith("Compra no débito")) return TransactionType.EXPENSE;
        if (line.startsWith("Pagamento de boleto efetuado")) return TransactionType.EXPENSE;
        if (line.startsWith("Aplicação RDB")) return TransactionType.EXPENSE;
        return null;
    }

    /**
     * Extracts recipient name from a transaction type keyword line.
     * For example: "Transferência enviada pelo Pix Natasha Reginaldo de Oliveira - •••.282.118-•• -"
     * → "Natasha Reginaldo de Oliveira"
     */
    private String extractRecipient(String line, TransactionType type) {
        String keyword = getKeyword(line);
        if (keyword == null || keyword.length() >= line.length()) {
            return "";
        }
        String rest = line.substring(keyword.length()).trim();
        // Strip CPF/CNPJ suffix
        rest = CPF_SUFFIX.matcher(rest).replaceAll("").trim();
        // Strip trailing " -"
        if (rest.endsWith(" -")) {
            rest = rest.substring(0, rest.length() - 2).trim();
        }
        return rest;
    }

    private String getKeyword(String line) {
        String[] keywords = {
                "Transferência enviada pelo Pix ",
                "Transferência recebida pelo Pix ",
                "Compra no débito ",
                "Pagamento de boleto efetuado ",
                "Aplicação RDB"
        };
        for (String kw : keywords) {
            if (line.startsWith(kw)) return kw;
        }
        return null;
    }

    /**
     * Extracts an amount that may appear at the end of the same keyword line.
     * Example: "Aplicação RDB 750,00" → 750.00
     */
    private BigDecimal extractInlineAmount(String line) {
        int lastSpace = line.lastIndexOf(' ');
        if (lastSpace < 0) return null;
        String tail = line.substring(lastSpace + 1).trim();
        if (AMOUNT_PATTERN.matcher(tail).matches()) {
            return parseAmount(tail);
        }
        return null;
    }

    private BigDecimal parseAmount(String raw) {
        // Format: "1.500,00" → "1500.00"
        String normalized = raw.replace(".", "").replace(",", ".");
        return new BigDecimal(normalized);
    }
}
