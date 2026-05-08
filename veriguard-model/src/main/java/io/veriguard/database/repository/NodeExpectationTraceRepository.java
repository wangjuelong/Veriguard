package io.veriguard.database.repository;

import io.veriguard.database.model.NodeExpectationTrace;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NodeExpectationTraceRepository
    extends CrudRepository<NodeExpectationTrace, String>,
        JpaSpecificationExecutor<NodeExpectationTrace> {

  @Query(
      "select t from NodeExpectationTrace t where t.attackChainNodeExpectation.id = :expectationId and t.securityPlatform.id = :sourceId")
  List<NodeExpectationTrace> findByExpectationAndSecurityPlatform(
      @Param("expectationId") final String expectationId, @Param("sourceId") final String sourceId);

  @Query(
      "select count(distinct t) from NodeExpectationTrace t where t.attackChainNodeExpectation.id = :expectationId and t.securityPlatform.id = :sourceId")
  long countAlerts(
      @Param("expectationId") final String expectationId, @Param("sourceId") final String sourceId);

  @Query(
      "select t from NodeExpectationTrace t where t.attackChainNodeExpectation.id = :expectationId and t.securityPlatform.id = :sourceId and t.alertName = :alertName and t.alertLink = :alertLink")
  NodeExpectationTrace findByAlertLinkAndAlertNameAndSecurityPlatformAndAttackChainNodeExpectation(
      @Param("alertLink") final String alertLink,
      @Param("alertName") final String alertName,
      @Param("sourceId") final String sourceId,
      @Param("expectationId") final String expectationId);

  @Modifying
  @Query(
      value =
          "INSERT INTO node_expectation_traces (trace_id, trace_expectation_id, trace_source_id, trace_alert_link, trace_alert_name, trace_date, trace_created_at, trace_updated_at) "
              + "VALUES (:id, :expectationId, :securityPlatformId, :alertLink, :alertName, :alertDate, :createdAtDate, :updatedAtDate) "
              + "ON CONFLICT (trace_expectation_id, trace_source_id, trace_alert_name, trace_alert_link) DO NOTHING",
      nativeQuery = true)
  void insertIfNotExists(
      @Param("id") String id,
      @Param("expectationId") String expectationId,
      @Param("securityPlatformId") String securityPlatformId,
      @Param("alertLink") String alertLink,
      @Param("alertName") String alertName,
      @Param("alertDate") Instant alertDate,
      @Param("createdAtDate") Instant createdAtDate,
      @Param("updatedAtDate") Instant updatedAtDate);
}
