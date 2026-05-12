package io.veriguard.injectors.email.service;

import io.veriguard.database.model.SmtpProfile;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mail inject 专用发送服务（B-ii PR-B）.
 *
 * <p>独立于 {@code SmtpService}（后者绑定单一全局 SMTP 用于平台通知），按 SmtpProfile 动态构造 jakarta.mail Session 与
 * Transport，支持每个 inject 选用不同 profile.
 */
@Service
@Slf4j
public class MailInjector {

  /** 构造 MimeMessage 但不发送（便于单测验证消息结构 + 拆分发送步骤）. */
  public MimeMessage buildMimeMessage(
      SmtpProfile profile,
      String fromAlias,
      String subject,
      String bodyText,
      String bodyHtml,
      List<String> recipients)
      throws Exception {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Mail subject is required");
    }
    boolean hasText = bodyText != null && !bodyText.isBlank();
    boolean hasHtml = bodyHtml != null && !bodyHtml.isBlank();
    if (!hasText && !hasHtml) {
      throw new IllegalArgumentException("Mail body (text or html) is required");
    }
    if (recipients == null || recipients.isEmpty()) {
      throw new IllegalArgumentException("Mail recipient list cannot be empty");
    }

    Session session = createSession(profile);
    MimeMessage msg = new MimeMessage(session);

    InternetAddress from =
        fromAlias != null && !fromAlias.isBlank()
            ? new InternetAddress(fromAlias)
            : new InternetAddress(profile.getDefaultFrom());
    msg.setFrom(from);

    if (profile.getDefaultReplyTo() != null && !profile.getDefaultReplyTo().isBlank()) {
      msg.setReplyTo(new InternetAddress[] {new InternetAddress(profile.getDefaultReplyTo())});
    }

    InternetAddress[] toAddrs = new InternetAddress[recipients.size()];
    for (int i = 0; i < recipients.size(); i++) {
      toAddrs[i] = new InternetAddress(recipients.get(i));
    }
    msg.setRecipients(Message.RecipientType.TO, toAddrs);

    msg.setSubject(subject, "utf-8");

    if (hasHtml) {
      msg.setContent(bodyHtml, "text/html; charset=utf-8");
    } else {
      msg.setText(bodyText, "utf-8");
    }
    msg.saveChanges();
    return msg;
  }

  /** 发送已构造好的 MimeMessage（独立步骤，便于失败处理 / 重试）. */
  public void send(SmtpProfile profile, MimeMessage message) throws Exception {
    Session session = message.getSession();
    try (Transport transport = session.getTransport()) {
      if (profile.getAuthType() == SmtpProfile.AUTH_TYPE.PASSWORD) {
        transport.connect(
            profile.getHost(), profile.getPort(), profile.getUsername(), profile.getPassword());
      } else {
        transport.connect(profile.getHost(), profile.getPort(), null, null);
      }
      transport.sendMessage(message, message.getAllRecipients());
    }
  }

  private Session createSession(SmtpProfile profile) {
    Properties props = new Properties();
    props.put("mail.smtp.host", profile.getHost());
    props.put("mail.smtp.port", String.valueOf(profile.getPort()));
    props.put("mail.smtp.auth", profile.getAuthType() == SmtpProfile.AUTH_TYPE.PASSWORD);
    switch (profile.getTlsMode()) {
      case STARTTLS -> props.put("mail.smtp.starttls.enable", true);
      case TLS -> props.put("mail.smtp.ssl.enable", true);
      case NONE -> {
        /* no-op */
      }
    }
    return Session.getInstance(props);
  }
}
