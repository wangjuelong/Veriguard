package io.veriguard.injectors.email;

import static io.veriguard.helper.SupportedLanguage.en;
import static io.veriguard.helper.SupportedLanguage.fr;
import static io.veriguard.injector_contract.Contract.executableContract;
import static io.veriguard.injector_contract.ContractCardinality.Multiple;
import static io.veriguard.injector_contract.ContractDef.contractBuilder;
import static io.veriguard.injector_contract.fields.ContractAttachment.attachmentField;
import static io.veriguard.injector_contract.fields.ContractExpectations.expectationsField;
import static io.veriguard.injector_contract.fields.ContractTeam.teamField;
import static io.veriguard.injector_contract.fields.ContractText.textField;
import static io.veriguard.injector_contract.fields.ContractTextArea.richTextareaField;
import static io.veriguard.injector_contract.fields.ContractTextArea.textareaField;

import io.veriguard.database.model.Endpoint;
import io.veriguard.helper.SupportedLanguage;
import io.veriguard.injector_contract.Contract;
import io.veriguard.injector_contract.ContractConfig;
import io.veriguard.injector_contract.Contractor;
import io.veriguard.injector_contract.ContractorIcon;
import io.veriguard.injector_contract.fields.ContractElement;
import io.veriguard.rest.domain.enums.PresetDomain;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Contractor that registers the {@code veriguard_mail} inject type.
 *
 * <p>Defines the contract form fields for sending phishing or notification emails via a configured
 * SMTP profile. The contract is executable (automated) and targets the Generic platform.
 */
@Component
public class EmailContract extends Contractor {

  public static final String TYPE = "veriguard_mail";

  public static final String EMAIL_DEFAULT = "0b78f5a1-2d49-4f5e-a7e1-b3c7f5d9e1a4";

  private final List<Contract> contracts;

  private final ContractConfig config;

  public EmailContract() {
    ContractElement teams = teamField(Multiple);
    ContractElement subject = textField("subject", "Subject");
    ContractElement bodyText = textareaField("body_text", "Body (plain text)");
    ContractElement bodyHtml = richTextareaField("body_html", "Body (HTML)");
    ContractElement fromAlias = textField("from_alias", "From alias (override default sender)");
    ContractElement smtpProfileId = textField("smtp_profile_id", "SMTP profile id");
    ContractElement attachments = attachmentField(Multiple);
    ContractElement expectations = expectationsField();

    Map<SupportedLanguage, String> label = Map.of(en, "Email", fr, "E-mail");
    config = new ContractConfig(TYPE, label, "#1976d2", "#1976d2", "/img/icon-email.png");

    List<ContractElement> fields =
        contractBuilder()
            .mandatory(teams)
            .mandatory(subject)
            .mandatory(smtpProfileId)
            .optional(bodyText)
            .optional(bodyHtml)
            .optional(fromAlias)
            .optional(attachments)
            .optional(expectations)
            .build();

    contracts =
        List.of(
            executableContract(
                config,
                EMAIL_DEFAULT,
                Map.of(en, "Send phishing / notification email", fr, "Envoyer un e-mail"),
                fields,
                List.of(Endpoint.PLATFORM_TYPE.Generic),
                false,
                Set.of(PresetDomain.EMAIL_INFILTRATION)));
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return config;
  }

  @Override
  public List<Contract> contracts() {
    return contracts;
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-email.png");
    return new ContractorIcon(iconStream);
  }
}
