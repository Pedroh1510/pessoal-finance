package br.com.phfinance.shared.queue;

import br.com.phfinance.shared.jobs.JobStatus;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.jobs.UploadJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "app.queue.retry.initial-interval=100",
        "app.queue.retry.max-attempts=3"
})
@Testcontainers
class OutboxEndToEndIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @MockBean JavaMailSender mailSender;

    @Autowired UploadJobService uploadJobService;
    @Autowired UploadJobRepository uploadJobRepository;

    @Test
    void reprocessJob_transitionsToCompleted() throws Exception {
        UUID jobId = uploadJobService.createReprocessJob("user@example.com");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            UploadJob job = uploadJobRepository.findById(jobId).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.getResultJson()).contains("categorized");
        });
    }

    @Test
    void newJob_initiallyQueued() throws Exception {
        // Pause OutboxPublisher via mock would be ideal, but checking immediately after save
        // is sufficient since the scheduler fires every 2s and Testcontainers startup takes time.
        UUID jobId = uploadJobService.createReprocessJob("user@example.com");

        UploadJob job = uploadJobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
    }
}
