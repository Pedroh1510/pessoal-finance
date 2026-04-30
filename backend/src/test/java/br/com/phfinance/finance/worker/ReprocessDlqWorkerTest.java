package br.com.phfinance.finance.worker;

import br.com.phfinance.shared.jobs.JobNotificationService;
import br.com.phfinance.shared.jobs.JobStatus;
import br.com.phfinance.shared.jobs.JobType;
import br.com.phfinance.shared.jobs.UploadJob;
import br.com.phfinance.shared.jobs.UploadJobRepository;
import br.com.phfinance.shared.queue.JobMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReprocessDlqWorkerTest {

    @Mock UploadJobRepository uploadJobRepository;
    @Mock JobNotificationService notificationService;

    @Test
    void handle_marksJobFailedAndSendsEmail() {
        ReprocessDlqWorker worker = new ReprocessDlqWorker(uploadJobRepository, notificationService);
        UUID jobId = UUID.randomUUID();
        UploadJob job = new UploadJob(JobType.REPROCESS, JobStatus.PROCESSING, "user@example.com");
        when(uploadJobRepository.markFailed(eq(jobId), any())).thenReturn(1);
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        worker.handle(new JobMessage(jobId, "REPROCESS", null, null));

        verify(uploadJobRepository).markFailed(eq(jobId), any());
        verify(notificationService).sendFailure(job);
    }

    @Test
    void handle_idempotent_alreadyFailed_skipsNotification() {
        ReprocessDlqWorker worker = new ReprocessDlqWorker(uploadJobRepository, notificationService);
        UUID jobId = UUID.randomUUID();
        when(uploadJobRepository.markFailed(eq(jobId), any())).thenReturn(0);

        worker.handle(new JobMessage(jobId, "REPROCESS", null, null));

        verify(notificationService, never()).sendFailure(any());
    }

    @Test
    void handle_jobDeletedBetweenMarkFailedAndFindById_skipsNotification() {
        ReprocessDlqWorker worker = new ReprocessDlqWorker(uploadJobRepository, notificationService);
        UUID jobId = UUID.randomUUID();
        when(uploadJobRepository.markFailed(eq(jobId), any())).thenReturn(1);
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.empty());

        worker.handle(new JobMessage(jobId, "REPROCESS", null, null));

        verify(notificationService, never()).sendFailure(any());
    }
}
