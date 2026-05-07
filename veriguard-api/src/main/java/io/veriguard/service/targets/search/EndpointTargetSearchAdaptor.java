package io.veriguard.service.targets.search;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.EndpointRepository;
import io.veriguard.service.targets.search.specifications.IncludeDirectEndpointTargetsSpecification;
import io.veriguard.service.targets.search.specifications.IncludeMembersOfAssetGroupsSpecification;
import io.veriguard.service.targets.search.specifications.SearchSpecificationUtils;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class EndpointTargetSearchAdaptor extends SearchAdaptorBase {

  private final EndpointRepository endpointRepository;
  private final SearchSpecificationUtils<Endpoint> searchSpecificationUtils;
  private final IncludeMembersOfAssetGroupsSpecification<Endpoint>
      includeMembersOfAssetGroupsSpecification;
  private final IncludeDirectEndpointTargetsSpecification<Endpoint>
      includeDirectEndpointTargetsSpecification;
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  private final List<String> joinPath = List.of("assets");

  public EndpointTargetSearchAdaptor(
      EndpointRepository endpointRepository,
      SearchSpecificationUtils<Endpoint> searchSpecificationUtils,
      IncludeMembersOfAssetGroupsSpecification<Endpoint> includeMembersOfAssetGroupsSpecification,
      IncludeDirectEndpointTargetsSpecification<Endpoint> includeDirectEndpointTargetsSpecification,
      HelperTargetSearchAdaptor helperTargetSearchAdaptor) {
    this.endpointRepository = endpointRepository;
    this.helperTargetSearchAdaptor = helperTargetSearchAdaptor;
    this.searchSpecificationUtils = searchSpecificationUtils;
    this.includeMembersOfAssetGroupsSpecification = includeMembersOfAssetGroupsSpecification;
    this.includeDirectEndpointTargetsSpecification = includeDirectEndpointTargetsSpecification;
    // field name translations
    this.fieldTranslations.put("target_name", "asset_name");
    this.fieldTranslations.put("target_tags", "asset_tags");
  }

  @Override
  public Page<AttackChainNodeTarget> search(
      SearchPaginationInput input, @NotNull AttackChainNode scopedAttackChainNode) {
    Specification<Endpoint> overallSpec =
        searchSpecificationUtils.compileSpecificationForAssetGroupMembership(
            scopedAttackChainNode, input, joinPath);

    Specification<Endpoint> memberOfAnyTargetGroupSpec =
        searchSpecificationUtils.compileSpecificationForAssetGroupMembership(
            scopedAttackChainNode,
            SearchPaginationInput.builder().filterGroup(new Filters.FilterGroup()).build(),
            joinPath);

    SearchPaginationInput translatedInput = this.translate(input, scopedAttackChainNode);

    Page<Endpoint> eps =
        buildPaginationJPA(
            (Specification<Endpoint> specification, Pageable pageable) -> {
              if (Filters.FilterMode.and.equals(input.getFilterGroup().getMode())) {
                return this.endpointRepository.findAll(overallSpec.and(specification), pageable);
              }
              return this.endpointRepository.findAll(
                  overallSpec.or(specification.and(memberOfAnyTargetGroupSpec)), pageable);
            },
            translatedInput,
            Endpoint.class);

    return new PageImpl<>(
        eps.getContent().stream()
            .map(endpoint -> convertFromEndpoint(endpoint, scopedAttackChainNode))
            .toList(),
        eps.getPageable(),
        eps.getTotalElements());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForAttackChainNode(
      AttackChainNode scopedAttackChainNode, String textSearch) {
    Specification<Endpoint> spec =
        includeMembersOfAssetGroupsSpecification
            .buildSpecification(
                scopedAttackChainNode.getAssetGroups().stream().map(AssetGroup::getId).toList(),
                joinPath)
            .or(
                includeDirectEndpointTargetsSpecification.buildSpecification(
                    scopedAttackChainNode, joinPath));

    Specification<Endpoint> nameSpec =
        (root, query, criteriaBuilder) ->
            criteriaBuilder.like(root.get("name"), "%" + textSearch + "%");

    return this.endpointRepository.findAll(spec.and(nameSpec)).stream()
        .map(ep -> new FilterUtilsJpa.Option(ep.getId(), ep.getName()))
        .toList();
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    return fromIterable(this.endpointRepository.findAllById(ids)).stream()
        .map(ep -> new FilterUtilsJpa.Option(ep.getId(), ep.getName()))
        .toList();
  }

  private AttackChainNodeTarget convertFromEndpoint(
      Endpoint endpoint, AttackChainNode attackChainNode) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        attackChainNode,
        () ->
            new EndpointTarget(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                endpoint.getPlatform().name()),
        true);
  }
}
