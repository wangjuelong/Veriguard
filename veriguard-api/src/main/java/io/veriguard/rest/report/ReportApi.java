package io.veriguard.rest.report;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.rest.attack_chain_run.service.AttackChainRunService;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.report.form.ReportAttackChainNodeCommentInput;
import io.veriguard.rest.report.form.ReportInput;
import io.veriguard.rest.report.model.Report;
import io.veriguard.rest.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ReportApi extends RestBehavior {

  private final AttackChainRunService attackChainRunService;
  private final ReportService reportService;
  private final AttackChainNodeService attackChainNodeService;

  @GetMapping("/api/reports/{reportId}")
  @RBAC(
      resourceId = "#reportId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Report report(@PathVariable String reportId) {
    return this.reportService.report(UUID.fromString(reportId));
  }

  @GetMapping("/api/attack_chain_runs/{simulationId}/reports/{reportId}")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Get a Report from a simulation")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Report returned"),
        @ApiResponse(
            responseCode = "404",
            description = "Report doesn't exist or not linked to the simulation")
      })
  public Report reportFromSimulationAttackChainRun(
      @PathVariable String simulationId, @PathVariable String reportId) {
    return this.reportService.reportFromSimulation(simulationId, UUID.fromString(reportId));
  }

  @GetMapping("/api/attack_chain_runs/{exerciseId}/reports")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Report> attackChainRunReports(@PathVariable String attackChainRunId) {
    return this.reportService.reportsFromAttackChainRun(attackChainRunId);
  }

  @PostMapping("/api/attack_chain_runs/{exerciseId}/reports")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Report createAttackChainRunReport(
      @PathVariable String attackChainRunId, @Valid @RequestBody ReportInput input) {
    AttackChainRun attackChainRun = this.attackChainRunService.attackChainRun(attackChainRunId);
    Report report = new Report();
    report.setAttackChainRun(attackChainRun);
    return this.reportService.updateReport(report, input);
  }

  @PutMapping("/api/attack_chain_runs/{exerciseId}/reports/{reportId}/inject-comments")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Report updateReportAttackChainNodeComment(
      @PathVariable String attackChainRunId,
      @PathVariable String reportId,
      @Valid @RequestBody ReportAttackChainNodeCommentInput input) {
    Report report = this.reportService.report(UUID.fromString(reportId));
    assert attackChainRunId.equals(report.getAttackChainRun().getId());
    AttackChainNode attackChainNode = this.attackChainNodeService.attackChainNode(input.getAttackChainNodeId());
    assert attackChainRunId.equals(attackChainNode.getAttackChainRun().getId());
    return this.reportService.updateReportAttackChainNodeComment(report, attackChainNode, input);
  }

  @PutMapping("/api/attack_chain_runs/{exerciseId}/reports/{reportId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Report updateAttackChainRunReport(
      @PathVariable String attackChainRunId,
      @PathVariable String reportId,
      @Valid @RequestBody ReportInput input) {
    Report report = this.reportService.report(UUID.fromString(reportId));
    assert attackChainRunId.equals(report.getAttackChainRun().getId());
    return this.reportService.updateReport(report, input);
  }

  @DeleteMapping("/api/attack_chain_runs/{exerciseId}/reports/{reportId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public void deleteAttackChainRunReport(@PathVariable String attackChainRunId, @PathVariable String reportId) {
    Report report = this.reportService.report(UUID.fromString(reportId));
    assert attackChainRunId.equals(report.getAttackChainRun().getId());
    this.reportService.deleteReport(UUID.fromString(reportId));
  }
}
