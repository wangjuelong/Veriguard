package io.veriguard.rest.report.repository;

import io.veriguard.rest.report.model.Report;
import io.veriguard.rest.report.model.ReportAttackChainNodeComment;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository
    extends CrudRepository<Report, UUID>, JpaSpecificationExecutor<Report> {
  @NotNull
  Optional<Report> findById(@NotNull final UUID id);

  @Query(
      value =
          "SELECT attackChainNodeComment FROM ReportAttackChainNodeComment attackChainNodeComment WHERE attackChainNodeComment.report.id = :reportId AND attackChainNodeComment.attackChainNode.id = :attackChainNodeId")
  Optional<ReportAttackChainNodeComment> findReportAttackChainNodeComment(
      @NotNull final UUID reportId, @NotNull final String attackChainNodeId);

  Optional<Report> findByIdAndExercise_Id(@NotNull UUID reportId, @NotBlank String attackChainRunId);
}
