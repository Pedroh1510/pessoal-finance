package br.com.phfinance.shared.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

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
            } catch (Exception e) {
                // leave unpublished; retried on next tick
            }
        }
    }
}
