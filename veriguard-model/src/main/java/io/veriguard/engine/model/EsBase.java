package io.veriguard.engine.model;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.annotation.EsQueryable;
import io.veriguard.annotation.Indexable;
import io.veriguard.annotation.Queryable;
import io.veriguard.engine.model.assetgroup.EsAssetGroup;
import io.veriguard.engine.model.attackpattern.EsAttackPattern;
import io.veriguard.engine.model.endpoint.EsEndpoint;
import io.veriguard.engine.model.finding.EsFinding;
import io.veriguard.engine.model.attack_chain_node.EsAttackChainNode;
import io.veriguard.engine.model.attack_chain_nodeexpectation.EsAttackChainNodeExpectation;
import io.veriguard.engine.model.attack_chain.EsAttackChain;
import io.veriguard.engine.model.securitydomain.EsSecurityDomain;
import io.veriguard.engine.model.securityplatform.EsSecurityPlatform;
import io.veriguard.engine.model.simulation.EsSimulation;
import io.veriguard.engine.model.tag.EsTag;
import io.veriguard.engine.model.team.EsTeam;
import io.veriguard.engine.model.vulnerableendpoint.EsVulnerableEndpoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
    discriminatorProperty = "base_entity",
    oneOf = {
      EsAttackPattern.class,
      EsEndpoint.class,
      EsFinding.class,
      EsAttackChainNode.class,
      EsAttackChainNodeExpectation.class,
      EsAttackChain.class,
      EsSimulation.class,
      EsTag.class,
      EsVulnerableEndpoint.class,
      EsTeam.class,
      EsAssetGroup.class,
      EsSecurityPlatform.class,
      EsSecurityDomain.class,
    },
    discriminatorMapping = {
      @DiscriminatorMapping(value = "attack-pattern", schema = EsAttackPattern.class),
      @DiscriminatorMapping(value = "endpoint", schema = EsEndpoint.class),
      @DiscriminatorMapping(value = "finding", schema = EsFinding.class),
      @DiscriminatorMapping(value = "node", schema = EsAttackChainNode.class),
      @DiscriminatorMapping(
          value = "expectation-inject",
          schema = EsAttackChainNodeExpectation.class),
      @DiscriminatorMapping(value = "attack_chain_run", schema = EsSimulation.class),
      @DiscriminatorMapping(value = "attack_chain", schema = EsAttackChain.class),
      @DiscriminatorMapping(value = "tag", schema = EsTag.class),
      @DiscriminatorMapping(value = "vulnerable-endpoint", schema = EsVulnerableEndpoint.class),
      @DiscriminatorMapping(value = "team", schema = EsTeam.class),
      @DiscriminatorMapping(value = "security-platform", schema = EsSecurityPlatform.class),
      @DiscriminatorMapping(value = "security-domain", schema = EsSecurityDomain.class),
      @DiscriminatorMapping(value = "asset-group", schema = EsAssetGroup.class),
    })
public class EsBase {

  @Queryable(label = "id", filterable = true, sortable = true)
  @EsQueryable(keyword = true)
  private String base_id;

  @Queryable(label = "entity", filterable = true, sortable = true)
  @EsQueryable(keyword = true)
  private String base_entity;

  private String base_representative;

  @Queryable(label = "created at", filterable = true, sortable = true)
  private Instant base_created_at;

  @Queryable(label = "updated at", filterable = true, sortable = true)
  private Instant base_updated_at;

  // -- Base for ACL --
  private List<String> base_restrictions;

  // To support logical side deletions, means "DELETE CASCADE", to set only if you want to delete
  // the linked object itself
  // Example : I delete the attackChainNode Id-A, all objects which contain in their
  // base_dependencies the
  // Id-A will be entirely deleted
  // https://github.com/rieske/postgres-cdc could be an alternative.
  private List<String> base_dependencies = new ArrayList<>();

  public EsBase() {
    try {
      base_entity = this.getClass().getAnnotation(Indexable.class).index();
    } catch (Exception e) {
      // Need for json deserialize
    }
  }
}
