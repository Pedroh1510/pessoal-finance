package br.com.phfinance.finance.worker;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FinanceUploadDlqWorker {

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
    public void handle(JobMessage message) throws Exception {
        uploadJobRepository.markFailed(message.jobId(), "Processamento falhou após todas as tentativas");

        if (message.filePath() != null) {
            Path base = Path.of(tempDir).toAbsolutePath().normalize();
            Path resolved = Path.of(message.filePath()).toAbsolutePath().normalize();
            if (resolved.startsWith(base)) {
                Files.deleteIfExists(resolved);
            }
        }

        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendFailure(job);
    }
}
