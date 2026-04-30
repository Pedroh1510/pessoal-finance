package br.com.phfinance.shared.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record JobResponse(
        UUID id,
        String type,
        String status,
        Map<String, Object> result,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    private static final Logger log = LoggerFactory.getLogger(JobResponse.class);

    public static JobResponse from(UploadJob job, ObjectMapper objectMapper) {
        Map<String, Object> result = null;
        if (job.getResultJson() != null) {
            try {
                result = objectMapper.readValue(job.getResultJson(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize resultJson for job {}", job.getId(), e);
            }
        }
        return new JobResponse(
                job.getId(),
                job.getType().name(),
                job.getStatus().name(),
                result,
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
