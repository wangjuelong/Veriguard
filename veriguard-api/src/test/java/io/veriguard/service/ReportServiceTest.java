package io.veriguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.rest.report.form.ReportAttackChainNodeCommentInput;
import io.veriguard.rest.report.form.ReportInformationInput;
import io.veriguard.rest.report.form.ReportInput;
import io.veriguard.rest.report.model.Report;
import io.veriguard.rest.report.model.ReportAttackChainNodeComment;
import io.veriguard.rest.report.model.ReportInformation;
import io.veriguard.rest.report.model.ReportInformationsType;
import io.veriguard.rest.report.repository.ReportRepository;
import io.veriguard.rest.report.service.ReportService;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@ExtendWith(MockitoExtension.class)
public class ReportServiceTest extends IntegrationTest {

  @Mock private ReportRepository reportRepository;

  private ReportService reportService;

  @BeforeEach
  void before() {
    // Injecting mocks into the controller
    reportService = new ReportService(reportRepository);
  }

  @DisplayName("Test create a report")
  @Test
  void createReport() throws Exception {
    // -- PREPARE --
    Report report = new Report();

    ReportInput reportInput = new ReportInput();
    reportInput.setName("test");
    ReportInformationInput reportInformationInput = new ReportInformationInput();
    reportInformationInput.setReportInformationsType(ReportInformationsType.MAIN_INFORMATION);
    reportInformationInput.setReportInformationsDisplay(true);
    ReportInformationInput reportInformationInput2 = new ReportInformationInput();
    reportInformationInput2.setReportInformationsType(ReportInformationsType.SCORE_DETAILS);
    reportInformationInput2.setReportInformationsDisplay(true);
    reportInput.setReportInformations(List.of(reportInformationInput, reportInformationInput2));

    when(reportRepository.save(any(Report.class))).thenReturn(report);

    // -- EXECUTE --
    reportService.updateReport(report, reportInput);

    // -- ASSERT --
    ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).save(reportCaptor.capture());
    Report capturedReport = reportCaptor.getValue();
    assertEquals(reportInput.getName(), capturedReport.getName());
    assertEquals(2, capturedReport.getReportInformations().size());
    ReportInformation reportInformationCaptured =
        capturedReport.getReportInformations().stream()
            .filter(r -> r.getReportInformationsType() == ReportInformationsType.MAIN_INFORMATION)
            .findFirst()
            .orElse(null);
    assert reportInformationCaptured != null;
    assertEquals(true, reportInformationCaptured.getReportInformationsDisplay());
    ReportInformation reportInformationCaptured2 =
        capturedReport.getReportInformations().stream()
            .filter(r -> r.getReportInformationsType() == ReportInformationsType.SCORE_DETAILS)
            .findFirst()
            .orElse(null);
    assert reportInformationCaptured2 != null;
    assertEquals(true, reportInformationCaptured2.getReportInformationsDisplay());
  }

  @DisplayName("Test update a report")
  @Test
  void updateReport() throws Exception {
    // -- PREPARE --
    Report report = new Report();
    report.setName("test");
    ReportInformation reportInformation = new ReportInformation();
    reportInformation.setReportInformationsType(ReportInformationsType.MAIN_INFORMATION);
    reportInformation.setReportInformationsDisplay(false);
    report.setReportInformations(List.of(reportInformation));

    ReportInput reportInput = new ReportInput();
    reportInput.setName("new name test");
    ReportInformationInput reportInformationInput = new ReportInformationInput();
    reportInformationInput.setReportInformationsType(ReportInformationsType.MAIN_INFORMATION);
    reportInformationInput.setReportInformationsDisplay(true);
    reportInput.setReportInformations(List.of(reportInformationInput));

    when(reportRepository.save(any(Report.class))).thenReturn(report);

    // -- EXECUTE --
    reportService.updateReport(report, reportInput);

    // -- ASSERT --
    ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).save(reportCaptor.capture());
    Report capturedReport = reportCaptor.getValue();
    assertEquals(reportInput.getName(), capturedReport.getName());
    assertEquals(1, capturedReport.getReportInformations().size());
    assertEquals(
        true, capturedReport.getReportInformations().getFirst().getReportInformationsDisplay());
  }

  @Nested
  @DisplayName("Reports inject comment")
  class ReportAttackChainNodeCommentTest {
    @DisplayName("Test update existing report inject comment")
    @Test
    void updateExistingReportAttackChainNodeComment() {
      // -- PREPARE --
      Report report = new Report();
      report.setName("test");
      report.setId(UUID.randomUUID().toString());
      AttackChainNode attackChainNode = new AttackChainNode();
      attackChainNode.setId("fakeID123");

      // add report attackChainNode comment
      ReportAttackChainNodeComment existingReportAttackChainNodeComment =
          new ReportAttackChainNodeComment();
      existingReportAttackChainNodeComment.setReport(report);
      existingReportAttackChainNodeComment.setComment("comment");
      existingReportAttackChainNodeComment.setAttackChainNode(attackChainNode);
      report.setReportAttackChainNodesComments(List.of(existingReportAttackChainNodeComment));

      ReportAttackChainNodeCommentInput commentInput = new ReportAttackChainNodeCommentInput();
      commentInput.setAttackChainNodeId(attackChainNode.getId());
      commentInput.setComment("New comment");

      // Mock
      when(reportRepository.findReportAttackChainNodeComment(
              eq(UUID.fromString(report.getId())), eq(attackChainNode.getId())))
          .thenReturn(Optional.of(existingReportAttackChainNodeComment));

      // -- EXECUTE --
      reportService.updateReportAttackChainNodeComment(report, attackChainNode, commentInput);

      // -- ASSERT --
      ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
      verify(reportRepository).save(reportCaptor.capture());
      Report capturedReport = reportCaptor.getValue();
      assertEquals(1, capturedReport.getReportAttackChainNodesComments().size());
      assertEquals(
          commentInput.getComment(),
          capturedReport.getReportAttackChainNodesComments().getFirst().getComment());
    }

    @DisplayName("Test add new report inject comment")
    @Test
    void addReportAttackChainNodeComment() throws Exception {
      // -- PREPARE --
      Report report = new Report();
      report.setName("test");
      report.setId(UUID.randomUUID().toString());
      AttackChainNode attackChainNode = new AttackChainNode();
      attackChainNode.setId("fakeID123");

      ReportAttackChainNodeCommentInput commentInput = new ReportAttackChainNodeCommentInput();
      commentInput.setAttackChainNodeId(attackChainNode.getId());
      commentInput.setComment("New test comment");

      // Mock
      when(reportRepository.findReportAttackChainNodeComment(
              eq(UUID.fromString(report.getId())), eq(attackChainNode.getId())))
          .thenReturn(Optional.empty());

      // -- EXECUTE --
      reportService.updateReportAttackChainNodeComment(report, attackChainNode, commentInput);

      // -- ASSERT --
      ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
      verify(reportRepository).save(reportCaptor.capture());
      Report capturedReport = reportCaptor.getValue();
      assertEquals(1, capturedReport.getReportAttackChainNodesComments().size());
      assertEquals(
          commentInput.getComment(),
          capturedReport.getReportAttackChainNodesComments().getFirst().getComment());
    }
  }
}
