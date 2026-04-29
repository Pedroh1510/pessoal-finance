package br.com.phfinance.shared.jobs;

import br.com.phfinance.shared.queue.JobMessage;
import br.com.phfinance.shared.queue.OutboxEvent;
import br.com.phfinance.shared.queue.OutboxEventRepository;
import br.com.phfinance.shared.queue.RabbitMqConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UploadJobService {

    private final UploadJobRepository uploadJobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String tempDir;

    public UploadJobService(
            UploadJobRepository uploadJobRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            @Value("${app.queue.file.temp-dir:/tmp/ph-finance}") String tempDir) {
        this.uploadJobRepository = uploadJobRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.tempDir = tempDir;
    }

    @Transactional(readOnly = true)
    public JobResponse getJobForUser(UUID id, String userId) {
        UploadJob job = uploadJobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!job.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return JobResponse.from(job, objectMapper);
    }

    @Transactional
    public UUID createFinanceUploadJob(byte[] fileBytes, String originalFilename, String bankName, String userId)
            throws IOException {
        validateOriginalFilename(originalFilename);
        String ext = extractExtension(originalFilename);
        String storageFilename = UUID.randomUUID() + "." + ext;
        Path filePath = resolveAndValidatePath(storageFilename);

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, fileBytes);

        UploadJob job = new UploadJob(JobType.FINANCE_UPLOAD, JobStatus.QUEUED, userId);
        job.setFilePath(filePath.toString());
        job.setOriginalFilename(originalFilename);
        uploadJobRepository.save(job);

        JobMessage message = new JobMessage(job.getId(), JobType.FINANCE_UPLOAD.name(), filePath.toString(), bankName);
        String payload = objectMapper.writeValueAsString(message);
        outboxEventRepository.save(new OutboxEvent(RabbitMqConfig.FINANCE_UPLOAD_QUEUE, payload));

        return job.getId();
    }

    @Transactional
    public UUID createInflationUploadJob(byte[] fileBytes, String originalFilename, String userId)
            throws IOException {
        validateOriginalFilename(originalFilename);
        String ext = extractExtension(originalFilename);
        String storageFilename = UUID.randomUUID() + "." + ext;
        Path filePath = resolveAndValidatePath(storageFilename);

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, fileBytes);

        UploadJob job = new UploadJob(JobType.INFLATION_UPLOAD, JobStatus.QUEUED, userId);
        job.setFilePath(filePath.toString());
        job.setOriginalFilename(originalFilename);
        uploadJobRepository.save(job);

        JobMessage message = new JobMessage(job.getId(), JobType.INFLATION_UPLOAD.name(), filePath.toString(), null);
        String payload = objectMapper.writeValueAsString(message);
        outboxEventRepository.save(new OutboxEvent(RabbitMqConfig.INFLATION_UPLOAD_QUEUE, payload));

        return job.getId();
    }

    @Transactional
    public UUID createReprocessJob(String userId) throws JsonProcessingException {
        UploadJob job = new UploadJob(JobType.REPROCESS, JobStatus.QUEUED, userId);
        uploadJobRepository.save(job);

        JobMessage message = new JobMessage(job.getId(), JobType.REPROCESS.name(), null, null);
        String payload = objectMapper.writeValueAsString(message);
        outboxEventRepository.save(new OutboxEvent(RabbitMqConfig.FINANCE_REPROCESS_QUEUE, payload));

        return job.getId();
    }

    private void validateOriginalFilename(String filename) {
        if (filename == null) return;
        resolveAndValidatePath(filename);
    }

    private Path resolveAndValidatePath(String filename) {
        Path base = Path.of(tempDir).toAbsolutePath().normalize();
        Path resolved = base.resolve(filename).normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Invalid file path: " + filename);
        }
        return resolved;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "bin";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "bin";
    }
}
