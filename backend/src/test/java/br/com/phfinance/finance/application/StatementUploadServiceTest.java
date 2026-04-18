package br.com.phfinance.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import br.com.phfinance.shared.category.Category;
import br.com.phfinance.shared.fileparse.PdfExtractor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementUploadServiceTest {

    @Mock
    private PdfExtractor pdfExtractor;

    @Mock
    private BankStatementParser nubankParser;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RecipientCategoryRuleRepository recipientCategoryRuleRepository;

    @Mock
    private InternalAccountRuleRepository internalAccountRuleRepository;

    @Mock
    private InternalTransferRepository internalTransferRepository;

    @Mock
    private InternalTransferDetector internalTransferDetector;

    private StatementUploadService service;

    private static final byte[] DUMMY_PDF = {1, 2, 3};

    @BeforeEach
    void setUp() {
        when(nubankParser.getBankName()).thenReturn(BankName.NUBANK);
        service = new StatementUploadService(
                pdfExtractor,
                List.of(nubankParser),
                bankAccountRepository,
                transactionRepository,
                recipientCategoryRuleRepository,
                internalAccountRuleRepository,
                internalTransferRepository,
                internalTransferDetector);
    }

    @Test
    @DisplayName("upload calls extractText, then parser, creates BankAccount if absent")
    void upload_callsExtractTextAndParser_createsBankAccountIfAbsent() {
        String extractedText = "some pdf text";
        when(pdfExtractor.extractText(DUMMY_PDF)).thenReturn(extractedText);
        when(nubankParser.parse(extractedText)).thenReturn(List.of());
        when(bankAccountRepository.findByBankName(BankName.NUBANK)).thenReturn(Optional.empty());
        when(recipientCategoryRuleRepository.findAll()).thenReturn(List.of());
        when(internalAccountRuleRepository.findAll()).thenReturn(List.of());

        BankAccount newAccount = new BankAccount();
        newAccount.setId(UUID.randomUUID());
        newAccount.setBankName(BankName.NUBANK);
        when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(newAccount);

        UploadResult result = service.upload(DUMMY_PDF, BankName.NUBANK);

        verify(pdfExtractor).extractText(DUMMY_PDF);
        verify(nubankParser).parse(extractedText);
        verify(bankAccountRepository).findByBankName(BankName.NUBANK);

        ArgumentCaptor<BankAccount> captor = ArgumentCaptor.forClass(BankAccount.class);
        verify(bankAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getBankName()).isEqualTo(BankName.NUBANK);
        assertThat(captor.getValue().getAccountIdentifier()).isEqualTo("NUBANK");

        assertThat(result.total()).isZero();
    }

    @Test
    @DisplayName("upload applies recipient category rule when pattern matches")
    void upload_appliesRecipientCategoryRule_whenPatternMatches() {
        String extractedText = "some text";
        when(pdfExtractor.extractText(DUMMY_PDF)).thenReturn(extractedText);

        RawTransaction raw = new RawTransaction(
                LocalDateTime.now(),
                BigDecimal.TEN,
                "Nubank Supermercado",
                "Compra débito",
                TransactionType.EXPENSE,
                "raw");
        when(nubankParser.parse(extractedText)).thenReturn(List.of(raw));

        BankAccount account = existingAccount();
        when(bankAccountRepository.findByBankName(BankName.NUBANK)).thenReturn(Optional.of(account));

        Category category = category("Alimentação");
        RecipientCategoryRule rule = new RecipientCategoryRule();
        rule.setRecipientPattern("supermercado");
        rule.setCategory(category);
        when(recipientCategoryRuleRepository.findAll()).thenReturn(List.of(rule));
        when(internalAccountRuleRepository.findAll()).thenReturn(List.of());

        Transaction savedTx = new Transaction();
        savedTx.setId(UUID.randomUUID());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);
        when(internalTransferDetector.findAutoMatch(any(), any())).thenReturn(Optional.empty());

        service.upload(DUMMY_PDF, BankName.NUBANK);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getCategory()).isEqualTo(category);
    }

    @Test
    @DisplayName("upload sets INTERNAL_TRANSFER when InternalAccountRule matches")
    void upload_setsInternalTransferType_whenInternalAccountRuleMatches() {
        String extractedText = "some text";
        when(pdfExtractor.extractText(DUMMY_PDF)).thenReturn(extractedText);

        RawTransaction raw = new RawTransaction(
                LocalDateTime.now(),
                BigDecimal.TEN,
                "Pedro Henrique",
                "Transferência",
                TransactionType.EXPENSE,
                "raw");
        when(nubankParser.parse(extractedText)).thenReturn(List.of(raw));

        BankAccount account = existingAccount();
        when(bankAccountRepository.findByBankName(BankName.NUBANK)).thenReturn(Optional.of(account));
        when(recipientCategoryRuleRepository.findAll()).thenReturn(List.of());

        InternalAccountRule accountRule = new InternalAccountRule();
        accountRule.setIdentifier("Pedro Henrique");
        List<InternalAccountRule> rules = List.of(accountRule);
        when(internalAccountRuleRepository.findAll()).thenReturn(rules);
        when(internalTransferDetector.matchesInternalAccountRule(any(), any())).thenReturn(true);

        Transaction savedTx = new Transaction();
        savedTx.setId(UUID.randomUUID());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);

        service.upload(DUMMY_PDF, BankName.NUBANK);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(TransactionType.INTERNAL_TRANSFER);
    }

    @Test
    @DisplayName("upload creates InternalTransfer when auto-match found")
    void upload_createsInternalTransfer_whenAutoMatchFound() {
        String extractedText = "some text";
        when(pdfExtractor.extractText(DUMMY_PDF)).thenReturn(extractedText);

        RawTransaction raw = new RawTransaction(
                LocalDateTime.now(),
                BigDecimal.TEN,
                "Neon",
                "Pix enviado",
                TransactionType.EXPENSE,
                "raw");
        when(nubankParser.parse(extractedText)).thenReturn(List.of(raw));

        BankAccount account = existingAccount();
        when(bankAccountRepository.findByBankName(BankName.NUBANK)).thenReturn(Optional.of(account));
        when(recipientCategoryRuleRepository.findAll()).thenReturn(List.of());
        when(internalAccountRuleRepository.findAll()).thenReturn(List.of());
        when(internalTransferDetector.matchesInternalAccountRule(any(), any())).thenReturn(false);

        Transaction savedTx = new Transaction();
        savedTx.setId(UUID.randomUUID());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);

        Transaction matchTx = new Transaction();
        matchTx.setId(UUID.randomUUID());
        when(internalTransferDetector.findAutoMatch(any(), any())).thenReturn(Optional.of(matchTx));
        when(internalTransferRepository.save(any(InternalTransfer.class))).thenReturn(new InternalTransfer());

        UploadResult result = service.upload(DUMMY_PDF, BankName.NUBANK);

        verify(internalTransferRepository).save(any(InternalTransfer.class));
        assertThat(result.internalTransfers()).isEqualTo(1);
    }

    @Test
    @DisplayName("upload counts rule-based internal transfer in internalTransfers result")
    void upload_countsRuleBasedInternalTransfer_inResult() {
        String extractedText = "some text";
        when(pdfExtractor.extractText(DUMMY_PDF)).thenReturn(extractedText);

        RawTransaction raw = new RawTransaction(
                LocalDateTime.now(),
                BigDecimal.TEN,
                "Pedro Henrique",
                "Transferência",
                TransactionType.EXPENSE,
                "raw");
        when(nubankParser.parse(extractedText)).thenReturn(List.of(raw));

        BankAccount account = existingAccount();
        when(bankAccountRepository.findByBankName(BankName.NUBANK)).thenReturn(Optional.of(account));
        when(recipientCategoryRuleRepository.findAll()).thenReturn(List.of());
        when(internalAccountRuleRepository.findAll()).thenReturn(List.of());
        when(internalTransferDetector.matchesInternalAccountRule(any(), any())).thenReturn(true);

        Transaction savedTx = new Transaction();
        savedTx.setId(UUID.randomUUID());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);

        UploadResult result = service.upload(DUMMY_PDF, BankName.NUBANK);

        assertThat(result.internalTransfers()).isEqualTo(1);
    }

    @Test
    @DisplayName("upload falls back to OCR when extractText returns blank")
    void upload_fallsBackToOcr_whenExtractTextReturnsBlank() {
        when(pdfExtractor.extractText(DUMMY_PDF)).thenReturn("  ");
        String ocrText = "ocr extracted text";
        when(pdfExtractor.extractTextOcr(DUMMY_PDF)).thenReturn(ocrText);
        when(nubankParser.parse(ocrText)).thenReturn(List.of());
        when(bankAccountRepository.findByBankName(BankName.NUBANK)).thenReturn(Optional.of(existingAccount()));
        when(recipientCategoryRuleRepository.findAll()).thenReturn(List.of());
        when(internalAccountRuleRepository.findAll()).thenReturn(List.of());

        service.upload(DUMMY_PDF, BankName.NUBANK);

        verify(pdfExtractor).extractTextOcr(DUMMY_PDF);
        verify(nubankParser).parse(ocrText);
    }

    @Test
    @DisplayName("upload returns correct UploadResult counts")
    void upload_returnsCorrectUploadResultCounts() {
        String extractedText = "some text";
        when(pdfExtractor.extractText(DUMMY_PDF)).thenReturn(extractedText);

        RawTransaction categorized = new RawTransaction(
                LocalDateTime.now(),
                BigDecimal.TEN,
                "Supermercado ABC",
                "Compra",
                TransactionType.EXPENSE,
                "raw1");
        RawTransaction uncategorized = new RawTransaction(
                LocalDateTime.now(),
                BigDecimal.ONE,
                "Unknown recipient",
                "Desc",
                TransactionType.EXPENSE,
                "raw2");
        when(nubankParser.parse(extractedText)).thenReturn(List.of(categorized, uncategorized));

        BankAccount account = existingAccount();
        when(bankAccountRepository.findByBankName(BankName.NUBANK)).thenReturn(Optional.of(account));

        Category category = category("Alimentação");
        RecipientCategoryRule rule = new RecipientCategoryRule();
        rule.setRecipientPattern("supermercado");
        rule.setCategory(category);
        when(recipientCategoryRuleRepository.findAll()).thenReturn(List.of(rule));
        when(internalAccountRuleRepository.findAll()).thenReturn(List.of());
        when(internalTransferDetector.matchesInternalAccountRule(any(), any())).thenReturn(false);

        Transaction savedTx = new Transaction();
        savedTx.setId(UUID.randomUUID());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);
        when(internalTransferDetector.findAutoMatch(any(), any())).thenReturn(Optional.empty());

        UploadResult result = service.upload(DUMMY_PDF, BankName.NUBANK);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.internalTransfers()).isZero();
        assertThat(result.uncategorized()).isEqualTo(1);
    }

    @Test
    @DisplayName("upload throws IllegalArgumentException when no parser registered for bank")
    void upload_throwsWhenNoParserForBank() {
        // Build a service with an empty parsers list so no bank is registered
        StatementUploadService serviceWithNoParsers = new StatementUploadService(
                pdfExtractor,
                List.of(),
                bankAccountRepository,
                transactionRepository,
                recipientCategoryRuleRepository,
                internalAccountRuleRepository,
                internalTransferRepository,
                internalTransferDetector);

        when(pdfExtractor.extractText(DUMMY_PDF)).thenReturn("some text");

        assertThatThrownBy(() -> serviceWithNoParsers.upload(DUMMY_PDF, BankName.INTER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INTER");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BankAccount existingAccount() {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setBankName(BankName.NUBANK);
        return account;
    }

    private Category category(String name) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName(name);
        category.setColor("#FF0000");
        return category;
    }
}
