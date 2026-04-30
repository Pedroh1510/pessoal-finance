package br.com.phfinance.shared.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int MAX_PUBLISH_ATTEMPTS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Integer> failureCount = new ConcurrentHashMap<>();

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingForUpdate();
        for (OutboxEvent event : pending) {
            try {
                JobMessage message = objectMapper.readValue(event.getPayload(), JobMessage.class);
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.MAIN_EXCHANGE,
                        event.getQueueName(),
                        message
                );
                event.setPublished(true);
                outboxEventRepository.save(event);
                if (event.getId() != null) failureCount.remove(event.getId());
            } catch (Exception e) {
                UUID eventId = event.getId();
                if (eventId != null) {
                    int count = failureCount.merge(eventId, 1, Integer::sum);
                    log.error("Failed to publish outbox event id={}, attempt={}/{}", eventId, count, MAX_PUBLISH_ATTEMPTS, e);
                    if (count >= MAX_PUBLISH_ATTEMPTS) {
                        log.error("Discarding outbox event id={} after {} failed attempts — manual intervention required", eventId, count);
                        event.setPublished(true);
                        outboxEventRepository.save(event);
                        failureCount.remove(eventId);
                    }
                } else {
                    log.error("Failed to publish outbox event (no id), queueName={}", event.getQueueName(), e);
                }
            }
        }
    }
}
