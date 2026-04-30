package br.com.phfinance.finance.worker;

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
class FinanceUploadDlqWorkerTest {

    @TempDir Path tempDir;
    @Mock UploadJobRepository uploadJobRepository;
    @Mock JobNotificationService notificationService;

    @Test
    void handle_marksJobFailedDeletesFileAndSendsEmail() throws Exception {
        Path file = tempDir.resolve("test.pdf");
        Files.write(file, "pdf".getBytes());

        FinanceUploadDlqWorker worker = new FinanceUploadDlqWorker(
                uploadJobRepository, notificationService, tempDir.toString());

        UUID jobId = UUID.randomUUID();
        UploadJob job = new UploadJob(JobType.FINANCE_UPLOAD, JobStatus.PROCESSING, "user@example.com");
        when(uploadJobRepository.markFailed(eq(jobId), any())).thenReturn(1);
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        worker.handle(new JobMessage(jobId, "FINANCE_UPLOAD", file.toString(), "NUBANK"));

        verify(uploadJobRepository).markFailed(eq(jobId), any());
        verify(notificationService).sendFailure(job);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void handle_idempotent_alreadyFailed_skipsNotification() {
        FinanceUploadDlqWorker worker = new FinanceUploadDlqWorker(
                uploadJobRepository, notificationService, tempDir.toString());

        UUID jobId = UUID.randomUUID();
        when(uploadJobRepository.markFailed(eq(jobId), any())).thenReturn(0);

        worker.handle(new JobMessage(jobId, "FINANCE_UPLOAD", null, "NUBANK"));

        verify(notificationService, never()).sendFailure(any());
    }

    @Test
    void handle_nullFilePath_doesNotThrow() {
        FinanceUploadDlqWorker worker = new FinanceUploadDlqWorker(
                uploadJobRepository, notificationService, tempDir.toString());

        UUID jobId = UUID.randomUUID();
        UploadJob job = new UploadJob(JobType.FINANCE_UPLOAD, JobStatus.PROCESSING, "user@example.com");
        when(uploadJobRepository.markFailed(eq(jobId), any())).thenReturn(1);
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        worker.handle(new JobMessage(jobId, "FINANCE_UPLOAD", null, "NUBANK"));

        verify(notificationService).sendFailure(job);
    }
}
