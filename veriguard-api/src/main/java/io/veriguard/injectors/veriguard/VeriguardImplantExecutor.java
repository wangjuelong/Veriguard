package io.veriguard.injectors.veriguard;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.model.expectation.DetectionExpectation.*;
import static io.veriguard.model.expectation.ManualExpectation.*;
import static io.veriguard.model.expectation.PreventionExpectation.*;
import static io.veriguard.utils.AgentUtils.getActiveAgents;
import static io.veriguard.utils.ExpectationUtils.*;
import static io.veriguard.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAssetGroup;

import io.veriguard.database.model.*;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutor;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.veriguard.model.VeriguardImplantAttackChainNodeContent;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.DetectionExpectation;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.model.expectation.PreventionExpectation;
import io.veriguard.model.expectation.VulnerabilityExpectation;
import io.veriguard.rest.inject.service.AssetToExecute;
import io.veriguard.rest.inject.service.AttackChainNodeService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.AttackChainNodeExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VeriguardImplantExecutor extends NodeExecutor {

  private final AssetGroupService assetGroupService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final AttackChainNodeService attackChainNodeService;

  public VeriguardImplantExecutor(
      NodeExecutorContext context,
      AssetGroupService assetGroupService,
      AttackChainNodeExpectationService attackChainNodeExpectationService,
      AttackChainNodeService attackChainNodeService) {
    super(context);
    this.assetGroupService = assetGroupService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
    this.attackChainNodeService = attackChainNodeService;
  }

  @Override
  public ExecutionProcess process(Execution execution, ExecutableNode injection)
      throws Exception {
    AttackChainNode attackChainNode = this.attackChainNodeService.attackChainNode(injection.getInjection().getAttackChainNode().getId());

    List<AssetToExecute> assetToExecutes = this.attackChainNodeService.resolveAllAssetsToExecute(attackChainNode);

    // Check assetToExecutes target
    if (assetToExecutes.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "Found 0 asset to execute the ability on (likely this inject does not have any target or the targeted asset is inactive and has been purged)",
              ExecutionTraceAction.COMPLETE));
    }

    // Compute expectations
    VeriguardImplantAttackChainNodeContent content =
        contentConvert(injection, VeriguardImplantAttackChainNodeContent.class);

    List<Expectation> expectations = new ArrayList<>();

    assetToExecutes.forEach(
        assetToExecute ->
            computeExpectationsForAssetAndAgents(expectations, content, assetToExecute, attackChainNode));

    List<AssetGroup> assetGroups = injection.getAssetGroups();
    assetGroups.forEach(
        (assetGroup -> computeExpectationsForAssetGroup(expectations, content, assetGroup)));

    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(injection, expectations);

    return new ExecutionProcess(true);
  }

  // -- PRIVATE --

  /** In case of direct assetToExecute, we have an individual expectation for the assetToExecute */
  private void computeExpectationsForAssetAndAgents(
      @NotNull final List<Expectation> expectations,
      @NotNull final VeriguardImplantAttackChainNodeContent content,
      @NotNull final AssetToExecute assetToExecute,
      final AttackChainNode attackChainNode) {

    if (!content.getExpectations().isEmpty()) {

      Map<String, Endpoint> valueTargetedAssetsMap = attackChainNodeService.getValueTargetedAssetMap(attackChainNode);

      expectations.addAll(
          content.getExpectations().stream()
              .flatMap(
                  expectation ->
                      switch (expectation.getType()) {
                        case PREVENTION ->
                            getPreventionExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), attackChainNode),
                                expectation,
                                valueTargetedAssetsMap,
                                attackChainNode.getId())
                                .stream();
                        case DETECTION ->
                            getDetectionExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), attackChainNode),
                                expectation,
                                valueTargetedAssetsMap,
                                attackChainNode.getId())
                                .stream();
                        case VULNERABILITY ->
                            getVulnerabilityExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), attackChainNode),
                                expectation,
                                valueTargetedAssetsMap,
                                attackChainNode.getId())
                                .stream();
                        case MANUAL ->
                            getManualExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), attackChainNode),
                                expectation)
                                .stream();
                        default -> Stream.of();
                      })
              .toList());
    }
  }

  /**
   * In case of asset group if expectation group -> we have an expectation for the group and one for
   * each asset if not expectation group -> we have an individual expectation for each asset
   */
  private void computeExpectationsForAssetGroup(
      @NotNull final List<Expectation> expectations,
      @NotNull final VeriguardImplantAttackChainNodeContent content,
      @NotNull final AssetGroup assetGroup) {
    if (!content.getExpectations().isEmpty()) {
      expectations.addAll(
          content.getExpectations().stream()
              .flatMap(
                  expectation ->
                      switch (expectation.getType()) {
                        case PREVENTION -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              prevExpectation ->
                                                  AttackChainNodeExpectation.EXPECTATION_TYPE.PREVENTION
                                                      == prevExpectation.type())
                                          .anyMatch(
                                              prevExpectation ->
                                                  ((PreventionExpectation) prevExpectation)
                                                              .getAsset()
                                                          != null
                                                      && ((PreventionExpectation) prevExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                preventionExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case DETECTION -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              detExpectation ->
                                                  AttackChainNodeExpectation.EXPECTATION_TYPE.DETECTION
                                                      == detExpectation.type())
                                          .anyMatch(
                                              detExpectation ->
                                                  ((DetectionExpectation) detExpectation).getAsset()
                                                          != null
                                                      && ((DetectionExpectation) detExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                detectionExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case VULNERABILITY -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              vulExpectation ->
                                                  AttackChainNodeExpectation.EXPECTATION_TYPE.VULNERABILITY
                                                      == vulExpectation.type())
                                          .anyMatch(
                                              vulExpectation ->
                                                  ((VulnerabilityExpectation) vulExpectation)
                                                              .getAsset()
                                                          != null
                                                      && ((VulnerabilityExpectation) vulExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                vulnerabilityExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case MANUAL -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              manExpectation ->
                                                  AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL
                                                      == manExpectation.type())
                                          .anyMatch(
                                              manExpectation ->
                                                  ((ManualExpectation) manExpectation).getAsset()
                                                          != null
                                                      && ((ManualExpectation) manExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                manualExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.getExpirationTime(),
                                    expectation.isExpectationGroup()));
                          }
                          yield Stream.of();
                        }
                        default -> Stream.of();
                      })
              .toList());
    }
  }
}
