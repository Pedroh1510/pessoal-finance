package br.com.phfinance.finance.api;

import br.com.phfinance.finance.application.InternalAccountRuleDTO;
import br.com.phfinance.finance.application.InternalAccountRuleService;
import br.com.phfinance.finance.application.RecipientCategoryRuleDTO;
import br.com.phfinance.finance.application.RecipientRuleService;
import br.com.phfinance.finance.application.StatementUploadService;
import br.com.phfinance.finance.application.TransactionDTO;
import br.com.phfinance.finance.application.TransactionService;
import br.com.phfinance.finance.application.UploadResult;
import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.TransactionType;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final StatementUploadService statementUploadService;
    private final TransactionService transactionService;
    private final RecipientRuleService recipientRuleService;
    private final InternalAccountRuleService internalAccountRuleService;

    public FinanceController(
            StatementUploadService statementUploadService,
            TransactionService transactionService,
            RecipientRuleService recipientRuleService,
            InternalAccountRuleService internalAccountRuleService) {
        this.statementUploadService = statementUploadService;
        this.transactionService = transactionService;
        this.recipientRuleService = recipientRuleService;
        this.internalAccountRuleService = internalAccountRuleService;
    }

    @PostMapping(value = "/uploads", consumes = "multipart/form-data")
    public ResponseEntity<UploadResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bankName") String bankName) throws Exception {
        BankName bank = BankName.valueOf(bankName.toUpperCase());
        byte[] bytes = file.getBytes();
        UploadResult result = statementUploadService.upload(bytes, bank);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/transactions")
    public Page<TransactionDTO> getTransactions(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BankName bank,
            @RequestParam(required = false) TransactionType type,
            Pageable pageable) {
        YearMonth yearMonth = month != null ? YearMonth.parse(month) : null;
        return transactionService.findAll(yearMonth, categoryId, bank, type, pageable);
    }

    @PutMapping("/transactions/{id}/category")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void categorize(
            @PathVariable UUID id,
            @RequestBody CategorizeRequest request) {
        transactionService.categorize(id, request.categoryId());
    }

    @PostMapping("/rules/recipient")
    public ResponseEntity<RecipientCategoryRuleDTO> createRecipientRule(
            @RequestBody CreateRecipientRuleRequest request) {
        RecipientCategoryRuleDTO dto = recipientRuleService.create(
                request.recipientPattern(), request.categoryId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/rules/recipient/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecipientRule(@PathVariable UUID id) {
        recipientRuleService.delete(id);
    }

    @PostMapping("/rules/internal-accounts")
    public ResponseEntity<InternalAccountRuleDTO> createInternalAccountRule(
            @RequestBody CreateInternalAccountRuleRequest request) {
        InternalAccountRuleDTO dto = internalAccountRuleService.create(
                request.identifier(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/rules/internal-accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInternalAccountRule(@PathVariable UUID id) {
        internalAccountRuleService.delete(id);
    }
}
