package io.veriguard.rest.finding;

import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.Finding;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.repository.FindingRepository;
import io.veriguard.database.specification.FindingSpecification;
import io.veriguard.rest.finding.form.AggregatedFindingOutput;
import io.veriguard.rest.finding.form.PageAggregatedFindingOutput;
import io.veriguard.rest.finding.form.PageRelatedFindingOutput;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.utils.mapper.FindingMapper;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(FindingApi.FINDING_URI)
@RequiredArgsConstructor
public class FindingSearchApi extends RestBehavior {

  private final FindingRepository findingRepository;
  private final FindingDistinctSearchService findingDistinctSearchService;

  private final FindingMapper findingMapper;

  @LogExecutionTime
  @PostMapping("/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.FINDING)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              schema =
                  @Schema(
                      oneOf = {PageAggregatedFindingOutput.class, PageRelatedFindingOutput.class})))
  public Page<AggregatedFindingOutput> findings(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @RequestParam(value = "distinct", required = false, defaultValue = "false")
          boolean distinct) {
    if (distinct) {
      return findingDistinctSearchService.searchDistinctFindings(searchPaginationInput);
    }
    return buildPaginationJPA(
            (specification, pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.forLatestSimulations().and(specification), pageable),
            searchPaginationInput,
            Finding.class)
        .map(findingMapper::toRelatedFindingOutput);
  }

  @LogExecutionTime
  @PostMapping("/injects/{injectId}/search")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              schema =
                  @Schema(
                      oneOf = {PageAggregatedFindingOutput.class, PageRelatedFindingOutput.class})))
  public Page<AggregatedFindingOutput> findingsByAttackChainNode(
      @PathVariable @NotNull final String attackChainNodeId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @RequestParam(value = "distinct", required = false, defaultValue = "false")
          boolean distinct) {
    if (distinct) {
      return findingDistinctSearchService.searchDistinctFindingsByAttackChainNode(
          attackChainNodeId, searchPaginationInput);
    }
    return buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.findFindingsForAttackChainNode(attackChainNodeId).and(specification),
                    pageable),
            searchPaginationInput,
            Finding.class)
        .map(findingMapper::toRelatedFindingOutput);
  }

  @LogExecutionTime
  @PostMapping("/exercises/{simulationId}/search")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              schema =
                  @Schema(
                      oneOf = {PageAggregatedFindingOutput.class, PageRelatedFindingOutput.class})))
  public Page<AggregatedFindingOutput> findingsBySimulation(
      @PathVariable @NotNull final String simulationId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @RequestParam(value = "distinct", required = false, defaultValue = "false")
          boolean distinct) {
    if (distinct) {
      return findingDistinctSearchService.searchDistinctFindingsBySimulation(
          simulationId, searchPaginationInput);
    }
    return buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.findFindingsForSimulation(simulationId).and(specification),
                    pageable),
            searchPaginationInput,
            Finding.class)
        .map(findingMapper::toRelatedFindingOutput);
  }

  @LogExecutionTime
  @PostMapping("/scenarios/{scenarioId}/search")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              schema =
                  @Schema(
                      oneOf = {PageAggregatedFindingOutput.class, PageRelatedFindingOutput.class})))
  public Page<AggregatedFindingOutput> findingsByAttackChain(
      @PathVariable @NotNull final String attackChainId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @RequestParam(value = "distinct", required = false, defaultValue = "false")
          boolean distinct) {
    if (distinct) {
      return findingDistinctSearchService.searchDistinctFindingsByAttackChain(
          attackChainId, searchPaginationInput);
    }
    return buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.findFindingsForAttackChain(attackChainId)
                        .and(FindingSpecification.forLatestSimulations())
                        .and(specification),
                    pageable),
            searchPaginationInput,
            Finding.class)
        .map(findingMapper::toRelatedFindingOutput);
  }

  @LogExecutionTime
  @PostMapping("/endpoints/{endpointId}/search")
  @RBAC(
      resourceId = "#endpointId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              schema =
                  @Schema(
                      oneOf = {PageAggregatedFindingOutput.class, PageRelatedFindingOutput.class})))
  public Page<AggregatedFindingOutput> findingsByEndpoint(
      @PathVariable @NotNull final String endpointId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @RequestParam(value = "distinct", required = false, defaultValue = "false")
          boolean distinct) {
    if (distinct) {
      return findingDistinctSearchService.searchDistinctFindingsByEndpoint(
          endpointId, searchPaginationInput);
    }
    return buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.findFindingsForEndpoint(endpointId)
                        .and(FindingSpecification.forLatestSimulations())
                        .and(specification),
                    pageable),
            searchPaginationInput,
            Finding.class)
        .map(findingMapper::toRelatedFindingOutput);
  }
}
