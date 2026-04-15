package br.com.phfinance.finance.application;

import br.com.phfinance.finance.domain.BankAccount;
import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.InternalAccountRule;
import br.com.phfinance.finance.domain.InternalTransfer;
import br.com.phfinance.finance.domain.InternalTransferDetector;
import br.com.phfinance.finance.domain.RawTransaction;
import br.com.phfinance.finance.domain.RecipientCategoryRule;
import br.com.phfinance.finance.domain.Transaction;
import br.com.phfinance.finance.domain.TransactionType;
import br.com.phfinance.finance.infra.BankAccountRepository;
import br.com.phfinance.finance.infra.InternalAccountRuleRepository;
import br.com.phfinance.finance.infra.InternalTransferRepository;
import br.com.phfinance.finance.infra.RecipientCategoryRuleRepository;
import br.com.phfinance.finance.infra.TransactionRepository;
import br.com.phfinance.finance.infra.parser.BankStatementParser;
import br.com.phfinance.shared.fileparse.PdfExtractor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StatementUploadService {

    private final PdfExtractor pdfExtractor;
    private final Map<BankName, BankStatementParser> parsers;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final RecipientCategoryRuleRepository recipientCategoryRuleRepository;
    private final InternalAccountRuleRepository internalAccountRuleRepository;
    private final InternalTransferRepository internalTransferRepository;
    private final InternalTransferDetector internalTransferDetector;

    public StatementUploadService(
            PdfExtractor pdfExtractor,
            List<BankStatementParser> parserList,
            BankAccountRepository bankAccountRepository,
            TransactionRepository transactionRepository,
            RecipientCategoryRuleRepository recipientCategoryRuleRepository,
            InternalAccountRuleRepository internalAccountRuleRepository,
            InternalTransferRepository internalTransferRepository,
            InternalTransferDetector internalTransferDetector) {
        this.pdfExtractor = pdfExtractor;
        this.parsers = parserList.stream()
                .collect(Collectors.toMap(BankStatementParser::getBankName, p -> p));
        this.bankAccountRepository = bankAccountRepository;
        this.transactionRepository = transactionRepository;
        this.recipientCategoryRuleRepository = recipientCategoryRuleRepository;
        this.internalAccountRuleRepository = internalAccountRuleRepository;
        this.internalTransferRepository = internalTransferRepository;
        this.internalTransferDetector = internalTransferDetector;
    }

    public UploadResult upload(byte[] pdf, BankName bank) {
        String text = pdfExtractor.extractText(pdf);
        if (text == null || text.isBlank()) {
            text = pdfExtractor.extractTextOcr(pdf);
        }

        BankStatementParser parser = parsers.get(bank);
        if (parser == null) {
            throw new IllegalArgumentException("No parser registered for bank: " + bank);
        }

        List<RawTransaction> rawTransactions = parser.parse(text);
        BankAccount account = findOrCreateAccount(bank);

        List<RecipientCategoryRule> categoryRules = recipientCategoryRuleRepository.findAll();
        List<InternalAccountRule> accountRules = internalAccountRuleRepository.findAll();

        int internalTransfers = 0;
        int uncategorized = 0;

        for (RawTransaction raw : rawTransactions) {
            Transaction tx = buildTransaction(raw, account);

            applyRecipientCategoryRule(tx, categoryRules);

            if (internalTransferDetector.matchesInternalAccountRule(tx, accountRules)) {
                tx.setType(TransactionType.INTERNAL_TRANSFER);
            }

            transactionRepository.save(tx);

            Optional<Transaction> match = internalTransferDetector.findAutoMatch(tx, transactionRepository);
            if (match.isPresent()) {
                createInternalTransfer(tx, match.get());
                internalTransfers++;
            }

            if (tx.getCategory() == null) {
                uncategorized++;
            }
        }

        return new UploadResult(rawTransactions.size(), internalTransfers, uncategorized);
    }

    private BankAccount findOrCreateAccount(BankName bank) {
        return bankAccountRepository.findByBankName(bank)
                .orElseGet(() -> {
                    BankAccount account = new BankAccount();
                    account.setBankName(bank);
                    account.setAccountIdentifier(bank.name());
                    return bankAccountRepository.save(account);
                });
    }

    private Transaction buildTransaction(RawTransaction raw, BankAccount account) {
        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setDate(raw.date().atOffset(ZoneOffset.UTC));
        tx.setAmount(raw.amount());
        tx.setRecipient(raw.recipient());
        tx.setDescription(raw.description());
        tx.setType(raw.type());
        tx.setRawText(raw.rawText());
        return tx;
    }

    private void applyRecipientCategoryRule(Transaction tx, List<RecipientCategoryRule> rules) {
        if (tx.getRecipient() == null) {
            return;
        }
        String recipientLower = tx.getRecipient().toLowerCase();
        rules.stream()
                .filter(rule -> recipientLower.contains(rule.getRecipientPattern().toLowerCase()))
                .findFirst()
                .ifPresent(rule -> tx.setCategory(rule.getCategory()));
    }

    private void createInternalTransfer(Transaction from, Transaction to) {
        InternalTransfer transfer = new InternalTransfer();
        transfer.setFromTransaction(from);
        transfer.setToTransaction(to);
        transfer.setDetectedAutomatically(true);
        internalTransferRepository.save(transfer);
    }
}
