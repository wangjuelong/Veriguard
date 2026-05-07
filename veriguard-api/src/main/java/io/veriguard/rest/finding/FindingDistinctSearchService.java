package io.veriguard.rest.finding;

import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.database.model.Finding;
import io.veriguard.database.model.TypeValueKey;
import io.veriguard.database.repository.FindingRepository;
import io.veriguard.database.specification.FindingSpecification;
import io.veriguard.rest.finding.form.AggregatedFindingOutput;
import io.veriguard.utils.mapper.FindingMapper;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FindingDistinctSearchService {

  private final FindingRepository findingRepository;
  private final FindingMapper findingMapper;

  public Page<AggregatedFindingOutput> searchDistinctFindings(
      SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (specification, pageable) ->
                findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.forLatestSimulations().and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(FindingSpecification.forLatestSimulations(), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByAttackChainNode(
      String attackChainNodeId, SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.findFindingsForAttackChainNode(attackChainNodeId)
                            .and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForAttackChainNode(attackChainNodeId), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsBySimulation(
      String simulationId, SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.findFindingsForSimulation(simulationId)
                            .and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForSimulation(simulationId), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByAttackChain(
      String attackChainId, SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.findFindingsForAttackChain(attackChainId)
                            .and(FindingSpecification.forLatestSimulations())
                            .and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForAttackChain(attackChainId)
            .and(FindingSpecification.forLatestSimulations()),
        page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByEndpoint(
      String endpointId, SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.findFindingsForEndpoint(endpointId)
                            .and(FindingSpecification.forLatestSimulations())
                            .and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForEndpoint(endpointId)
            .and(FindingSpecification.forLatestSimulations()),
        page);
  }

  public Page<AggregatedFindingOutput> searchDistinctBySpecification(
      Specification<Finding> baseFilterSpec, Page<Finding> page) {

    // Step 1: Extract distinct (type, value) keys
    List<TypeValueKey> typeValueKeys =
        page.getContent().stream()
            .map(f -> new TypeValueKey(f.getType(), f.getValue()))
            .distinct()
            .toList();

    if (typeValueKeys.isEmpty()) {
      return Page.empty(page.getPageable());
    }

    // Step 2: Fetch all findings with assets for those values/types
    List<ContractOutputType> types = typeValueKeys.stream().map(TypeValueKey::getType).toList();
    List<String> values = typeValueKeys.stream().map(TypeValueKey::getValue).toList();

    List<Finding> findingsWithAssets =
        findingRepository.findAll(
            FindingSpecification.findAllWithAssetsByTypeValueIn(types, values, baseFilterSpec));

    // Step 3: Group assets by (type, value)
    Map<TypeValueKey, List<Asset>> groupedAssets =
        findingsWithAssets.stream()
            .filter(f -> typeValueKeys.contains(new TypeValueKey(f.getType(), f.getValue())))
            .flatMap(
                finding ->
                    finding.getAssets().stream()
                        .map(
                            asset ->
                                Map.entry(
                                    new TypeValueKey(finding.getType(), finding.getValue()),
                                    asset)))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    // Step 4: Map page findings + grouped assets to AggregatedFindingOutput
    return page.map(
        finding -> {
          TypeValueKey key = new TypeValueKey(finding.getType(), finding.getValue());
          List<Asset> relatedAssets = groupedAssets.getOrDefault(key, List.of());
          return findingMapper.toAggregatedFindingOutput(finding, relatedAssets);
        });
  }
}
