package io.veriguard.injectors.manual;

import static io.veriguard.helper.SupportedLanguage.en;
import static io.veriguard.helper.SupportedLanguage.fr;
import static io.veriguard.injector_contract.Contract.manualContract;
import static io.veriguard.injector_contract.ContractCardinality.Multiple;
import static io.veriguard.injector_contract.ContractDef.contractBuilder;
import static io.veriguard.injector_contract.fields.ContractExpectations.expectationsField;
import static io.veriguard.injector_contract.fields.ContractTeam.teamField;

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

@Component
public class ManualContract extends Contractor {
  public static final String TYPE = "veriguard_manual";

  public static final String MANUAL_DEFAULT = "d02e9132-b9d0-4daa-b3b1-4b9871f8472c";

  private final List<Contract> contracts;

  private final ContractConfig config;

  public ManualContract() {

    ContractElement teams = teamField(Multiple);
    ContractElement expectations = expectationsField();

    Map<SupportedLanguage, String> label = Map.of(en, "Manual", fr, "Manuel");
    config = new ContractConfig(TYPE, label, "#009688", "#009688", "/img/manual.png");

    List<ContractElement> instance =
        contractBuilder().mandatoryOnCondition(teams, expectations).optional(expectations).build();
    contracts =
        List.of(
            manualContract(
                config,
                MANUAL_DEFAULT,
                Map.of(en, "Manual", fr, "Manuel"),
                instance,
                List.of(Endpoint.PLATFORM_TYPE.Internal),
                false,
                Set.of(PresetDomain.EMAIL_INFILTRATION, PresetDomain.TABLETOP)));
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
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-manual.png");
    return new ContractorIcon(iconStream);
  }
}
