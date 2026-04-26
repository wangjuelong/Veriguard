package io.veriguard.utils.mapper;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.AssetGroup;
import io.veriguard.database.model.Tag;
import io.veriguard.rest.asset_group.form.AssetGroupOutput;
import io.veriguard.rest.asset_group.form.AssetGroupSimple;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssetGroupMapper {

  public AssetGroupSimple toAssetGroupSimple(AssetGroup assetGroup) {
    return AssetGroupSimple.builder().id(assetGroup.getId()).name(assetGroup.getName()).build();
  }

  public AssetGroupOutput toAssetGroupOutput(AssetGroup assetGroup) {
    return AssetGroupOutput.builder()
        .id(assetGroup.getId())
        .name(assetGroup.getName())
        .description(assetGroup.getDescription())
        .dynamicFilter(assetGroup.getDynamicFilter())
        .assets(assetGroup.getAssets().stream().map(Asset::getId).collect(Collectors.toSet()))
        .tags(assetGroup.getTags().stream().map(Tag::getName).collect(Collectors.toSet()))
        .build();
  }
}
