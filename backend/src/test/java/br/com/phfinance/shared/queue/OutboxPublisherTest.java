package br.com.phfinance.shared.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisher outboxPublisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        outboxPublisher = new OutboxPublisher(outboxEventRepository, rabbitTemplate, objectMapper);
    }

    @Test
    @DisplayName("publishesPendingEventAndMarksPublished - publishes to RabbitMQ and marks event as published")
    void publishesPendingEventAndMarksPublished() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobMessage jobMessage = new JobMessage(jobId, "FINANCE_UPLOAD", "/tmp/file.pdf", "nubank");
        String payload = objectMapper.writeValueAsString(jobMessage);

        OutboxEvent event = new OutboxEvent(RabbitMqConfig.FINANCE_UPLOAD_QUEUE, payload);

        when(outboxEventRepository.findPendingForUpdate()).thenReturn(List.of(event));

        outboxPublisher.publishPending();

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMqConfig.MAIN_EXCHANGE),
                eq(RabbitMqConfig.FINANCE_UPLOAD_QUEUE),
                messageCaptor.capture()
        );

        JobMessage sent = (JobMessage) messageCaptor.getValue();
        assertThat(sent.jobId()).isEqualTo(jobId);

        assertThat(event.isPublished()).isTrue();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("skipsWhenNoPendingEvents - no RabbitTemplate interaction when queue is empty")
    void skipsWhenNoPendingEvents() {
        when(outboxEventRepository.findPendingForUpdate()).thenReturn(List.of());

        outboxPublisher.publishPending();

        verifyNoInteractions(rabbitTemplate);
    }
}
