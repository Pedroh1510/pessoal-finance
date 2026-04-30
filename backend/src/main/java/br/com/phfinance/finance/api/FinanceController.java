package br.com.phfinance.finance.api;

import br.com.phfinance.finance.application.InternalAccountRuleDTO;
import br.com.phfinance.finance.application.InternalAccountRuleService;
import br.com.phfinance.finance.application.RecipientCategoryRuleDTO;
import br.com.phfinance.finance.application.RecipientRuleService;
import br.com.phfinance.finance.application.TransactionDTO;
import br.com.phfinance.finance.application.TransactionService;
import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.finance.domain.TransactionType;
import br.com.phfinance.shared.jobs.UploadJobService;
import java.util.Arrays;
import java.util.List;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final UploadJobService uploadJobService;
    private final TransactionService transactionService;
    private final RecipientRuleService recipientRuleService;
    private final InternalAccountRuleService internalAccountRuleService;

    public FinanceController(
            UploadJobService uploadJobService,
            TransactionService transactionService,
            RecipientRuleService recipientRuleService,
            InternalAccountRuleService internalAccountRuleService) {
        this.uploadJobService = uploadJobService;
        this.transactionService = transactionService;
        this.recipientRuleService = recipientRuleService;
        this.internalAccountRuleService = internalAccountRuleService;
    }

    private static final long MAX_UPLOAD_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    @PostMapping(value = "/uploads", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bankName") String bankName,
            Authentication auth) throws java.io.IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds maximum allowed size of 10 MB");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are accepted");
        }
        try {
            BankName.valueOf(bankName.toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(BankName.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid bank name '" + bankName + "'. Valid values: " + valid);
        }
        UUID jobId = uploadJobService.createFinanceUploadJob(
                file.getBytes(), file.getOriginalFilename(), bankName, auth.getName());
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @GetMapping("/transactions")
    public Page<TransactionDTO> getTransactions(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BankName bank,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        YearMonth yearMonth = month != null ? YearMonth.parse(month) : null;
        return transactionService.findAll(yearMonth, categoryId, bank, type, search, pageable);
    }

    @PutMapping("/transactions/{id}/category")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void categorize(
            @PathVariable UUID id,
            @Valid @RequestBody CategorizeRequest request) {
        transactionService.categorize(id, request.categoryId());
    }

    @GetMapping("/transactions/{id}")
    public TransactionDTO getTransaction(@PathVariable UUID id) {
        return transactionService.findById(id);
    }

    @GetMapping("/rules/recipient")
    public List<RecipientCategoryRuleDTO> getRecipientRules() {
        return recipientRuleService.findAll();
    }

    @PostMapping("/rules/recipient")
    public ResponseEntity<RecipientCategoryRuleDTO> createRecipientRule(
            @Valid @RequestBody CreateRecipientRuleRequest request) {
        RecipientCategoryRuleDTO dto = recipientRuleService.create(
                request.recipientPattern(), request.categoryId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/rules/recipient/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecipientRule(@PathVariable UUID id) {
        recipientRuleService.delete(id);
    }

    @GetMapping("/rules/internal-accounts")
    public List<InternalAccountRuleDTO> getInternalAccountRules() {
        return internalAccountRuleService.findAll();
    }

    @PostMapping("/rules/internal-accounts")
    public ResponseEntity<InternalAccountRuleDTO> createInternalAccountRule(
            @Valid @RequestBody CreateInternalAccountRuleRequest request) {
        InternalAccountRuleDTO dto = internalAccountRuleService.create(
                request.identifier(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/rules/internal-accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInternalAccountRule(@PathVariable UUID id) {
        internalAccountRuleService.delete(id);
    }

    @PostMapping("/reprocess")
    public ResponseEntity<Map<String, Object>> reprocess(Authentication auth) throws java.io.IOException {
        UUID jobId = uploadJobService.createReprocessJob(auth.getName());
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }
}
