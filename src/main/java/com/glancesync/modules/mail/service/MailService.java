package com.glancesync.modules.mail.service;

import com.glancesync.modules.mail.config.MailConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

/**
 * Delivers plain-text email over SMTP; what the emails say lives in {@link AccountMails}.
 * Sending happens off the request thread, so a slow mail server never holds up the
 * response. With no SMTP host configured (local development) the message is written
 * to the log instead.
 */
public class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    private final ExecutorService executor;
    private final Session session;
    private final String from;

    public MailService(ExecutorService executor, MailConfig config) {
        this.executor = executor;
        this.from = config.getFrom();
        this.session = config.isEnabled() ? buildSession(config) : null;
    }

    public void send(String to, String subject, String body) {
        if (session == null) {
            LOG.info("SMTP not configured; email to {} not sent.\nSubject: {}\n\n{}", to, subject, body);
            return;
        }
        executor.execute(() -> {
            try {
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true));
                message.setSubject(subject, StandardCharsets.UTF_8.name());
                message.setText(body, StandardCharsets.UTF_8.name());
                Transport.send(message);
            } catch (MessagingException e) {
                LOG.error("Failed to send \"{}\" to {}", subject, to, e);
            }
        });
    }

    private static Session buildSession(MailConfig config) {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getHost());
        props.put("mail.smtp.port", String.valueOf(config.getPort()));
        props.put("mail.smtp.starttls.enable", String.valueOf(config.isStarttls()));
        props.put("mail.smtp.starttls.required", String.valueOf(config.isStarttls()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        if (config.getUsername() == null || config.getUsername().isBlank()) {
            return Session.getInstance(props);
        }
        props.put("mail.smtp.auth", "true");
        String username = config.getUsername();
        String password = config.getPassword();
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
}
