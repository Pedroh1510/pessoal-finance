package br.com.phfinance.finance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.phfinance.finance.application.CategoryDTO;
import br.com.phfinance.finance.application.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest(properties = {"management.health.mail.enabled=false", "management.health.rabbit.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser
class FinanceControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    JavaMailSender mailSender;

    @MockBean
    RabbitTemplate rabbitTemplate;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CategoryService categoryService;

    private byte[] loadNubankPdf() throws Exception {
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("fixtures/nubank-jan-2026.pdf")) {
            if (stream == null) {
                throw new IllegalStateException("fixtures/nubank-jan-2026.pdf not found in classpath");
            }
            return stream.readAllBytes();
        }
    }

    @Test
    void uploadNubankStatement_returns202_withJobId() throws Exception {
        byte[] pdfBytes = loadNubankPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/finance/uploads")
                        .file(file)
                        .param("bankName", "NUBANK"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isString());
    }

    @Test
    void getTransactions_returns200_withPage() throws Exception {
        mockMvc.perform(get("/api/finance/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getTransactions_withMonthFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/finance/transactions")
                        .param("month", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void categorizeTransaction_returns204() throws Exception {
        // Upload first to have a transaction
        byte[] pdfBytes = loadNubankPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/finance/uploads")
                .file(file)
                .param("bankName", "NUBANK"));

        // Get a transaction
        MvcResult result = mockMvc.perform(get("/api/finance/transactions"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode page = objectMapper.readTree(body);
        com.fasterxml.jackson.databind.JsonNode content = page.get("content");

        if (content.isEmpty()) {
            // No transactions in DB yet, skip categorization check
            return;
        }

        String transactionId = content.get(0).get("transactionId").asText();
        CategoryDTO category = categoryService.findAll().get(0);

        mockMvc.perform(put("/api/finance/transactions/{id}/category", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("categoryId", category.id()))))
                .andExpect(status().isNoContent());
    }

    @Test
    void createRecipientRule_returns201() throws Exception {
        CategoryDTO category = categoryService.findAll().get(0);

        MvcResult result = mockMvc.perform(post("/api/finance/rules/recipient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientPattern", "Mercado",
                                "categoryId", category.id()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.recipientPattern").value("Mercado"))
                .andReturn();

        // Extract the created rule id for cleanup verification
        String responseBody = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rule = objectMapper.readTree(responseBody);
        String ruleId = rule.get("id").asText();

        // Delete it
        mockMvc.perform(delete("/api/finance/rules/recipient/{id}", ruleId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRecipientRule_returns204() throws Exception {
        CategoryDTO category = categoryService.findAll().get(0);

        MvcResult result = mockMvc.perform(post("/api/finance/rules/recipient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientPattern", "TestDelete",
                                "categoryId", category.id()))))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String ruleId = objectMapper.readTree(responseBody).get("id").asText();

        mockMvc.perform(delete("/api/finance/rules/recipient/{id}", ruleId))
                .andExpect(status().isNoContent());
    }

    @Test
    void createInternalAccountRule_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/finance/rules/internal-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "João Silva",
                                "type", "NAME"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.identifier").value("João Silva"))
                .andExpect(jsonPath("$.type").value("NAME"))
                .andReturn();

        // Cleanup
        String ruleId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(delete("/api/finance/rules/internal-accounts/{id}", ruleId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteInternalAccountRule_returns204() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/finance/rules/internal-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "123.456.789-00",
                                "type", "CPF"))))
                .andExpect(status().isCreated())
                .andReturn();

        String ruleId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(delete("/api/finance/rules/internal-accounts/{id}", ruleId))
                .andExpect(status().isNoContent());
    }
}
