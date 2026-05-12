package io.veriguard.injectors.pcap_replay.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.model.attack_chain_node.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * POJO mirroring {@code inject_content} JSONB for the {@code veriguard_pcap_replay} inject type.
 *
 * <p>Field names mirror spec §6.1 wire format (snake_case).
 */
@Getter
@Setter
public class PcapReplayContent {

  @JsonProperty("pcap_file_id")
  private String pcapFileId;

  @JsonProperty("pcap_target_interface")
  private String targetInterface;

  @JsonProperty("pcap_replay_mode")
  private String replayMode;

  @JsonProperty("pcap_replay_rate")
  private Double replayRate;

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
