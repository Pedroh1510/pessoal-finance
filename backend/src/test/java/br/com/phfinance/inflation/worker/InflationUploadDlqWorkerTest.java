package br.com.phfinance.inflation.worker;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.JobStatus;
import br.com.phfinance.shared.jobs.JobType;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InflationUploadDlqWorkerTest {

    @TempDir Path tempDir;
    @Mock UploadJobRepository uploadJobRepository;
    @Mock JobNotificationService notificationService;

    @Test
    void handle_marksJobFailedDeletesFileAndSendsEmail() throws Exception {
        Path file = tempDir.resolve("market.xlsx");
        Files.write(file, "xlsx".getBytes());

        InflationUploadDlqWorker worker = new InflationUploadDlqWorker(
                uploadJobRepository, notificationService, tempDir.toString());

        UUID jobId = UUID.randomUUID();
        UploadJob job = new UploadJob(JobType.INFLATION_UPLOAD, JobStatus.PROCESSING, "user@example.com");
        when(uploadJobRepository.markFailed(eq(jobId), any())).thenReturn(1);
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        worker.handle(new JobMessage(jobId, "INFLATION_UPLOAD", file.toString(), null));

        verify(uploadJobRepository).markFailed(eq(jobId), any());
        verify(notificationService).sendFailure(job);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void handle_idempotent_alreadyFailed_skipsNotification() {
        InflationUploadDlqWorker worker = new InflationUploadDlqWorker(
                uploadJobRepository, notificationService, tempDir.toString());

        UUID jobId = UUID.randomUUID();
        when(uploadJobRepository.markFailed(eq(jobId), any())).thenReturn(0);

        worker.handle(new JobMessage(jobId, "INFLATION_UPLOAD", null, null));

        verify(notificationService, never()).sendFailure(any());
    }
}
