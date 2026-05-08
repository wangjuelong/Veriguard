package io.veriguard.rest.asset_group;

import static io.veriguard.database.specification.AssetGroupSpecification.fromIds;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.AssetGroup;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.repository.AssetGroupRepository;
import io.veriguard.database.repository.TagRepository;
import io.veriguard.rest.asset.endpoint.form.EndpointOutput;
import io.veriguard.rest.asset_group.form.AssetGroupInput;
import io.veriguard.rest.asset_group.form.AssetGroupOutput;
import io.veriguard.rest.asset_group.form.UpdateAssetsOnAssetGroupInput;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.EndpointService;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.InputFilterOptions;
import io.veriguard.utils.mapper.EndpointMapper;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AssetGroupApi extends RestBehavior {

  public static final String ASSET_GROUP_URI = "/api/asset_groups";
  private final EndpointService endpointService;
  private final EndpointMapper endpointMapper;

  private final AssetGroupService assetGroupService;
  private final AssetGroupCriteriaBuilderService assetGroupCriteriaBuilderService;
  private final TagRepository tagRepository;
  private final AssetGroupRepository assetGroupRepository;

  @PostMapping(ASSET_GROUP_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public AssetGroup createAssetGroup(@Valid @RequestBody final AssetGroupInput input) {
    AssetGroup assetGroup = new AssetGroup();
    assetGroup.setUpdateAttributes(input);
    assetGroup.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.assetGroupService.createAssetGroup(assetGroup);
  }

  @GetMapping(ASSET_GROUP_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.ASSET_GROUP)
  public List<AssetGroup> assetGroups() {
    return this.assetGroupService.assetGroups();
  }

  @LogExecutionTime
  @PostMapping(ASSET_GROUP_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public Page<AssetGroupOutput> assetGroups(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return this.assetGroupCriteriaBuilderService.assetGroupPagination(searchPaginationInput);
  }

  @PostMapping(ASSET_GROUP_URI + "/{assetGroupId}/assets/search")
  @RBAC(
      resourceId = "#assetGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET_GROUP)
  public Page<EndpointOutput> endpointsFromAssetGroup(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      @PathVariable @NotBlank final String assetGroupId) {

    Page<Endpoint> endpointPage =
        endpointService.searchManagedEndpointsByAssetGroup(assetGroupId, searchPaginationInput);
    // Convert the Page of Endpoint to a Page of EndpointOutput
    List<EndpointOutput> endpointOutputs =
        endpointPage.getContent().stream()
            .map(
                endpoint -> {
                  Boolean isPresent =
                      endpoint.getAssetGroups().stream()
                          .map(AssetGroup::getId)
                          .anyMatch(id -> Objects.equals(id, assetGroupId));
                  EndpointOutput endpointOutput = endpointMapper.toEndpointOutput(endpoint);
                  endpointOutput.setIsStatic(isPresent);
                  return endpointOutput;
                })
            .toList();
    return new PageImpl<>(
        endpointOutputs, endpointPage.getPageable(), endpointPage.getTotalElements());
  }

  @PostMapping(ASSET_GROUP_URI + "/find")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  @Transactional(readOnly = true)
  public List<AssetGroupOutput> findAssetGroups(
      @RequestBody @Valid @NotNull final List<String> assetGroupIds) {
    return this.assetGroupCriteriaBuilderService.find(fromIds(assetGroupIds));
  }

  @GetMapping(ASSET_GROUP_URI + "/{assetGroupId}")
  @RBAC(
      resourceId = "#assetGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET_GROUP)
  public AssetGroup assetGroup(@PathVariable @NotBlank final String assetGroupId) {
    return this.assetGroupService.assetGroup(assetGroupId);
  }

  @PutMapping(ASSET_GROUP_URI + "/{assetGroupId}")
  @RBAC(
      resourceId = "#assetGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public AssetGroup updateAssetGroup(
      @PathVariable @NotBlank final String assetGroupId,
      @Valid @RequestBody final AssetGroupInput input) {
    AssetGroup assetGroup = this.assetGroupService.assetGroup(assetGroupId);
    assetGroup.setUpdateAttributes(input);
    assetGroup.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.assetGroupService.updateAssetGroup(assetGroup);
  }

  @PutMapping(ASSET_GROUP_URI + "/{assetGroupId}/assets")
  @RBAC(
      resourceId = "#assetGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public AssetGroup updateAssetsOnAssetGroup(
      @PathVariable @NotBlank final String assetGroupId,
      @Valid @RequestBody final UpdateAssetsOnAssetGroupInput input) {
    AssetGroup assetGroup = this.assetGroupService.assetGroup(assetGroupId);
    return this.assetGroupService.updateAssetsOnAssetGroup(assetGroup, input.getAssetIds());
  }

  @DeleteMapping(ASSET_GROUP_URI + "/{assetGroupId}")
  @RBAC(
      resourceId = "#assetGroupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ASSET_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public void deleteAssetGroup(@PathVariable @NotBlank final String assetGroupId) {
    try {
      assetGroupService.assetGroup(assetGroupId);
    } catch (IllegalArgumentException ex) {
      throw new ElementNotFoundException(ex.getMessage());
    }
    this.assetGroupService.deleteAssetGroup(assetGroupId);
  }

  // -- OPTION --

  @GetMapping(ASSET_GROUP_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId,
      @RequestParam(required = false) final String inputFilterOption) {
    List<FilterUtilsJpa.Option> options = List.of();
    InputFilterOptions attackChainNodeFilterOptionEnum;
    try {
      attackChainNodeFilterOptionEnum = InputFilterOptions.valueOf(inputFilterOption);
    } catch (Exception e) {
      if (StringUtils.isEmpty(inputFilterOption)) {
        log.warn("InputFilterOption is null, fall back to backwards compatible case");
        if (StringUtils.isNotEmpty(sourceId)) {
          attackChainNodeFilterOptionEnum = InputFilterOptions.SIMULATION_OR_SCENARIO;
        } else {
          attackChainNodeFilterOptionEnum = InputFilterOptions.ATOMIC_TESTING;
        }
      } else {
        throw new BadRequestException(
            String.format("Invalid input filter option %s", inputFilterOption));
      }
    }
    switch (attackChainNodeFilterOptionEnum) {
      case ALL_INJECTS:
        {
          options =
              assetGroupRepository
                  .findAllAssetGroupsForAtomicTestingsSimulationsAndAttackChains()
                  .stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .distinct()
                  .toList();
          break;
        }
      case SIMULATION_OR_SCENARIO:
        {
          if (StringUtils.isEmpty(sourceId)) {
            throw new BadRequestException("Missing simulation or scenario id");
          }
          // fall through intentional
        }
      case ATOMIC_TESTING:
        {
          options =
              assetGroupRepository
                  .findAllBySimulationOrAttackChainIdAndName(
                      StringUtils.trimToNull(sourceId), StringUtils.trimToNull(searchText))
                  .stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .toList();
          break;
        }
    }
    return options;
  }

  @LogExecutionTime
  @GetMapping(ASSET_GROUP_URI + "/findings/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public List<FilterUtilsJpa.Option> optionsByNameLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId) {
    return assetGroupService.getOptionsByNameLinkedToFindings(
        searchText, sourceId, PageRequest.of(0, 50));
  }

  @LogExecutionTime
  @PostMapping(ASSET_GROUP_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.assetGroupRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
