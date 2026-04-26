package io.veriguard.injectors.veriguard;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.model.expectation.DetectionExpectation.*;
import static io.veriguard.model.expectation.ManualExpectation.*;
import static io.veriguard.model.expectation.PreventionExpectation.*;
import static io.veriguard.utils.AgentUtils.getActiveAgents;
import static io.veriguard.utils.ExpectationUtils.*;
import static io.veriguard.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAssetGroup;

import io.veriguard.database.model.*;
import io.veriguard.execution.ExecutableInject;
import io.veriguard.executors.Injector;
import io.veriguard.executors.InjectorContext;
import io.veriguard.injectors.veriguard.model.VeriguardImplantInjectContent;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.DetectionExpectation;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.model.expectation.PreventionExpectation;
import io.veriguard.model.expectation.VulnerabilityExpectation;
import io.veriguard.rest.inject.service.AssetToExecute;
import io.veriguard.rest.inject.service.InjectService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VeriguardImplantExecutor extends Injector {

  private final AssetGroupService assetGroupService;
  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;

  public VeriguardImplantExecutor(
      InjectorContext context,
      AssetGroupService assetGroupService,
      InjectExpectationService injectExpectationService,
      InjectService injectService) {
    super(context);
    this.assetGroupService = assetGroupService;
    this.injectExpectationService = injectExpectationService;
    this.injectService = injectService;
  }

  @Override
  public ExecutionProcess process(Execution execution, ExecutableInject injection)
      throws Exception {
    Inject inject = this.injectService.inject(injection.getInjection().getInject().getId());

    List<AssetToExecute> assetToExecutes = this.injectService.resolveAllAssetsToExecute(inject);

    // Check assetToExecutes target
    if (assetToExecutes.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "Found 0 asset to execute the ability on (likely this inject does not have any target or the targeted asset is inactive and has been purged)",
              ExecutionTraceAction.COMPLETE));
    }

    // Compute expectations
    VeriguardImplantInjectContent content =
        contentConvert(injection, VeriguardImplantInjectContent.class);

    List<Expectation> expectations = new ArrayList<>();

    assetToExecutes.forEach(
        assetToExecute ->
            computeExpectationsForAssetAndAgents(expectations, content, assetToExecute, inject));

    List<AssetGroup> assetGroups = injection.getAssetGroups();
    assetGroups.forEach(
        (assetGroup -> computeExpectationsForAssetGroup(expectations, content, assetGroup)));

    injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);

    return new ExecutionProcess(true);
  }

  // -- PRIVATE --

  /** In case of direct assetToExecute, we have an individual expectation for the assetToExecute */
  private void computeExpectationsForAssetAndAgents(
      @NotNull final List<Expectation> expectations,
      @NotNull final VeriguardImplantInjectContent content,
      @NotNull final AssetToExecute assetToExecute,
      final Inject inject) {

    if (!content.getExpectations().isEmpty()) {

      Map<String, Endpoint> valueTargetedAssetsMap = injectService.getValueTargetedAssetMap(inject);

      expectations.addAll(
          content.getExpectations().stream()
              .flatMap(
                  expectation ->
                      switch (expectation.getType()) {
                        case PREVENTION ->
                            getPreventionExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                valueTargetedAssetsMap,
                                inject.getId())
                                .stream();
                        case DETECTION ->
                            getDetectionExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                valueTargetedAssetsMap,
                                inject.getId())
                                .stream();
                        case VULNERABILITY ->
                            getVulnerabilityExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                valueTargetedAssetsMap,
                                inject.getId())
                                .stream();
                        case MANUAL ->
                            getManualExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
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
      @NotNull final VeriguardImplantInjectContent content,
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
                                                  InjectExpectation.EXPECTATION_TYPE.PREVENTION
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
                                                  InjectExpectation.EXPECTATION_TYPE.DETECTION
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
                                                  InjectExpectation.EXPECTATION_TYPE.VULNERABILITY
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
                                                  InjectExpectation.EXPECTATION_TYPE.MANUAL
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
