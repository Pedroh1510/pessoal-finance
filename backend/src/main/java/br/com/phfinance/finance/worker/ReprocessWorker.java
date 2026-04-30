package br.com.phfinance.finance.worker;

import br.com.phfinance.finance.application.ReprocessResult;
import br.com.phfinance.finance.application.TransactionReprocessService;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReprocessWorker {

    private static final Logger log = LoggerFactory.getLogger(ReprocessWorker.class);

    private final UploadJobRepository uploadJobRepository;
    private final TransactionReprocessService reprocessService;
    private final JobNotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ReprocessWorker(UploadJobRepository uploadJobRepository,
                            TransactionReprocessService reprocessService,
                            JobNotificationService notificationService,
                            ObjectMapper objectMapper) {
        this.uploadJobRepository = uploadJobRepository;
        this.reprocessService = reprocessService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqConfig.FINANCE_REPROCESS_QUEUE)
    public void handle(JobMessage message) throws Exception {
        int updated = uploadJobRepository.markProcessing(message.jobId());
        if (updated == 0) {
            return;
        }

        ReprocessResult result = reprocessService.reprocess();

        String resultJson = objectMapper.writeValueAsString(result);
        int completed = uploadJobRepository.markCompleted(message.jobId(), resultJson);

        if (completed > 0) {
            UploadJob job = uploadJobRepository.findById(message.jobId()).orElseThrow();
            notificationService.sendSuccess(job);
        } else {
            log.error("markCompleted returned 0 for job {} — job may have been externally modified", message.jobId());
        }
    }
}
