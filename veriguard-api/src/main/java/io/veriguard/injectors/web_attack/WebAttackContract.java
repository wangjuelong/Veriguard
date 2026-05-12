package io.veriguard.injectors.web_attack;

import static io.veriguard.helper.SupportedLanguage.en;
import static io.veriguard.helper.SupportedLanguage.fr;
import static io.veriguard.injector_contract.Contract.executableContract;
import static io.veriguard.injector_contract.ContractCardinality.Multiple;
import static io.veriguard.injector_contract.ContractDef.contractBuilder;
import static io.veriguard.injector_contract.fields.ContractExpectations.expectationsField;
import static io.veriguard.injector_contract.fields.ContractTeam.teamField;
import static io.veriguard.injector_contract.fields.ContractText.textField;
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
 * Contractor that registers the {@code veriguard_web_attack} inject type.
 *
 * <p>HTTP request payload is dispatched to a 协作主机 Agent declaring capability {@code http_attack}.
 * Actual HTTP issuance happens agent-side; this contract defines the platform-side form fields
 * only.
 */
@Component
public class WebAttackContract extends Contractor {

  public static final String TYPE = "veriguard_web_attack";

  public static final String WEB_ATTACK_DEFAULT = "0c89e7d2-3b41-4c5d-8a6e-9f1b2c3d4e5f";

  public static final String CAPABILITY_HTTP_ATTACK = "http_attack";

  private final List<Contract> contracts;

  private final ContractConfig config;

  public WebAttackContract() {
    ContractElement teams = teamField(Multiple);
    ContractElement method = textField("web_request_method", "HTTP method (GET/POST/PUT/...)");
    ContractElement url = textField("web_request_url", "Request URL");
    ContractElement headers = textareaField("web_request_headers", "Headers (JSON array)");
    ContractElement body = textareaField("web_request_body", "Request body");
    ContractElement bodyType =
        textField("web_request_body_type", "Body content type (text/json/form/multipart)");
    ContractElement cookies = textField("web_request_cookies", "Cookies header value");
    ContractElement timeout =
        textField("web_request_timeout_seconds", "Timeout (seconds, default 30)");
    ContractElement expectedStatus =
        textField("expected_status_codes", "Expected HTTP status codes (CSV, e.g. 200,302)");
    ContractElement expectedBody = textField("expected_body_regex", "Expected body regex match");
    ContractElement expectations = expectationsField();

    Map<SupportedLanguage, String> label = Map.of(en, "Web Attack", fr, "Attaque Web");
    config = new ContractConfig(TYPE, label, "#d32f2f", "#d32f2f", "/img/icon-web_attack.png");

    List<ContractElement> fields =
        contractBuilder()
            .mandatory(teams)
            .mandatory(method)
            .mandatory(url)
            .optional(headers)
            .optional(body)
            .optional(bodyType)
            .optional(cookies)
            .optional(timeout)
            .optional(expectedStatus)
            .optional(expectedBody)
            .optional(expectations)
            .build();

    contracts =
        List.of(
            executableContract(
                config,
                WEB_ATTACK_DEFAULT,
                Map.of(
                    en,
                    "Send HTTP attack via cooperative agent",
                    fr,
                    "Envoyer une attaque HTTP via agent coopératif"),
                fields,
                List.of(Endpoint.PLATFORM_TYPE.Generic),
                false,
                Set.of(PresetDomain.WEB_APP)));
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
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-web_attack.png");
    return new ContractorIcon(iconStream);
  }
}
