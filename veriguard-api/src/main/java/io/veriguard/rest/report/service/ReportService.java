package io.veriguard.rest.report.service;

import static java.time.Instant.now;

import io.veriguard.database.model.*;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.report.form.ReportAttackChainNodeCommentInput;
import io.veriguard.rest.report.form.ReportInput;
import io.veriguard.rest.report.model.Report;
import io.veriguard.rest.report.model.ReportInformation;
import io.veriguard.rest.report.model.ReportAttackChainNodeComment;
import io.veriguard.rest.report.repository.ReportRepository;
import io.veriguard.rest.report.specification.ReportSpecification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ReportService {
  private final ReportRepository reportRepository;

  public Report report(@NotNull final UUID reportId) {
    return this.reportRepository.findById(reportId).orElseThrow(ElementNotFoundException::new);
  }

  /**
   * Return the report based on the Simulation id, if the report doesn't exist or is not linked to
   * the simulation, it throws a ElementNotFoundException
   *
   * @param simulationId
   * @param reportId
   * @return
   */
  public Report reportFromSimulation(
      @NotBlank final String simulationId, @NotNull final UUID reportId) {
    return this.reportRepository
        .findByIdAndExercise_Id(reportId, simulationId)
        .orElseThrow(ElementNotFoundException::new);
  }

  public List<Report> reportsFromAttackChainRun(@NotNull final String attackChainRunId) {
    return this.reportRepository.findAll(ReportSpecification.fromAttackChainRun(attackChainRunId));
  }

  public Report updateReport(@NotNull final Report report, @NotNull final ReportInput input) {
    report.setUpdateAttributes(input);
    report.setUpdateDate(now());
    input
        .getReportInformations()
        .forEach(
            i -> {
              ReportInformation reportInformation =
                  report.getReportInformations().stream()
                      .filter(
                          r -> r.getReportInformationsType().equals(i.getReportInformationsType()))
                      .findFirst()
                      .orElse(null);
              if (reportInformation != null) {
                reportInformation.setReportInformationsDisplay(i.getReportInformationsDisplay());
              } else {
                reportInformation = new ReportInformation();
                reportInformation.setReport(report);
                reportInformation.setReportInformationsDisplay(i.getReportInformationsDisplay());
                reportInformation.setReportInformationsType(i.getReportInformationsType());
                report.getReportInformations().add(reportInformation);
              }
            });
    return this.reportRepository.save(report);
  }

  public Report updateReportAttackChainNodeComment(
      @NotNull final Report report,
      @NotNull final AttackChainNode attackChainNode,
      @NotNull final ReportAttackChainNodeCommentInput input) {
    Optional<ReportAttackChainNodeComment> reportAttackChainNodeComment =
        this.reportRepository.findReportAttackChainNodeComment(
            UUID.fromString(report.getId()), attackChainNode.getId());
    ReportAttackChainNodeComment attackChainNodeComment;
    if (reportAttackChainNodeComment.isPresent()) {
      attackChainNodeComment = reportAttackChainNodeComment.get();
      attackChainNodeComment.setComment(input.getComment());
    } else {
      attackChainNodeComment = new ReportAttackChainNodeComment();
      attackChainNodeComment.setAttackChainNode(attackChainNode);
      attackChainNodeComment.setReport(report);
      attackChainNodeComment.setComment(input.getComment());
      report.getReportAttackChainNodesComments().add(attackChainNodeComment);
    }
    return this.reportRepository.save(report);
  }

  public void deleteReport(@NotBlank final UUID reportId) {
    this.reportRepository.deleteById(reportId);
  }
}
