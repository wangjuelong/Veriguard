package io.veriguard.injectors.veriguard;

import static io.veriguard.helper.SupportedLanguage.en;
import static io.veriguard.helper.SupportedLanguage.fr;

import io.veriguard.helper.SupportedLanguage;
import io.veriguard.injector_contract.Contract;
import io.veriguard.injector_contract.ContractConfig;
import io.veriguard.injector_contract.Contractor;
import io.veriguard.injector_contract.ContractorIcon;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VeriguardImplantContract extends Contractor {

  public static final String TYPE = "veriguard_implant";

  @Override
  public String getType() {
    return TYPE;
  }

  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-veriguard.png");
    return new ContractorIcon(iconStream);
  }

  @Override
  public ContractConfig getConfig() {
    Map<SupportedLanguage, String> labels = Map.of(en, "Veriguard Implant", fr, "Veriguard Implant");
    return new ContractConfig(TYPE, labels, "#8b0000", "#8b0000", "/img/icon-veriguard.png");
  }

  @Override
  public List<Contract> contracts() throws Exception {
    return List.of();
  }
}
