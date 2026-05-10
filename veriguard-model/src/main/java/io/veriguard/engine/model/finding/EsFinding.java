package io.veriguard.engine.model.finding;

import io.veriguard.annotation.EsQueryable;
import io.veriguard.annotation.Indexable;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.engine.model.EsBase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "finding", label = "Finding")
public class EsFinding extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(label = "finding value", filterable = true)
  @EsQueryable(keyword = true)
  private String finding_value;

  @Queryable(label = "finding type", filterable = true, refEnumClazz = ContractOutputType.class)
  @EsQueryable(keyword = true)
  private String finding_type;

  @Queryable(label = "field")
  private String finding_field;

  // -- SIDE --

  @Queryable(label = "node", filterable = true)
  @EsQueryable(keyword = true)
  private String base_node_side; // Must finish by _side

  @Queryable(label = "attack_chain_run", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_attack_chain_run_side; // Must finish by _side

  @Queryable(label = "attack_chain", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_attack_chain_side; // Must finish by _side

  @Queryable(label = "endpoint", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_endpoint_side; // Must finish by _side
}
