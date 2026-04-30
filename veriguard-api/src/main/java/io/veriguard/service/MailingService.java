package io.veriguard.service;

import io.veriguard.config.VeriguardConfig;
import io.veriguard.database.model.User;
import io.veriguard.injectors.email.service.SmtpService;
import jakarta.annotation.Resource;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Lightweight admin notification mailer used for password reset, lessons feedback, and platform
 * notifications. The full attack-simulation Email injector pipeline is removed in 二开 — this
 * service relies on {@link SmtpService} only.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MailingService {

  @Resource private VeriguardConfig veriguardConfig;
  private final SmtpService smtpService;

  public void sendEmail(String subject, String body, List<User> users) {
    if (users == null || users.isEmpty()) {
      return;
    }
    String from = veriguardConfig.getDefaultMailer();
    String replyTo = veriguardConfig.getDefaultReplyTo();
    for (User user : users) {
      if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
        continue;
      }
      try {
        MimeMessage mimeMessage = smtpService.createMimeMessage();
        if (from != null) {
          mimeMessage.setFrom(new InternetAddress(from));
        }
        if (replyTo != null) {
          mimeMessage.setReplyTo(new InternetAddress[] {new InternetAddress(replyTo)});
        }
        mimeMessage.setRecipients(
            Message.RecipientType.TO, new InternetAddress[] {new InternetAddress(user.getEmail())});
        mimeMessage.setSubject(subject, "utf-8");

        MimeMultipart multipart = new MimeMultipart("mixed");
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(body, "text/html;charset=utf-8");
        multipart.addBodyPart(bodyPart);
        mimeMessage.setContent(multipart);

        smtpService.send(mimeMessage);
      } catch (Exception e) {
        log.warn("Failed to send notification mail to {}: {}", user.getEmail(), e.getMessage());
      }
    }
  }
}
