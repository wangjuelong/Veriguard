package io.veriguard.service;

import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE.*;
import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE.VULNERABILITY;
import static io.veriguard.database.model.NodeContract.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.NodeContract;
import io.veriguard.expectation.ExpectationBuilderService;
import io.veriguard.model.inject.form.Expectation;
import io.veriguard.rest.injector_contract.NodeContractService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ExpectationService {

  private final ExpectationBuilderService expectationBuilderService;
  private final NodeContractService nodeContractService;

  @Resource protected ObjectMapper mapper;

  /**
   * Get available expectations for an attackChainNode by nodeExecutor contract id
   *
   * @param nodeContractId nodeExecutor contract id
   * @return available expectations
   */
  public List<Expectation> getAvailableExpectationsForAttackChainNode(String nodeContractId) {
    NodeContract nodeContract =
        nodeContractService.nodeContract(nodeContractId);
    ObjectNode nodeContractContent = nodeContract.getConvertedContent();
    boolean isHumanAttackChainNode = false;
    List<io.veriguard.model.inject.form.Expectation> availableExpectations = new ArrayList<>();
    Iterator<JsonNode> it = nodeContractContent.get(CONTRACT_CONTENT_FIELDS).elements();
    while (it.hasNext()) {
      JsonNode node = it.next();
      if (node.get(CONTRACT_ELEMENT_CONTENT_KEY)
          .asText()
          .equals(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS)) {
        isHumanAttackChainNode = true;
      }
      if (node.get(CONTRACT_ELEMENT_CONTENT_KEY)
          .asText()
          .equals(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS)) {
        try {
          availableExpectations =
              mapper.readValue(
                  node.get(PREDEFINED_EXPECTATIONS).traverse(), new TypeReference<>() {});
        } catch (Exception e) {
          log.error(
              "Can't access to content predefinedExpectations for injector contract id {}",
              nodeContractId,
              e);
        }
      }
    }
    if (isHumanAttackChainNode) {
      if (availableExpectations.stream()
          .noneMatch(expectation -> expectation.getType().equals(MANUAL))) {
        availableExpectations.add(expectationBuilderService.buildManualExpectation());
      }
    } else {
      if (availableExpectations.stream()
          .noneMatch(expectation -> expectation.getType().equals(DETECTION))) {
        availableExpectations.add(expectationBuilderService.buildDetectionExpectation());
      }
      if (availableExpectations.stream()
          .noneMatch(expectation -> expectation.getType().equals(PREVENTION))) {
        availableExpectations.add(expectationBuilderService.buildPreventionExpectation());
      }
      if (availableExpectations.stream()
          .noneMatch(expectation -> expectation.getType().equals(VULNERABILITY))) {
        availableExpectations.add(expectationBuilderService.buildVulnerabilityExpectation());
      }
    }
    return availableExpectations;
  }
}
