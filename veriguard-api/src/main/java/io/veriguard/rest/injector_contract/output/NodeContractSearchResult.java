package io.veriguard.rest.injector_contract.output;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class NodeContractSearchResult {
  private List<NodeContractFullOutput> contracts;
  private Map<String, Long> nodeContractDomainCounts;

  public NodeContractSearchResult(
      List<NodeContractFullOutput> contracts, Map<String, Long> domainCounts) {
    this.contracts = contracts;
    this.nodeContractDomainCounts = domainCounts;
  }
}
