package io.veriguard.database.repository;

import io.veriguard.database.model.Organization;
import io.veriguard.database.raw.RawOrganization;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository
    extends CrudRepository<Organization, String>, JpaSpecificationExecutor<Organization> {

  @NotNull
  Optional<Organization> findById(@NotNull String id);

  @NotNull
  List<Organization> findByNameIgnoreCase(@NotNull final String name);

  @Query(
      value =
          "SELECT org.*, "
              + "array_agg(DISTINCT org_tags.tag_id) FILTER (WHERE org_tags.tag_id IS NOT NULL) AS organization_tags, "
              + "array_agg(DISTINCT attack_chain_nodes.node_id) FILTER (WHERE attack_chain_nodes.node_id IS NOT NULL) AS organization_injects, "
              + "coalesce(array_length(array_agg(DISTINCT attack_chain_nodes.node_id) FILTER (WHERE attack_chain_nodes.node_id IS NOT NULL), 1), 0) AS organization_injects_number "
              + "FROM organizations org "
              + "LEFT JOIN organizations_tags org_tags ON org.organization_id = org_tags.organization_id "
              + "LEFT JOIN users ON users.user_organization = org.organization_id "
              + "LEFT JOIN users_teams ON users.user_id = users_teams.user_id "
              + "LEFT JOIN attack_chain_nodes_teams ON attack_chain_nodes_teams.team_id = users_teams.team_id "
              + "LEFT JOIN attack_chain_nodes ON attack_chain_nodes.node_id = attack_chain_nodes_teams.node_id OR attack_chain_nodes.node_all_teams "
              + "GROUP BY org.organization_id",
      nativeQuery = true)
  List<RawOrganization> rawAll();
}
