package com.coretrust.bank.notification.service;

import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Email delivery failed (subject: {})", subject, ex);
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED,
                    "Could not send the email right now. Please try again.");
        }
    }
}