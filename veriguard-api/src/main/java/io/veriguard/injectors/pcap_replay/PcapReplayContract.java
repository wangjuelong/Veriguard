package io.veriguard.injectors.pcap_replay;

import static io.veriguard.helper.SupportedLanguage.en;
import static io.veriguard.helper.SupportedLanguage.fr;
import static io.veriguard.injector_contract.Contract.executableContract;
import static io.veriguard.injector_contract.ContractCardinality.Multiple;
import static io.veriguard.injector_contract.ContractDef.contractBuilder;
import static io.veriguard.injector_contract.fields.ContractExpectations.expectationsField;
import static io.veriguard.injector_contract.fields.ContractTeam.teamField;
import static io.veriguard.injector_contract.fields.ContractText.textField;

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
 * Contractor that registers the {@code veriguard_pcap_replay} inject type.
 *
 * <p>pcap replay parameters are dispatched to a 协作主机 Agent declaring capability {@code
 * pcap_replay}. Actual tcpreplay execution happens agent-side; this contract defines the
 * platform-side form fields only.
 */
@Component
public class PcapReplayContract extends Contractor {

  public static final String TYPE = "veriguard_pcap_replay";

  public static final String PCAP_REPLAY_DEFAULT = "1d9af8e3-4c52-4e6d-9b7a-fa2c3b4d5e6f";

  public static final String CAPABILITY_PCAP_REPLAY = "pcap_replay";

  private final List<Contract> contracts;

  private final ContractConfig config;

  public PcapReplayContract() {
    ContractElement teams = teamField(Multiple);
    ContractElement pcapFileId = textField("pcap_file_id", "pcap file id (uploaded asset)");
    ContractElement targetInterface =
        textField("pcap_target_interface", "Network interface (e.g. eth0)");
    ContractElement replayMode =
        textField("pcap_replay_mode", "Replay mode: ORIGINAL / MBPS / PPS / MULTIPLIER / TOPSPEED");
    ContractElement replayRate =
        textField(
            "pcap_replay_rate",
            "Rate value (Mbps / pps / multiplier; ignored for ORIGINAL/TOPSPEED)");
    ContractElement expectations = expectationsField();

    Map<SupportedLanguage, String> label = Map.of(en, "PCAP Replay", fr, "Rejeu PCAP");
    config = new ContractConfig(TYPE, label, "#009933", "#009933", "/img/icon-pcap_replay.png");

    List<ContractElement> fields =
        contractBuilder()
            .mandatory(teams)
            .mandatory(pcapFileId)
            .mandatory(targetInterface)
            .mandatory(replayMode)
            .optional(replayRate)
            .optional(expectations)
            .build();

    contracts =
        List.of(
            executableContract(
                config,
                PCAP_REPLAY_DEFAULT,
                Map.of(
                    en,
                    "Replay pcap traffic via cooperative agent (tcpreplay)",
                    fr,
                    "Rejouer le pcap via l'agent coopératif (tcpreplay)"),
                fields,
                List.of(Endpoint.PLATFORM_TYPE.Generic),
                false,
                Set.of(PresetDomain.NETWORK)));
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
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-pcap_replay.png");
    return new ContractorIcon(iconStream);
  }
}
