package io.veriguard.database.repository;

import io.veriguard.database.model.Asset;
import io.veriguard.database.raw.RawAsset;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository
    extends CrudRepository<Asset, String>, JpaSpecificationExecutor<Asset> {

  @Query("select a from Asset a where a.type IN :types")
  List<Asset> findByType(@Param("types") final List<String> types);

  /**
   * Returns the raw assets having the ids passed in parameter
   *
   * @param ids the ids
   * @return the list of raw assets
   */
  @Query(
      value =
          "SELECT asset_id, asset_name, asset_type, asset_external_reference, endpoint_platform, "
              + "asset_created_at, asset_updated_at "
              + "FROM assets "
              + "WHERE asset_id IN :ids ;",
      nativeQuery = true)
  List<RawAsset> rawByIds(@Param("ids") List<String> ids);

  @Query(
      value =
          "SELECT DISTINCT a.asset_id, a.asset_name, a.asset_type, a.asset_external_reference, a.endpoint_platform, "
              + "a.asset_created_at, a.asset_updated_at "
              + "FROM assets a "
              + "LEFT JOIN attack_chain_nodes_assets ia ON a.asset_id = ia.asset_id "
              + "WHERE a.asset_id IN (:assetIds) OR ia.node_id IN (:attackChainNodeIds) ;",
      nativeQuery = true)
  List<RawAsset> rawByIdsOrAttackChainNodeIds(
      @Param("assetIds") Set<String> assetIds,
      @Param("attackChainNodeIds") Set<String> attackChainNodeIds);

  @Query(
      "SELECT DISTINCT n.attackChainRun.id, a.id, a.name "
          + "FROM AttackChainNode n JOIN n.assets a "
          + "WHERE n.attackChainRun.id IN :attackChainRunIds")
  List<Object[]> assetsByAttackChainRunIds(
      @Param("attackChainRunIds") java.util.Collection<String> attackChainRunIds);

  @Query(
      value =
          "SELECT DISTINCT ia.node_id, a.asset_id, a.asset_name "
              + "FROM assets a "
              + "INNER JOIN attack_chain_nodes_assets ia ON a.asset_id = ia.asset_id "
              + "WHERE ia.node_id in :attackChainNodeIds",
      nativeQuery = true)
  List<Object[]> assetsByAttackChainNodeIds(Set<String> attackChainNodeIds);
}
