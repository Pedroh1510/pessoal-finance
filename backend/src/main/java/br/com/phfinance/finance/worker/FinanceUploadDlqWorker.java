package br.com.phfinance.finance.worker;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FinanceUploadDlqWorker {

    private static final Logger log = LoggerFactory.getLogger(FinanceUploadDlqWorker.class);

    private final UploadJobRepository uploadJobRepository;
    private final JobNotificationService notificationService;
    private final String tempDir;

    public FinanceUploadDlqWorker(UploadJobRepository uploadJobRepository,
                                   JobNotificationService notificationService,
                                   @Value("${app.queue.file.temp-dir:/tmp/ph-finance}") String tempDir) {
        this.uploadJobRepository = uploadJobRepository;
        this.notificationService = notificationService;
        this.tempDir = tempDir;
    }

    @RabbitListener(queues = RabbitMqConfig.FINANCE_UPLOAD_DLQ)
    public void handle(JobMessage message) {
        int updated = uploadJobRepository.markFailed(message.jobId(), "Processamento falhou após todas as tentativas");
        if (updated == 0) {
            return;
        }

        if (message.filePath() != null) {
            Path base = Path.of(tempDir).toAbsolutePath().normalize();
            Path resolved = Path.of(message.filePath()).toAbsolutePath().normalize();
            if (resolved.startsWith(base)) {
                try {
                    Files.deleteIfExists(resolved);
                } catch (Exception e) {
                    log.warn("Could not delete temp file {}: {}", resolved, e.getMessage());
                }
            }
        }

        UploadJob job = uploadJobRepository.findById(message.jobId()).orElse(null);
        if (job != null) {
            notificationService.sendFailure(job);
        } else {
            log.error("Job {} not found after markFailed — skipping failure notification", message.jobId());
        }
    }
}
