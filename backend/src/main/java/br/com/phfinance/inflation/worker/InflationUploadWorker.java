package br.com.phfinance.inflation.worker;

import br.com.phfinance.inflation.application.InflationUploadResult;
import br.com.phfinance.inflation.application.MarketUploadService;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class InflationUploadWorker {

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
        uploadJobRepository.markProcessing(message.jobId());

        Path filePath = validatePath(message.filePath());
        byte[] bytes = Files.readAllBytes(filePath);

        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        InflationUploadResult result = marketUploadService.upload(bytes, job.getOriginalFilename());

        String resultJson = objectMapper.writeValueAsString(result);
        uploadJobRepository.markCompleted(message.jobId(), resultJson);

        Files.deleteIfExists(filePath);

        UploadJob updated = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendSuccess(updated);
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
