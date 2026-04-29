package br.com.phfinance.shared.jobs;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class JobNotificationService {

    private static final Logger log = LoggerFactory.getLogger(JobNotificationService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public JobNotificationService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:noreply@ph-finance.local}") String from) {
        this.mailSender = mailSender;
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
            String total = extractJsonField(resultJson, "total");
            String internalTransfers = extractJsonField(resultJson, "internalTransfers");
            String uncategorized = extractJsonField(resultJson, "uncategorized");
            if (total != null) sb.append("Total de transações: ").append(total).append("\n");
            if (internalTransfers != null) sb.append("Transferências internas: ").append(internalTransfers).append("\n");
            if (uncategorized != null) sb.append("Sem categoria: ").append(uncategorized).append("\n");
        }
        return sb.toString();
    }

    private String buildInflationUploadSuccessBody(String resultJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("Seu upload de mercado foi processado com sucesso.\n\n");
        if (resultJson != null) {
            String purchasesCreated = extractJsonField(resultJson, "purchasesCreated");
            String purchasesSkipped = extractJsonField(resultJson, "purchasesSkipped");
            String itemsImported = extractJsonField(resultJson, "itemsImported");
            if (purchasesCreated != null) sb.append("Compras criadas: ").append(purchasesCreated).append("\n");
            if (purchasesSkipped != null) sb.append("Compras ignoradas: ").append(purchasesSkipped).append("\n");
            if (itemsImported != null) sb.append("Itens importados: ").append(itemsImported).append("\n");
        }
        return sb.toString();
    }

    private String buildReprocessSuccessBody(String resultJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("O reprocessamento foi concluído com sucesso.\n\n");
        if (resultJson != null) {
            String categorized = extractJsonField(resultJson, "categorized");
            String typeChanged = extractJsonField(resultJson, "typeChanged");
            if (categorized != null) sb.append("Transações categorizadas: ").append(categorized).append("\n");
            if (typeChanged != null) sb.append("Tipo alterado: ").append(typeChanged).append("\n");
        }
        return sb.toString();
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
            throw new RuntimeException("Falha ao enviar e-mail: " + ex.getMessage(), ex);
        }
    }

    /**
     * Extrai o valor de um campo de um JSON simples (sem ObjectMapper para evitar dependência
     * extra). Funciona apenas para campos de valor primitivo no nível raiz.
     */
    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        String raw = json.substring(start, end).trim();
        return raw.isEmpty() ? null : raw.replace("\"", "");
    }
}
