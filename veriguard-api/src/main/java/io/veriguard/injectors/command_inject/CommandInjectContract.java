package io.veriguard.injectors.command_inject;

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
 * Contractor that registers the {@code veriguard_command_inject} inject type — Task C.11.
 *
 * <p>Command payload is dispatched to a 协作主机 Agent declaring capability {@code command_inject}.
 * Actual shell / PowerShell execution happens agent-side (see {@code wangjuelong/veriguard-agent}
 * fork Mode A transport + capabilities → C1-Agent-2); this contract defines the platform-side form
 * fields only.
 *
 * <p>Wire format (payload_type = {@code "Command"}) follows the existing {@code
 * PayloadType.COMMAND} enum convention (UPPER_SNAKE_CASE Java, PascalCase JSON via {@link
 * io.veriguard.database.model.Command#COMMAND_TYPE}).
 */
@Component
public class CommandInjectContract extends Contractor {

  public static final String TYPE = "veriguard_command_inject";

  public static final String COMMAND_INJECT_DEFAULT = "2e8af9f4-5d63-4f7e-ad8b-fb3d4e5f6a7b";

  public static final String CAPABILITY_COMMAND_INJECT = "command_inject";

  /** Allowed executor shells (mirrors agent-side selection — keep in sync). */
  public static final Set<String> ALLOWED_EXECUTORS = Set.of("bash", "powershell", "sh", "cmd");

  private final List<Contract> contracts;

  private final ContractConfig config;

  public CommandInjectContract() {
    ContractElement teams = teamField(Multiple);
    ContractElement executor =
        textField("command_executor", "Executor shell: bash / powershell / sh / cmd");
    ContractElement commandBody = textareaField("command_content", "Command line to execute");
    ContractElement timeout = textField("command_timeout_seconds", "Timeout (seconds, default 30)");
    ContractElement expectedExitCodes =
        textField("command_expected_exit_codes", "Expected exit codes (CSV, e.g. 0,1)");
    ContractElement expectedOutput =
        textField("command_expected_output_regex", "Expected stdout regex match");
    ContractElement expectations = expectationsField();

    Map<SupportedLanguage, String> label =
        Map.of(en, "Command Inject", fr, "Injection de commande");
    config = new ContractConfig(TYPE, label, "#7a4ec7", "#7a4ec7", "/img/icon-command_inject.png");

    List<ContractElement> fields =
        contractBuilder()
            .mandatory(teams)
            .mandatory(executor)
            .mandatory(commandBody)
            .optional(timeout)
            .optional(expectedExitCodes)
            .optional(expectedOutput)
            .optional(expectations)
            .build();

    contracts =
        List.of(
            executableContract(
                config,
                COMMAND_INJECT_DEFAULT,
                Map.of(
                    en,
                    "Execute shell/PowerShell command via cooperative agent",
                    fr,
                    "Exécuter une commande shell/PowerShell via l'agent coopératif"),
                fields,
                List.of(Endpoint.PLATFORM_TYPE.Generic),
                false,
                Set.of(PresetDomain.ENDPOINT)));
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
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-command_inject.png");
    return new ContractorIcon(iconStream);
  }
}
