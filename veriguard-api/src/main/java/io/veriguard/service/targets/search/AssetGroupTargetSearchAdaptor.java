package io.veriguard.service.targets.search;

import io.veriguard.database.model.AssetGroupTarget;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeTarget;
import io.veriguard.rest.asset_group.AssetGroupCriteriaBuilderService;
import io.veriguard.rest.asset_group.form.AssetGroupOutput;
import io.veriguard.service.AssetGroupService;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

@Component
public class AssetGroupTargetSearchAdaptor extends SearchAdaptorBase {

  private final AssetGroupCriteriaBuilderService assetGroupCriteriaBuilderService;
  private final AssetGroupService assetGroupService;
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  public AssetGroupTargetSearchAdaptor(
      AssetGroupCriteriaBuilderService assetGroupCriteriaBuilderService,
      AssetGroupService assetGroupService,
      HelperTargetSearchAdaptor helperTargetSearchAdaptor) {
    this.assetGroupCriteriaBuilderService = assetGroupCriteriaBuilderService;
    this.helperTargetSearchAdaptor = helperTargetSearchAdaptor;
    this.assetGroupService = assetGroupService;

    // field name translations
    this.fieldTranslations.put("target_name", "asset_group_name");
    this.fieldTranslations.put("target_tags", "asset_group_tags");
    this.fieldTranslations.put("target_injects", "asset_group_injects");
  }

  @Override
  public Page<AttackChainNodeTarget> search(SearchPaginationInput input, AttackChainNode scopedAttackChainNode) {
    Page<AssetGroupOutput> filteredAssetGroups =
        assetGroupCriteriaBuilderService.assetGroupPagination(this.translate(input, scopedAttackChainNode));
    return new PageImpl<>(
        filteredAssetGroups.getContent().stream()
            .map(assetGroupOutput -> convertFromAssetGroupOutput(assetGroupOutput, scopedAttackChainNode))
            .toList(),
        filteredAssetGroups.getPageable(),
        filteredAssetGroups.getTotalElements());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForAttackChainNode(AttackChainNode scopedAttackChainNode, String textSearch) {
    return scopedAttackChainNode.getAssetGroups().stream()
        .filter(ag -> ag.getName().toLowerCase().contains(textSearch.toLowerCase()))
        .map(ag -> new FilterUtilsJpa.Option(ag.getId(), ag.getName()))
        .toList();
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    return assetGroupService.assetGroups(ids).stream()
        .map(ag -> new FilterUtilsJpa.Option(ag.getId(), ag.getName()))
        .toList();
  }

  private AttackChainNodeTarget convertFromAssetGroupOutput(
      AssetGroupOutput assetGroupOutput, AttackChainNode attackChainNode) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        attackChainNode,
        () ->
            new AssetGroupTarget(
                assetGroupOutput.getId(), assetGroupOutput.getName(), assetGroupOutput.getTags()),
        true);
  }
}
