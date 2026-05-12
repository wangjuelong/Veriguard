package io.veriguard.injectors.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.database.model.SmtpProfile;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailInjectorTest {

  private SmtpProfile profile() {
    SmtpProfile p = new SmtpProfile();
    p.setName("test");
    p.setHost("smtp.example.com");
    p.setPort(587);
    p.setAuthType(SmtpProfile.AUTH_TYPE.PASSWORD);
    p.setUsername("u");
    p.setPassword("p");
    p.setTlsMode(SmtpProfile.TLS_MODE.STARTTLS);
    p.setDefaultFrom("noreply@example.com");
    return p;
  }

  @Test
  @DisplayName("buildMimeMessage 设置主题 / from / to / 正文")
  void buildMimeMessage_setsAllFields() throws Exception {
    MailInjector injector = new MailInjector();
    MimeMessage msg =
        injector.buildMimeMessage(
            profile(), null, "Subject Test", "Body text", null, List.of("victim@example.com"));

    assertThat(msg.getSubject()).isEqualTo("Subject Test");
    assertThat(msg.getContent()).isEqualTo("Body text");
    Address[] from = msg.getFrom();
    assertThat(from).hasSize(1);
    assertThat(((InternetAddress) from[0]).getAddress()).isEqualTo("noreply@example.com");
    Address[] to = msg.getRecipients(Message.RecipientType.TO);
    assertThat(to).hasSize(1);
    assertThat(((InternetAddress) to[0]).getAddress()).isEqualTo("victim@example.com");
  }

  @Test
  @DisplayName("from_alias 覆盖 default_from")
  void fromAlias_overridesDefault() throws Exception {
    MailInjector injector = new MailInjector();
    MimeMessage msg =
        injector.buildMimeMessage(
            profile(),
            "IT Support <support@phish.com>",
            "Subject",
            "Body",
            null,
            List.of("victim@example.com"));

    Address[] from = msg.getFrom();
    assertThat(((InternetAddress) from[0]).getAddress()).isEqualTo("support@phish.com");
    assertThat(((InternetAddress) from[0]).getPersonal()).isEqualTo("IT Support");
  }

  @Test
  @DisplayName("html body → content type text/html")
  void htmlBody_setsHtmlContentType() throws Exception {
    MailInjector injector = new MailInjector();
    MimeMessage msg =
        injector.buildMimeMessage(
            profile(), null, "S", null, "<b>Hi</b>", List.of("v@example.com"));

    assertThat(msg.getContentType()).startsWith("text/html");
    assertThat(msg.getContent()).isEqualTo("<b>Hi</b>");
  }

  @Test
  @DisplayName("空收件人 → 抛 IllegalArgumentException")
  void emptyRecipients_throws() {
    MailInjector injector = new MailInjector();

    assertThatThrownBy(() -> injector.buildMimeMessage(profile(), null, "S", "B", null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recipient");
  }

  @Test
  @DisplayName("空 subject → 抛 IllegalArgumentException")
  void blankSubject_throws() {
    MailInjector injector = new MailInjector();

    assertThatThrownBy(
            () ->
                injector.buildMimeMessage(
                    profile(), null, " ", "B", null, List.of("v@example.com")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("subject");
  }

  @Test
  @DisplayName("body_text 与 body_html 都为空 → 抛 IllegalArgumentException")
  void bothBodiesEmpty_throws() {
    MailInjector injector = new MailInjector();

    assertThatThrownBy(
            () ->
                injector.buildMimeMessage(
                    profile(), null, "S", null, null, List.of("v@example.com")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("body");
  }
}
