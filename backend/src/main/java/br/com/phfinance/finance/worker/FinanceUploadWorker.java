package br.com.phfinance.finance.worker;

import br.com.phfinance.finance.application.StatementUploadService;
import br.com.phfinance.finance.application.UploadResult;
import br.com.phfinance.finance.domain.BankName;
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
public class FinanceUploadWorker {

    private final UploadJobRepository uploadJobRepository;
    private final StatementUploadService statementUploadService;
    private final JobNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String tempDir;

    public FinanceUploadWorker(UploadJobRepository uploadJobRepository,
                                StatementUploadService statementUploadService,
                                JobNotificationService notificationService,
                                ObjectMapper objectMapper,
                                @Value("${app.queue.file.temp-dir:/tmp/ph-finance}") String tempDir) {
        this.uploadJobRepository = uploadJobRepository;
        this.statementUploadService = statementUploadService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.tempDir = tempDir;
    }

    @RabbitListener(queues = RabbitMqConfig.FINANCE_UPLOAD_QUEUE)
    public void handle(JobMessage message) throws Exception {
        uploadJobRepository.markProcessing(message.jobId());

        Path filePath = validatePath(message.filePath());
        byte[] bytes = Files.readAllBytes(filePath);
        BankName bank = BankName.valueOf(message.bankName());

        UploadResult result = statementUploadService.upload(bytes, bank);

        String resultJson = objectMapper.writeValueAsString(result);
        uploadJobRepository.markCompleted(message.jobId(), resultJson);

        Files.deleteIfExists(filePath);

        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendSuccess(job);
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
