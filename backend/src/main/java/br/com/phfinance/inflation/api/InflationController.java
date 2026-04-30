package br.com.phfinance.inflation.api;

import br.com.phfinance.inflation.application.MarketComparisonService;
import br.com.phfinance.inflation.application.MarketItemDTO;
import br.com.phfinance.inflation.application.MarketUploadService;
import br.com.phfinance.inflation.application.NcmComparisonDTO;
import br.com.phfinance.shared.jobs.UploadJobService;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/inflation")
public class InflationController {

    private static final long MAX_UPLOAD_SIZE_BYTES = 10L * 1024 * 1024;

    private final MarketUploadService marketUploadService;
    private final MarketComparisonService marketComparisonService;
    private final UploadJobService uploadJobService;

    public InflationController(MarketUploadService marketUploadService,
                               MarketComparisonService marketComparisonService,
                               UploadJobService uploadJobService) {
        this.marketUploadService = marketUploadService;
        this.marketComparisonService = marketComparisonService;
        this.uploadJobService = uploadJobService;
    }

    private static final java.util.Set<String> VALID_SPREADSHEET_TYPES = java.util.Set.of(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    @PostMapping(value = "/uploads", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File exceeds maximum allowed size of 10 MB");
        }
        if (!VALID_SPREADSHEET_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Apenas arquivos XLS/XLSX são aceitos");
        }
        try {
            UUID jobId = uploadJobService.createInflationUploadJob(
                    file.getBytes(), file.getOriginalFilename(), auth.getName());
            return ResponseEntity.accepted().body(Map.of("jobId", jobId));
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read uploaded file");
        }
    }

    @GetMapping("/comparison")
    public NcmComparisonDTO getComparison(
            @RequestParam(required = false) String ncm,
            @RequestParam(required = false) String description,
            @RequestParam String from,
            @RequestParam String to) {
        if (ncm == null && description == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "At least one of 'ncm' or 'description' must be provided");
        }
        try {
            return marketComparisonService.getComparison(
                ncm, description, YearMonth.parse(from), YearMonth.parse(to));
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid period format. Expected YYYY-MM");
        }
    }

    @GetMapping("/items")
    public List<MarketItemDTO> getItems(
            @RequestParam(required = false) String ncm,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String period) {
        YearMonth yearMonth = null;
        if (period != null) {
            try {
                yearMonth = YearMonth.parse(period);
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid period format. Expected YYYY-MM");
            }
        }
        return marketComparisonService.getItems(ncm, description, yearMonth);
    }
}
