package br.com.phfinance.finance.worker;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReprocessDlqWorker {

    private static final Logger log = LoggerFactory.getLogger(ReprocessDlqWorker.class);

    private final UploadJobRepository uploadJobRepository;
    private final JobNotificationService notificationService;

    public ReprocessDlqWorker(UploadJobRepository uploadJobRepository,
                               JobNotificationService notificationService) {
        this.uploadJobRepository = uploadJobRepository;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMqConfig.FINANCE_REPROCESS_DLQ)
    public void handle(JobMessage message) {
        int updated = uploadJobRepository.markFailed(message.jobId(), "Reprocessamento falhou após todas as tentativas");
        if (updated == 0) {
            return;
        }
        UploadJob job = uploadJobRepository.findById(message.jobId()).orElse(null);
        if (job != null) {
            notificationService.sendFailure(job);
        } else {
            log.error("Job {} not found after markFailed — skipping failure notification", message.jobId());
        }
    }
}
