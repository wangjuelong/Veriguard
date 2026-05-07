package io.veriguard.rest.inject.service;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.AssetGroup;
import java.util.ArrayList;
import java.util.List;

public record AssetToExecute(
    Asset asset, boolean isDirectlyLinkedToAttackChainNode, List<AssetGroup> assetGroups) {

  public AssetToExecute(final Asset asset) {
    this(asset, true, new ArrayList<>());
  }
}
