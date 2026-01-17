package com.glideclouds.taskmanagementsystem.notifications;

import com.glideclouds.taskmanagementsystem.config.AppMailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppMailProperties props;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider, AppMailProperties props) {
        this.mailSenderProvider = mailSenderProvider;
        this.props = props;
    }

    /**
     * Sends an email when MAIL_ENABLED=true and a mail sender is configured.
     * Failures are logged and do not break the main request flow.
     */
    public void send(String to, String subject, String body) {
        if (!props.enabled()) {
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Email is enabled but no JavaMailSender is configured.");
            return;
        }

        if (to == null || to.isBlank()) {
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            String from = props.from();
            if (from != null && !from.isBlank()) {
                msg.setFrom(from);
            }
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception e) {
            // Don't break the main request flow if email fails.
            log.warn("Email send failed (to={}, subject={})", to, subject, e);
        }
    }
}
