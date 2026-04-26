package io.veriguard.injectors.ovh;

import static io.veriguard.helper.SupportedLanguage.en;
import static io.veriguard.helper.SupportedLanguage.fr;
import static io.veriguard.injector_contract.Contract.executableContract;
import static io.veriguard.injector_contract.ContractCardinality.Multiple;
import static io.veriguard.injector_contract.ContractDef.contractBuilder;
import static io.veriguard.injector_contract.fields.ContractExpectations.expectationsField;
import static io.veriguard.injector_contract.fields.ContractTeam.teamField;
import static io.veriguard.injector_contract.fields.ContractTextArea.textareaField;

import io.veriguard.database.model.Endpoint;
import io.veriguard.injector_contract.Contract;
import io.veriguard.injector_contract.ContractConfig;
import io.veriguard.injector_contract.Contractor;
import io.veriguard.injector_contract.ContractorIcon;
import io.veriguard.injector_contract.fields.ContractElement;
import io.veriguard.injector_contract.fields.ContractExpectations;
import io.veriguard.rest.domain.enums.PresetDomain;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OvhSmsContract extends Contractor {

  public static final String TYPE = "veriguard_ovh_sms";

  public static final String OVH_DEFAULT = "e9e902bc-b03d-4223-89e1-fca093ac79dd";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(TYPE, Map.of(en, "SMS (OVH)"), "#9c27b0", "#9c27b0", "/img/sms.png");
  }

  @Override
  public List<Contract> contracts() {
    ContractConfig contractConfig = getConfig();
    ContractExpectations expectationsField = expectationsField();
    List<ContractElement> instance =
        contractBuilder()
            .mandatory(teamField(Multiple))
            .mandatory(textareaField("message", "Message"))
            .optional(expectationsField)
            .build();
    return List.of(
        executableContract(
            contractConfig,
            OVH_DEFAULT,
            Map.of(en, "Send a SMS", fr, "Envoyer un SMS"),
            instance,
            List.of(Endpoint.PLATFORM_TYPE.Service),
            false,
            Set.of(PresetDomain.EMAIL_INFILTRATION, PresetDomain.TABLETOP)));
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-ovh-sms.png");
    return new ContractorIcon(iconStream);
  }
}
