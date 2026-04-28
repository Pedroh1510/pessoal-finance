package br.com.phfinance.shared.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:3000");
    }

    @Test
    void sendPasswordResetEmail_sendsEmailWithTokenLink() {
        emailService.sendPasswordResetEmail("user@test.com", "reset-token-uuid");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).contains("user@test.com");
        assertThat(msg.getSubject()).contains("ph-finance");
        assertThat(msg.getText()).contains("http://localhost:3000/reset-password?token=reset-token-uuid");
        assertThat(msg.getText()).contains("1 hora");
    }
}
