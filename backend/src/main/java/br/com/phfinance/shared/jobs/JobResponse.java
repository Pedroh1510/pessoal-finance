package br.com.phfinance.shared.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String type,
        String status,
        Map<String, Object> result,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JobResponse from(UploadJob job) {
        Map<String, Object> result = null;
        String resultJson = job.getResultJson();
        if (resultJson != null) {
            try {
                result = MAPPER.readValue(resultJson, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                // leave result as null
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
