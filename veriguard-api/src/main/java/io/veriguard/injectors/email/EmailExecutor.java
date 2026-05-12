package io.veriguard.injectors.email;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.database.model.ExecutionTrace.getNewSuccessTrace;

import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.database.model.SmtpProfile;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutor;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.email.model.EmailContent;
import io.veriguard.injectors.email.service.MailInjector;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.SmtpProfileService;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * NodeExecutor 子类，处理 Email inject（B-ii PR-B）.
 *
 * <p>process() 流程：
 *
 * <ol>
 *   <li>反序列化 content → EmailContent
 *   <li>校验 smtp_profile_id 存在，查 SmtpProfile
 *   <li>从 injection.getUsers() 提取收件人邮箱；无有效邮箱则报错
 *   <li>逐个收件人构建 MimeMessage + 发送（单个失败不终止其余发送）
 *   <li>保存 ManualExpectation（via AttackChainNodeExpectationService）
 *   <li>全部成功 → success trace；有失败 → error trace 含失败明细
 * </ol>
 */
@Slf4j
public class EmailExecutor extends NodeExecutor {

  private final SmtpProfileService smtpProfileService;
  private final MailInjector mailInjector;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public EmailExecutor(
      NodeExecutorContext context,
      SmtpProfileService smtpProfileService,
      MailInjector mailInjector,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(context);
    this.smtpProfileService = smtpProfileService;
    this.mailInjector = mailInjector;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableNode injection)
      throws Exception {

    EmailContent content = contentConvert(injection, EmailContent.class);

    // 1) Validate smtp_profile_id present
    if (content.getSmtpProfileId() == null || content.getSmtpProfileId().isBlank()) {
      execution.addTrace(
          getNewErrorTrace("smtp_profile_id is required", ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }

    // 2) Look up SmtpProfile; not-found → error trace
    Optional<SmtpProfile> profileOpt = smtpProfileService.findById(content.getSmtpProfileId());
    if (profileOpt.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "SMTP profile not found: " + content.getSmtpProfileId(),
              ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }
    SmtpProfile profile = profileOpt.get();

    // 3) Extract recipient emails from injection users (player context)
    List<String> recipients = extractUserEmails(injection);
    if (recipients.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "No valid recipient emails found for inject", ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }

    // 4) Send per-recipient; collect failures but continue
    List<String> failures = new ArrayList<>();
    for (String recipient : recipients) {
      try {
        MimeMessage message =
            mailInjector.buildMimeMessage(
                profile,
                content.getFromAlias(),
                content.getSubject(),
                content.getBodyText(),
                content.getBodyHtml(),
                List.of(recipient));
        mailInjector.send(profile, message);
        log.debug("Email sent to {}", recipient);
      } catch (Exception e) {
        log.warn("Failed to send email to {}: {}", recipient, e.getMessage());
        failures.add(recipient + ": " + e.getMessage());
      }
    }

    // 5) Save ManualExpectation entries
    List<Expectation> expectations =
        content.getExpectations().stream()
            .flatMap(
                entry ->
                    switch (entry.getType()) {
                      case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                      default -> Stream.of();
                    })
            .toList();
    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(
        injection, expectations);

    // 6) Trace result
    if (failures.isEmpty()) {
      execution.addTrace(
          getNewSuccessTrace(
              "Email inject sent to " + recipients.size() + " recipient(s)",
              ExecutionTraceAction.COMPLETE));
    } else {
      String detail = String.join("; ", failures);
      execution.addTrace(
          getNewErrorTrace(
              "Email inject completed with " + failures.size() + " failure(s): " + detail,
              ExecutionTraceAction.COMPLETE));
    }

    return new ExecutionProcess(false);
  }

  /**
   * Extracts unique, non-blank email addresses from the inject's execution-context user list.
   *
   * <p>injection.getUsers() returns List&lt;ExecutionContext&gt;; each context wraps a ProtectUser
   * via getUser().getEmail().
   */
  private List<String> extractUserEmails(ExecutableNode injection) {
    return injection.getUsers().stream()
        .map(ctx -> ctx.getUser().getEmail())
        .filter(email -> email != null && !email.isBlank())
        .distinct()
        .toList();
  }
}
