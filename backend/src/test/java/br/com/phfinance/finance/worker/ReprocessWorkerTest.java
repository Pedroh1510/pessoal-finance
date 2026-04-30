package br.com.phfinance.finance.worker;

import br.com.phfinance.finance.application.ReprocessResult;
import br.com.phfinance.finance.application.TransactionReprocessService;
import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.JobStatus;
import br.com.phfinance.shared.jobs.JobType;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReprocessWorkerTest {

    @Mock UploadJobRepository uploadJobRepository;
    @Mock TransactionReprocessService reprocessService;
    @Mock JobNotificationService notificationService;

    @Test
    void handle_skipsWhenAlreadyClaimed() throws Exception {
        ReprocessWorker worker = new ReprocessWorker(
                uploadJobRepository, reprocessService,
                notificationService, new ObjectMapper()
        );
        UUID jobId = UUID.randomUUID();
        when(uploadJobRepository.markProcessing(jobId)).thenReturn(0);

        worker.handle(new JobMessage(jobId, "REPROCESS", null, null));

        verify(reprocessService, never()).reprocess();
        verify(notificationService, never()).sendSuccess(any());
    }

    @Test
    void handle_updatesJobCompleted() throws Exception {
        ReprocessWorker worker = new ReprocessWorker(
                uploadJobRepository, reprocessService,
                notificationService, new ObjectMapper()
        );
        UUID jobId = UUID.randomUUID();
        when(uploadJobRepository.markProcessing(jobId)).thenReturn(1);
        when(uploadJobRepository.markCompleted(eq(jobId), any())).thenReturn(1);
        when(reprocessService.reprocess()).thenReturn(new ReprocessResult(5, 2));
        UploadJob job = new UploadJob(JobType.REPROCESS, JobStatus.QUEUED, "user1");
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        worker.handle(new JobMessage(jobId, "REPROCESS", null, null));

        verify(uploadJobRepository).markProcessing(jobId);
        verify(uploadJobRepository).markCompleted(eq(jobId), any(String.class));
        verify(notificationService).sendSuccess(any());
    }
}
