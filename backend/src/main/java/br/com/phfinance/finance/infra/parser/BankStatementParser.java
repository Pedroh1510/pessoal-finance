package br.com.phfinance.finance.infra.parser;

import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.RawTransaction;

import java.util.List;

/**
 * Strategy interface for parsing bank statement text into raw transactions.
 * Each bank implementation handles its own PDF text format.
 */
public interface BankStatementParser {

    /**
     * Returns the bank this parser handles.
     */
    BankName getBankName();

    /**
     * Parses extracted PDF text into a list of raw transactions.
     *
     * @param extractedText plain text extracted from a bank statement PDF
     * @return list of parsed transactions; empty if text is null, blank, or contains no transactions
     */
    List<RawTransaction> parse(String extractedText);
}
