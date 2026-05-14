package io.veriguard.injectors.command_inject.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.model.attack_chain_node.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * POJO mirroring {@code inject_content} JSONB for the {@code veriguard_command_inject} inject type
 * (Task C.11).
 *
 * <p>Field names mirror the wire format (snake_case). {@code command_executor} is the shell choice
 * (bash / powershell / sh / cmd), validated by {@link
 * io.veriguard.injectors.command_inject.service.CommandInjectDispatchService}.
 */
@Getter
@Setter
public class CommandInjectContent {

  @JsonProperty("command_executor")
  private String executor;

  @JsonProperty("command_content")
  private String content;

  @JsonProperty("command_timeout_seconds")
  private int timeoutSeconds = 30;

  @JsonProperty("command_expected_exit_codes")
  private List<Integer> expectedExitCodes = new ArrayList<>();

  @JsonProperty("command_expected_output_regex")
  private String expectedOutputRegex;

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
