package br.com.phfinance.inflation.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest(properties = {"management.health.mail.enabled=false", "management.health.rabbit.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser
class InflationControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    JavaMailSender mailSender;

    @Autowired
    MockMvc mockMvc;

    @Test
    void uploadXlsx_returns200_withPurchaseAndItemCount() throws Exception {
        String uniqueChave = uniqueChave();
        byte[] xlsx = buildValidXlsx(uniqueChave);
        MockMultipartFile file = new MockMultipartFile(
            "file", "2603_produtos.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsx
        );

        mockMvc.perform(multipart("/api/inflation/uploads").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.purchasesCreated").value(1))
            .andExpect(jsonPath("$.purchasesSkipped").value(0))
            .andExpect(jsonPath("$.itemsImported").value(2));
    }

    @Test
    void uploadXlsx_sameFileTwice_secondUploadSkipped() throws Exception {
        String uniqueChave = uniqueChave();
        byte[] xlsx = buildValidXlsx(uniqueChave);
        MockMultipartFile file = new MockMultipartFile(
            "file", "2603_produtos_dup.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsx
        );

        mockMvc.perform(multipart("/api/inflation/uploads").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.purchasesCreated").value(1));

        mockMvc.perform(multipart("/api/inflation/uploads").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.purchasesCreated").value(0))
            .andExpect(jsonPath("$.purchasesSkipped").value(1));
    }

    @Test
    void getItems_returns200_withArray() throws Exception {
        mockMvc.perform(get("/api/inflation/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getItems_withNcmFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/inflation/items").param("ncm", "10059010"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getComparison_returns200() throws Exception {
        mockMvc.perform(get("/api/inflation/comparison")
                .param("ncm", "10059010")
                .param("from", "2024-01")
                .param("to", "2025-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ncm").value("10059010"))
            .andExpect(jsonPath("$.prices").isArray());
    }

    @Test
    void uploadXlsx_withEmptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[0]
        );

        mockMvc.perform(multipart("/api/inflation/uploads").file(file))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getComparison_withDescriptionOnly_returns200() throws Exception {
        mockMvc.perform(get("/api/inflation/comparison")
                .param("description", "MILHO")
                .param("from", "2024-01")
                .param("to", "2025-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.prices").isArray());
    }

    @Test
    void getComparison_withNcmAndDescription_returns200() throws Exception {
        mockMvc.perform(get("/api/inflation/comparison")
                .param("ncm", "10059010")
                .param("description", "MILHO")
                .param("from", "2024-01")
                .param("to", "2025-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.prices").isArray());
    }

    @Test
    void getComparison_withoutNcmOrDescription_returns400() throws Exception {
        mockMvc.perform(get("/api/inflation/comparison")
                .param("from", "2024-01")
                .param("to", "2025-01"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getItems_withDescriptionFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/inflation/items").param("description", "MILHO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    private String uniqueChave() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        // NFe chave tem 44 dígitos — preenche com dígitos do UUID (hex -> tomar só dígitos)
        String digits = uuid.replaceAll("[^0-9]", "0");
        return (digits + "00000000000000000000000000000000000000000000").substring(0, 44);
    }

    private byte[] buildValidXlsx(String chave) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Produtos");
            writeRow(sheet, 0, "Data", "Código", "Descrição", "NCM", "Quantidade",
                "Valor Unitário", "Valor Total Item", "Emitente", "Chave");
            writeDataRow(sheet, 1,
                "29/03/2026", "12403", "MILHO PARA PIPOCA 400G", "10059010",
                1.5, 8.90, 13.35, "MERCADO TESTE LTDA", chave);
            writeDataRow(sheet, 2,
                "29/03/2026", "70087", "PAO DE FORMA 400G", "19059010",
                1.0, 7.99, 7.99, "MERCADO TESTE LTDA", chave);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private void writeRow(Sheet sheet, int rowNum, String... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private void writeDataRow(Sheet sheet, int rowNum, String date, String code,
            String desc, String ncm, double qty, double unitPrice, double total,
            String emitente, String chave) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(date);
        row.createCell(1).setCellValue(code);
        row.createCell(2).setCellValue(desc);
        row.createCell(3).setCellValue(ncm);
        row.createCell(4).setCellValue(qty);
        row.createCell(5).setCellValue(unitPrice);
        row.createCell(6).setCellValue(total);
        row.createCell(7).setCellValue(emitente);
        row.createCell(8).setCellValue(chave);
    }
}
