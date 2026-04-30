package br.com.phfinance.finance.worker;

import br.com.phfinance.finance.application.StatementUploadService;
import br.com.phfinance.finance.application.UploadResult;
import br.com.phfinance.finance.domain.BankName;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.JobStatus;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceUploadWorkerTest {

    @TempDir Path tempDir;
    @Mock UploadJobRepository uploadJobRepository;
    @Mock StatementUploadService statementUploadService;
    @Mock JobNotificationService notificationService;

    @Test
    void handle_updatesJobCompletedAndDeletesFile() throws Exception {
        Path file = tempDir.resolve("test.pdf");
        Files.write(file, "pdf".getBytes());

        FinanceUploadWorker worker = new FinanceUploadWorker(
                uploadJobRepository, statementUploadService,
                notificationService, new ObjectMapper(), tempDir.toString()
        );

        UUID jobId = UUID.randomUUID();
        UploadJob job = new UploadJob(br.com.phfinance.shared.jobs.JobType.FINANCE_UPLOAD,
                JobStatus.QUEUED, "user1");
        when(uploadJobRepository.markProcessing(jobId)).thenReturn(1);
        when(uploadJobRepository.markCompleted(eq(jobId), any())).thenReturn(1);
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(statementUploadService.upload(any(), eq(BankName.NUBANK)))
                .thenReturn(new UploadResult(10, 2, 3));

        JobMessage msg = new JobMessage(jobId, "FINANCE_UPLOAD", file.toString(), "NUBANK");
        worker.handle(msg);

        verify(uploadJobRepository).markProcessing(jobId);
        verify(uploadJobRepository).markCompleted(eq(jobId), any(String.class));
        verify(notificationService).sendSuccess(any());
        assertThat(Files.exists(file)).isFalse();
    }
}
