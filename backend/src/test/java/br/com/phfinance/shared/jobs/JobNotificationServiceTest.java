package br.com.phfinance.shared.jobs;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class JobNotificationServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock MimeMessage mimeMessage;

    JobNotificationService service;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        service = new JobNotificationService(mailSender, "noreply@ph-finance.local");
    }

    @Test
    void sendSuccess_financeUpload_sendsEmail() {
        UploadJob job = buildJob(
                JobType.FINANCE_UPLOAD,
                JobStatus.COMPLETED,
                "{\"total\":42,\"internalTransfers\":3,\"uncategorized\":5}",
                "user@example.com");

        service.sendSuccess(job);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendSuccess_inflationUpload_sendsEmail() {
        UploadJob job = buildJob(
                JobType.INFLATION_UPLOAD,
                JobStatus.COMPLETED,
                "{\"purchasesCreated\":10,\"purchasesSkipped\":2,\"itemsImported\":55}",
                "user@example.com");

        service.sendSuccess(job);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendSuccess_reprocess_sendsEmail() {
        UploadJob job = buildJob(
                JobType.REPROCESS,
                JobStatus.COMPLETED,
                "{\"categorized\":7,\"typeChanged\":2}",
                "user@example.com");

        service.sendSuccess(job);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendFailure_sendsEmail() {
        UploadJob job = buildJob(
                JobType.FINANCE_UPLOAD, JobStatus.FAILED, null, "user@example.com");
        job.setErrorMessage("Parse failed");
        job.setAttemptCount((short) 3);

        service.sendFailure(job);

        verify(mailSender).send(mimeMessage);
    }

    private UploadJob buildJob(
            JobType type, JobStatus status, String resultJson, String userId) {
        UploadJob job = new UploadJob();
        job.setType(type);
        job.setStatus(status);
        job.setUserId(userId);
        job.setResultJson(resultJson);
        job.setOriginalFilename("file.pdf");
        return job;
    }
}
