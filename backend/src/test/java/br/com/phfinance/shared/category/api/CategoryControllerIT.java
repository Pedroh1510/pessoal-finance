package br.com.phfinance.shared.category.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.phfinance.finance.application.CategoryDTO;
import br.com.phfinance.finance.application.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CategoryControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CategoryService categoryService;

    @Test
    void getCategories_returns200_withSystemCategories() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void createCategory_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Test Category",
                                "color", "#123456"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Test Category"))
                .andExpect(jsonPath("$.color").value("#123456"))
                .andExpect(jsonPath("$.isSystem").value(false))
                .andReturn();

        // Cleanup
        String id = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(delete("/api/categories/{id}", id));
    }

    @Test
    void updateCategory_returns200() throws Exception {
        // Create a category to update
        MvcResult createResult = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Before Update",
                                "color", "#AAAAAA"))))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(put("/api/categories/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "After Update",
                                "color", "#BBBBBB"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After Update"))
                .andExpect(jsonPath("$.color").value("#BBBBBB"));

        // Cleanup
        mockMvc.perform(delete("/api/categories/{id}", id));
    }

    @Test
    void deleteNonSystemCategory_returns204() throws Exception {
        // Create a non-system category
        MvcResult createResult = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "To Delete",
                                "color", "#FF0000"))))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(delete("/api/categories/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSystemCategory_returns409Conflict() throws Exception {
        // Find a system category from the seeded data
        CategoryDTO systemCategory = categoryService.findAll().stream()
                .filter(CategoryDTO::isSystem)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No system category found"));

        mockMvc.perform(delete("/api/categories/{id}", systemCategory.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isString());
    }
}
