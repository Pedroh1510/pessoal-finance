package br.com.phfinance.shared.jobs;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class UploadJobController {

    private final UploadJobService uploadJobService;

    public UploadJobController(UploadJobService uploadJobService) {
        this.uploadJobService = uploadJobService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(uploadJobService.getJobForUser(id, auth.getName()));
    }
}
