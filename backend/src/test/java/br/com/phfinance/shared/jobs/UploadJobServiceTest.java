package br.com.phfinance.shared.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.phfinance.shared.queue.OutboxEvent;
import br.com.phfinance.shared.queue.OutboxEventRepository;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UploadJobServiceTest {

    @Mock
    private UploadJobRepository uploadJobRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @TempDir
    Path tempDir;

    private UploadJobService service;

    @BeforeEach
    void setUp() {
        lenient().when(uploadJobRepository.save(any(UploadJob.class))).thenAnswer(inv -> {
            UploadJob job = inv.getArgument(0);
            // Simulate JPA ID generation — set the id field via reflection since
            // UploadJob uses @GeneratedValue and has no public setId()
            try {
                java.lang.reflect.Field field = UploadJob.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(job, UUID.randomUUID());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return job;
        });
        lenient().when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UploadJobService(uploadJobRepository, outboxEventRepository, new ObjectMapper(), tempDir.toString());
    }

    @Test
    void createFinanceUploadJob_savesFileAndReturnsJobId() throws Exception {
        byte[] content = "pdf-content".getBytes();
        String filename = "statement.pdf";
        String bankName = "NUBANK";
        String userId = "user@example.com";

        UUID jobId = service.createFinanceUploadJob(content, filename, bankName, userId);

        // job ID must be non-null
        assertThat(jobId).isNotNull();

        // UploadJob saved with correct fields
        ArgumentCaptor<UploadJob> jobCaptor = forClass(UploadJob.class);
        verify(uploadJobRepository).save(jobCaptor.capture());
        UploadJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(savedJob.getType()).isEqualTo(JobType.FINANCE_UPLOAD);
        assertThat(savedJob.getUserId()).isEqualTo(userId);
        assertThat(savedJob.getOriginalFilename()).isEqualTo(filename);

        // File must exist at the saved path
        String savedPath = savedJob.getFilePath();
        assertThat(savedPath).isNotNull();
        assertThat(Files.exists(Path.of(savedPath))).isTrue();
        assertThat(Files.readAllBytes(Path.of(savedPath))).isEqualTo(content);

        // OutboxEvent saved with correct queue and payload containing jobId and bankName
        ArgumentCaptor<OutboxEvent> eventCaptor = forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getQueueName()).isEqualTo(RabbitMqConfig.FINANCE_UPLOAD_QUEUE);
        assertThat(savedEvent.getPayload()).contains(bankName);
        // payload is JSON so jobId UUID is embedded as a string value
        assertThat(savedEvent.getPayload()).isNotBlank();
    }

    @Test
    void createFinanceUploadJob_rejectsPathOutsideTempDir() {
        assertThatThrownBy(() ->
                service.createFinanceUploadJob("data".getBytes(), "../../../etc/passwd", "NUBANK", "user@example.com"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid file path");
    }

    @Test
    void createInflationUploadJob_savesFileAndCreatesOutboxEvent() throws Exception {
        byte[] bytes = "xlsx-content".getBytes();
        UUID jobId = service.createInflationUploadJob(bytes, "market.xlsx", "user@example.com");

        assertThat(jobId).isNotNull();

        ArgumentCaptor<UploadJob> jobCaptor = ArgumentCaptor.forClass(UploadJob.class);
        verify(uploadJobRepository).save(jobCaptor.capture());
        UploadJob saved = jobCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(JobType.INFLATION_UPLOAD);
        assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(saved.getUserId()).isEqualTo("user@example.com");
        assertThat(saved.getFilePath()).startsWith(tempDir.toString());

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getQueueName()).isEqualTo(RabbitMqConfig.INFLATION_UPLOAD_QUEUE);
    }

    @Test
    void createReprocessJob_createsJobWithoutFile() throws Exception {
        String userId = "user@example.com";

        UUID jobId = service.createReprocessJob(userId);

        assertThat(jobId).isNotNull();

        ArgumentCaptor<UploadJob> jobCaptor = forClass(UploadJob.class);
        verify(uploadJobRepository).save(jobCaptor.capture());
        UploadJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getType()).isEqualTo(JobType.REPROCESS);
        assertThat(savedJob.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(savedJob.getUserId()).isEqualTo(userId);
        assertThat(savedJob.getFilePath()).isNull();
        assertThat(savedJob.getOriginalFilename()).isNull();

        ArgumentCaptor<OutboxEvent> eventCaptor = forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getQueueName()).isEqualTo(RabbitMqConfig.FINANCE_REPROCESS_QUEUE);
    }
}
