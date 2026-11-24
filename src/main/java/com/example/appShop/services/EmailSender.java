package com.example.appShop.services;

import com.example.appShop.config.AppMailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Единственная точка, которая умеет отправлять письма.
 *
 * Ключевой момент для локальной разработки: бин JavaMailSender появляется в контейнере
 * ТОЛЬКО если задан spring.mail.host (автоконфигурация MailSenderAutoConfiguration
 * условием смотрит именно на этот ключ). Мы дополнительно проверяем, что хост непустой,
 * и если письма ходить некуда — честно возвращаем false. Вызывающий код
 * (EmailVerificationService) в этом случае печатает PIN-код в лог, поэтому
 * подтвердить регистрацию можно и без почтового сервера.
 */
@Service
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final AppMailProperties mailProperties;
    private final String smtpHost;

    public EmailSender(ObjectProvider<JavaMailSender> mailSenders,
                       AppMailProperties mailProperties,
                       @Value("${spring.mail.host:}") String smtpHost) {
        this.mailSenders = mailSenders;
        this.mailProperties = mailProperties;
        this.smtpHost = smtpHost == null ? "" : smtpHost.trim();
    }

    public boolean isSmtpConfigured() {
        return !smtpHost.isEmpty() && mailSenders.getIfAvailable() != null;
    }

    /** @return true, только если письмо реально ушло на SMTP. */
    public boolean send(String to, String subject, String text) {
        JavaMailSender sender = isSmtpConfigured() ? mailSenders.getIfAvailable() : null;
        if (sender == null) {
            log.info("SMTP не настроен (spring.mail.host пуст) — письмо для {} не отправляем", to);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.fromOrDefault());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            sender.send(message);
            log.info("Письмо «{}» отправлено на {}", subject, to);
            return true;
        } catch (MailException e) {
            // Не роняем регистрацию из-за почтового сервера: код всё равно попадёт в лог.
            log.error("Не удалось отправить письмо на {}: {}", to, e.getMessage());
            return false;
        }
    }
}
