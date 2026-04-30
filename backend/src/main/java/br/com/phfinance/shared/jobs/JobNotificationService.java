package br.com.phfinance.shared.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class JobNotificationService {

    private static final Logger log = LoggerFactory.getLogger(JobNotificationService.class);

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final String from;

    public JobNotificationService(
            JavaMailSender mailSender,
            ObjectMapper objectMapper,
            @Value("${app.mail.from:noreply@ph-finance.local}") String from) {
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.from = from;
    }

    public void sendSuccess(UploadJob job) {
        String subject = switch (job.getType()) {
            case FINANCE_UPLOAD -> "[ph-finance] Upload concluído";
            case INFLATION_UPLOAD -> "[ph-finance] Upload mercado concluído";
            case REPROCESS -> "[ph-finance] Reprocessamento concluído";
        };

        String body = buildSuccessBody(job);
        send(job.getUserId(), subject, body);
    }

    public void sendFailure(UploadJob job) {
        String subject = "[ph-finance] Falha no processamento";
        String body = buildFailureBody(job);
        send(job.getUserId(), subject, body);
    }

    private String buildSuccessBody(UploadJob job) {
        String resultJson = job.getResultJson();
        return switch (job.getType()) {
            case FINANCE_UPLOAD -> buildFinanceUploadSuccessBody(resultJson);
            case INFLATION_UPLOAD -> buildInflationUploadSuccessBody(resultJson);
            case REPROCESS -> buildReprocessSuccessBody(resultJson);
        };
    }

    private String buildFinanceUploadSuccessBody(String resultJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("Seu upload financeiro foi processado com sucesso.\n\n");
        if (resultJson != null) {
            Map<String, Object> fields = parseJson(resultJson);
            if (fields.containsKey("total")) sb.append("Total de transações: ").append(fields.get("total")).append("\n");
            if (fields.containsKey("internalTransfers")) sb.append("Transferências internas: ").append(fields.get("internalTransfers")).append("\n");
            if (fields.containsKey("uncategorized")) sb.append("Sem categoria: ").append(fields.get("uncategorized")).append("\n");
        }
        return sb.toString();
    }

    private String buildInflationUploadSuccessBody(String resultJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("Seu upload de mercado foi processado com sucesso.\n\n");
        if (resultJson != null) {
            Map<String, Object> fields = parseJson(resultJson);
            if (fields.containsKey("purchasesCreated")) sb.append("Compras criadas: ").append(fields.get("purchasesCreated")).append("\n");
            if (fields.containsKey("purchasesSkipped")) sb.append("Compras ignoradas: ").append(fields.get("purchasesSkipped")).append("\n");
            if (fields.containsKey("itemsImported")) sb.append("Itens importados: ").append(fields.get("itemsImported")).append("\n");
        }
        return sb.toString();
    }

    private String buildReprocessSuccessBody(String resultJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("O reprocessamento foi concluído com sucesso.\n\n");
        if (resultJson != null) {
            Map<String, Object> fields = parseJson(resultJson);
            if (fields.containsKey("categorized")) sb.append("Transações categorizadas: ").append(fields.get("categorized")).append("\n");
            if (fields.containsKey("typeChanged")) sb.append("Tipo alterado: ").append(fields.get("typeChanged")).append("\n");
        }
        return sb.toString();
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not parse resultJson for email body: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String buildFailureBody(UploadJob job) {
        String filename = job.getOriginalFilename() != null ? job.getOriginalFilename() : "N/A";
        return "Ocorreu uma falha ao processar seu arquivo.\n\n"
                + "Tipo: " + job.getType().name() + "\n"
                + "Arquivo: " + filename + "\n"
                + "Erro: " + job.getErrorMessage() + "\n"
                + "Tentativas: " + job.getAttemptCount() + "\n";
    }

    private void send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("E-mail enviado para {} — assunto: {}", to, subject);
        } catch (MessagingException ex) {
            log.error("Falha ao enviar e-mail para {} — assunto: {}", to, subject, ex);
        }
    }

}
