package br.com.phfinance.shared.jobs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "management.health.rabbit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "owner@example.com")
class UploadJobControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @MockBean
    JavaMailSender mailSender;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UploadJobRepository uploadJobRepository;

    private UploadJob buildAndSaveJob(String userId) {
        UploadJob job = new UploadJob(JobType.FINANCE_UPLOAD, JobStatus.QUEUED, userId);
        return uploadJobRepository.save(job);
    }

    @Test
    void getJob_returns200_forOwner() throws Exception {
        UploadJob job = buildAndSaveJob("owner@example.com");

        mockMvc.perform(get("/api/jobs/{id}", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void getJob_returns404_forUnknownId() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "other@example.com")
    void getJob_returns403_forDifferentUser() throws Exception {
        UploadJob job = buildAndSaveJob("owner@example.com");

        mockMvc.perform(get("/api/jobs/{id}", job.getId()))
                .andExpect(status().isForbidden());
    }
}
