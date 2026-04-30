package br.com.phfinance.finance.worker;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReprocessDlqWorker {

    private final UploadJobRepository uploadJobRepository;
    private final JobNotificationService notificationService;

    public ReprocessDlqWorker(UploadJobRepository uploadJobRepository,
                               JobNotificationService notificationService) {
        this.uploadJobRepository = uploadJobRepository;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMqConfig.FINANCE_REPROCESS_DLQ)
    public void handle(JobMessage message) {
        uploadJobRepository.markFailed(message.jobId(), "Reprocessamento falhou após todas as tentativas");
        UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
        notificationService.sendFailure(job);
    }
}
