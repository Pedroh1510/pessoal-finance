package br.com.phfinance.inflation.worker;

import br.com.phfinance.inflation.application.InflationUploadResult;
import br.com.phfinance.inflation.application.MarketUploadService;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class InflationUploadWorker {

    private static final Logger log = LoggerFactory.getLogger(InflationUploadWorker.class);

    private final UploadJobRepository uploadJobRepository;
    private final MarketUploadService marketUploadService;
    private final JobNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String tempDir;

    public InflationUploadWorker(UploadJobRepository uploadJobRepository,
                                  MarketUploadService marketUploadService,
                                  JobNotificationService notificationService,
                                  ObjectMapper objectMapper,
                                  @Value("${app.queue.file.temp-dir:/tmp/ph-finance}") String tempDir) {
        this.uploadJobRepository = uploadJobRepository;
        this.marketUploadService = marketUploadService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.tempDir = tempDir;
    }

    @RabbitListener(queues = RabbitMqConfig.INFLATION_UPLOAD_QUEUE)
    public void handle(JobMessage message) throws Exception {
        int updated = uploadJobRepository.markProcessing(message.jobId());
        if (updated == 0) {
            cleanupTempFile(message.filePath());
            return;
        }

        Path filePath = validatePath(message.filePath());
        byte[] bytes = Files.readAllBytes(filePath);

        String originalFilename = uploadJobRepository.findById(message.jobId())
                .orElseThrow().getOriginalFilename();
        InflationUploadResult result = marketUploadService.upload(bytes, originalFilename);

        String resultJson = objectMapper.writeValueAsString(result);
        int completedCount = uploadJobRepository.markCompleted(message.jobId(), resultJson);

        try {
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.warn("Could not delete temp file {}: {}", filePath, e.getMessage());
        }

        if (completedCount > 0) {
            UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
            notificationService.sendSuccess(job);
        } else {
            log.error("markCompleted returned 0 for job {} — job may have been externally modified", message.jobId());
        }
    }

    private void cleanupTempFile(String rawPath) {
        if (rawPath == null) return;
        try {
            Path base = Path.of(tempDir).toAbsolutePath().normalize();
            Path resolved = Path.of(rawPath).toAbsolutePath().normalize();
            if (resolved.startsWith(base)) {
                Files.deleteIfExists(resolved);
            }
        } catch (Exception e) {
            log.warn("Could not clean up temp file {}: {}", rawPath, e.getMessage());
        }
    }

    private Path validatePath(String rawPath) {
        Path base = Path.of(tempDir).toAbsolutePath().normalize();
        Path resolved = Path.of(rawPath).toAbsolutePath().normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Invalid file path: " + rawPath);
        }
        return resolved;
    }
}
