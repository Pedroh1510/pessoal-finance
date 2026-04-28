package br.com.phfinance.shared.auth.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Redefinição de senha - ph-finance");
        message.setText(
                "Clique no link para redefinir sua senha:\n"
                + baseUrl + "/reset-password?token=" + token
                + "\n\nO link expira em 1 hora.");
        mailSender.send(message);
    }
}
