package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.rest.helper.queue.Queueable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttackChainNodeExecutionCallback implements Queueable {

  private String id = UUID.randomUUID().toString();

  @JsonProperty("agent_id")
  private String agentId;

  @JsonProperty("inject_id")
  private String attackChainNodeId;

  @JsonProperty("inject_execution_input")
  private AttackChainNodeExecutionInput attackChainNodeExecutionInput;

  @JsonProperty("execution_emission_date")
  private long emissionDate;

  @JsonProperty("retry_count")
  private int retryCount = 0;

  @Override
  public boolean equals(Object o) {
    if (o instanceof AttackChainNodeExecutionCallback) {
      return id != null && id.equals(((AttackChainNodeExecutionCallback) o).getId());
    }
    return false;
  }

  @Override
  public String getUniqueElementKey() {
    return attackChainNodeId;
  }
}
