package io.veriguard.engine.model.assetgroup;

import io.veriguard.annotation.Indexable;
import io.veriguard.annotation.Queryable;
import io.veriguard.engine.model.EsBase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "asset-group", label = "Asset group")
public class EsAssetGroup extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */
  @Queryable(label = "asset group name")
  private String name;
}
