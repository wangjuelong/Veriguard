package io.veriguard.engine.model.attack_chain_nodeexpectation;

import io.veriguard.annotation.EsQueryable;
import io.veriguard.annotation.Indexable;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import io.veriguard.database.model.Filters;
import io.veriguard.engine.model.EsBase;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "expectation-inject", label = "Inject expectation", ref = "InjectExpectation")
public class EsAttackChainNodeExpectation extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(
      label = "inject expectation type",
      filterable = true,
      refEnumClazz = EXPECTATION_TYPE.class)
  @EsQueryable(keyword = true)
  private String node_expectation_type;

  @Queryable(label = "inject expectation name")
  private String node_expectation_name;

  @Queryable(label = "inject title of the inject expectation")
  private String node_title;

  @Queryable(label = "inject expectation description")
  private String node_expectation_description;

  @Queryable(label = "inject expectation results")
  private String node_expectation_results;

  @Queryable(label = "inject expectation score", filterable = true)
  private Double node_expectation_score;

  @Queryable(label = "inject expectation expected score", filterable = true)
  private Double node_expectation_expected_score;

  @Queryable(label = "inject expectation expiration time")
  private Long node_expectation_expiration_time;

  @Queryable(label = "inject expectation is group")
  private Boolean node_expectation_group;

  @Queryable(
      label = "inject expectation status",
      filterable = true,
      refEnumClazz = EXPECTATION_STATUS.class)
  @EsQueryable(keyword = true)
  private String node_expectation_status;

  @Queryable(label = "execution date", filterable = true, sortable = true)
  private Instant execution_date;

  // -- SIDE --

  @Queryable(label = "attack_chain_run", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_attack_chain_run_side; // Must finish by _side

  @Queryable(label = "attack_chain", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_attack_chain_side; // Must finish by _side

  @Queryable(label = "node")
  @EsQueryable(keyword = true)
  private String base_node_side; // Must finish by _side

  @Queryable(label = "user")
  @EsQueryable(keyword = true)
  private String base_user_side; // Must finish by _side

  @Queryable(label = "team", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_team_side; // Must finish by _side

  @Queryable(
      label = "asset",
      filterable = true,
      overrideOperators = {Filters.FilterOperator.empty, Filters.FilterOperator.not_empty})
  @EsQueryable(keyword = true)
  private String base_asset_side; // Must finish by _side

  @Queryable(
      label = "asset group",
      filterable = true,
      overrideOperators = {Filters.FilterOperator.empty, Filters.FilterOperator.not_empty})
  @EsQueryable(keyword = true)
  private String base_asset_group_side; // Must finish by _side

  @Queryable(
      label = "attack patterns",
      filterable = true,
      dynamicValues = true,
      clazz = String.class)
  @EsQueryable(keyword = true)
  private Set<String> base_attack_patterns_side; // Must finish by _side

  @Queryable(
      label = "security domains",
      filterable = true,
      dynamicValues = true,
      clazz = String.class)
  @EsQueryable(keyword = true)
  private Set<String> base_security_domains_side; // Must finish by _side

  @Queryable(label = "security platform", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private Set<String> base_security_platforms_side; // Must finish by _side
}
