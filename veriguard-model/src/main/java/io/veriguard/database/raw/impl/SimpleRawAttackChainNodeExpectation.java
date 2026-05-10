package io.veriguard.database.raw.impl;

import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Simple implementation of {@link RawAttackChainNodeExpectation} for programmatic construction.
 *
 * <p>This class provides a mutable implementation of the {@link RawAttackChainNodeExpectation}
 * projection interface, allowing expectation data to be constructed programmatically rather than
 * exclusively from database queries.
 *
 * <p>Used when expectation data needs to be assembled from multiple sources or transformed before
 * use.
 *
 * @see RawAttackChainNodeExpectation
 */
@Getter
@Setter
public class SimpleRawAttackChainNodeExpectation implements RawAttackChainNodeExpectation {

  private String node_expectation_id;
  private String node_expectation_type;
  private Double node_expectation_score;
  private Double node_expectation_expected_score;
  private String team_id;
  private String team_name;
  private String user_id;
  private String user_firstname;
  private String user_lastname;
  private String agent_id;
  private String asset_id;
  private String asset_name;
  private String asset_type;
  private String asset_external_reference;
  private String endpoint_platform;
  private String asset_group_id;
  private String asset_group_name;
  private List<String> asset_ids;
  private String attack_chain_id;
  private String attack_chain_run_id;
  private String node_id;
  private Boolean node_expectation_group;
  private Instant node_expectation_created_at;
  private String node_expectation_name;
  public String node_expectation_description;
  public String node_expectation_results;
  public Long node_expiration_time;
  public Instant node_expectation_updated_at;
  public Set<String> attack_pattern_ids;
  public Set<String> domain_ids;
  public Set<String> security_platform_ids;
  private String node_title;
  private Instant tracking_sent_date;
}
