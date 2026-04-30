package br.com.phfinance.shared.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

public interface UploadJobRepository extends JpaRepository<UploadJob, UUID> {

    @Modifying
    @Transactional
    @Query("UPDATE UploadJob j SET j.status = br.com.phfinance.shared.jobs.JobStatus.PROCESSING, j.attemptCount = j.attemptCount + 1 WHERE j.id = :id")
    void markProcessing(UUID id);

    @Modifying
    @Transactional
    @Query("UPDATE UploadJob j SET j.status = br.com.phfinance.shared.jobs.JobStatus.COMPLETED, j.resultJson = :resultJson WHERE j.id = :id")
    void markCompleted(UUID id, String resultJson);

    @Modifying
    @Transactional
    @Query("UPDATE UploadJob j SET j.status = br.com.phfinance.shared.jobs.JobStatus.FAILED, j.errorMessage = :errorMessage WHERE j.id = :id")
    void markFailed(UUID id, String errorMessage);
}
