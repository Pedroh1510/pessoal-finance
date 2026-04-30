package br.com.phfinance.shared.queue;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int MAX_PUBLISH_ATTEMPTS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final UploadJobRepository uploadJobRepository;
    private final JobNotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Map<UUID, Integer> failureCount = new ConcurrentHashMap<>();

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           UploadJobRepository uploadJobRepository,
                           JobNotificationService notificationService,
                           RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper,
                           PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.uploadJobRepository = uploadJobRepository;
        this.notificationService = notificationService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 1000)
    public void publishPending() {
        List<OutboxEvent> pending = transactionTemplate.execute(tx -> outboxEventRepository.findPendingForUpdate());
        if (pending == null || pending.isEmpty()) return;

        for (OutboxEvent event : pending) {
            try {
                JobMessage message = objectMapper.readValue(event.getPayload(), JobMessage.class);
                rabbitTemplate.convertAndSend(RabbitMqConfig.MAIN_EXCHANGE, event.getQueueName(), message);
                transactionTemplate.execute(tx -> {
                    event.setPublished(true);
                    outboxEventRepository.save(event);
                    return null;
                });
                if (event.getId() != null) failureCount.remove(event.getId());
            } catch (Exception e) {
                UUID eventId = event.getId();
                if (eventId != null) {
                    int count = failureCount.merge(eventId, 1, Integer::sum);
                    log.error("Failed to publish outbox event id={}, attempt={}/{}", eventId, count, MAX_PUBLISH_ATTEMPTS, e);
                    if (count >= MAX_PUBLISH_ATTEMPTS) {
                        discardAndFailJob(event, eventId, count);
                    }
                } else {
                    log.error("Failed to publish outbox event (no id), queueName={}", event.getQueueName(), e);
                }
            }
        }
    }

    private void discardAndFailJob(OutboxEvent event, UUID eventId, int count) {
        log.error("Discarding outbox event id={} after {} failed attempts — marking job as failed", eventId, count);
        transactionTemplate.execute(tx -> {
            event.setPublished(true);
            outboxEventRepository.save(event);
            return null;
        });
        failureCount.remove(eventId);

        try {
            JobMessage message = objectMapper.readValue(event.getPayload(), JobMessage.class);
            int failed = uploadJobRepository.markFailed(message.jobId(),
                    "Falha ao publicar job na fila após " + count + " tentativas");
            if (failed > 0) {
                UploadJob job = uploadJobRepository.findById(message.jobId()).orElse(null);
                if (job != null) {
                    notificationService.sendFailure(job);
                }
            }
        } catch (Exception ex) {
            log.error("Could not fail job for discarded outbox event id={}", eventId, ex);
        }
    }
}
