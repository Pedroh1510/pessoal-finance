package br.com.phfinance.inflation.worker;

import br.com.phfinance.inflation.application.InflationUploadResult;
import br.com.phfinance.inflation.application.MarketUploadService;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.JobStatus;
import br.com.phfinance.shared.jobs.JobType;
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
class InflationUploadWorkerTest {

    @TempDir Path tempDir;
    @Mock UploadJobRepository uploadJobRepository;
    @Mock MarketUploadService marketUploadService;
    @Mock JobNotificationService notificationService;

    @Test
    void handle_updatesJobCompletedAndDeletesFile() throws Exception {
        Path file = tempDir.resolve("market.xlsx");
        Files.write(file, "xlsx".getBytes());

        InflationUploadWorker worker = new InflationUploadWorker(
                uploadJobRepository, marketUploadService,
                notificationService, new ObjectMapper(), tempDir.toString()
        );

        UUID jobId = UUID.randomUUID();
        UploadJob job = new UploadJob(JobType.INFLATION_UPLOAD, JobStatus.QUEUED, "user1");
        job.setOriginalFilename("market.xlsx");
        when(uploadJobRepository.markProcessing(jobId)).thenReturn(1);
        when(uploadJobRepository.markCompleted(eq(jobId), any())).thenReturn(1);
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(marketUploadService.upload(any(), eq("market.xlsx")))
                .thenReturn(new InflationUploadResult(5, 1, 30));

        JobMessage msg = new JobMessage(jobId, "INFLATION_UPLOAD", file.toString(), null);
        worker.handle(msg);

        verify(uploadJobRepository).markProcessing(jobId);
        verify(uploadJobRepository).markCompleted(eq(jobId), any(String.class));
        verify(notificationService).sendSuccess(any());
        assertThat(Files.exists(file)).isFalse();
    }
}
