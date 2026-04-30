package br.com.phfinance.shared.queue;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.JobStatus;
import br.com.phfinance.shared.jobs.JobType;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private UploadJobRepository uploadJobRepository;
    @Mock private JobNotificationService notificationService;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private OutboxPublisher outboxPublisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        outboxPublisher = new OutboxPublisher(
                outboxEventRepository, uploadJobRepository, notificationService,
                rabbitTemplate, objectMapper, transactionManager
        );
    }

    @Test
    @DisplayName("publishes to RabbitMQ and marks event as published")
    void publishesPendingEventAndMarksPublished() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobMessage jobMessage = new JobMessage(jobId, "FINANCE_UPLOAD", "/tmp/file.pdf", "NUBANK");
        String payload = objectMapper.writeValueAsString(jobMessage);

        OutboxEvent event = new OutboxEvent(RabbitMqConfig.FINANCE_UPLOAD_QUEUE, payload);

        when(outboxEventRepository.findPendingForUpdate()).thenReturn(List.of(event));

        outboxPublisher.publishPending();

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.MAIN_EXCHANGE),
                eq(RabbitMqConfig.FINANCE_UPLOAD_QUEUE),
                messageCaptor.capture()
        );
        assertThat(((JobMessage) messageCaptor.getValue()).jobId()).isEqualTo(jobId);
        assertThat(event.isPublished()).isTrue();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("no RabbitTemplate interaction when queue is empty")
    void skipsWhenNoPendingEvents() {
        when(outboxEventRepository.findPendingForUpdate()).thenReturn(List.of());

        outboxPublisher.publishPending();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("after MAX_PUBLISH_ATTEMPTS failures marks event published and calls markFailed")
    void discardsAndFailsJobAfterMaxAttempts() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        JobMessage jobMessage = new JobMessage(jobId, "FINANCE_UPLOAD", "/tmp/file.pdf", "NUBANK");
        String payload = objectMapper.writeValueAsString(jobMessage);

        OutboxEvent event = new OutboxEvent(eventId, RabbitMqConfig.FINANCE_UPLOAD_QUEUE, payload);
        UploadJob job = new UploadJob(JobType.FINANCE_UPLOAD, JobStatus.QUEUED, "user1");

        when(outboxEventRepository.findPendingForUpdate()).thenReturn(List.of(event));
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));
        when(uploadJobRepository.markFailed(eq(jobId), any())).thenReturn(1);
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        for (int i = 0; i < 5; i++) {
            outboxPublisher.publishPending();
        }

        assertThat(event.isPublished()).isTrue();
        verify(uploadJobRepository).markFailed(eq(jobId), any());
        verify(notificationService).sendFailure(job);
    }
}
