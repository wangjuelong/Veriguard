package io.veriguard.rest;

import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.Capability;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.rest.exercise.service.AttackChainRunService;
import io.veriguard.rest.inject.service.AttackChainNodeService;
import io.veriguard.rest.mapper.MapperApi;
import io.veriguard.rest.report.ReportApi;
import io.veriguard.rest.report.form.ReportAttackChainNodeCommentInput;
import io.veriguard.rest.report.form.ReportInput;
import io.veriguard.rest.report.model.Report;
import io.veriguard.rest.report.service.ReportService;
import io.veriguard.utils.fixtures.PaginationFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
public class ReportApiTest extends IntegrationTest {

  private MockMvc mvc;

  @Mock private ReportService reportService;
  @Mock private AttackChainRunService attackChainRunService;
  @Mock private AttackChainNodeService attackChainNodeService;

  @Autowired private ObjectMapper objectMapper;

  private AttackChainRun attackChainRun;
  private Report report;
  private ReportInput reportInput;

  @BeforeEach
  void before() throws IllegalAccessException, NoSuchFieldException {
    ReportApi reportApi = new ReportApi(attackChainRunService, reportService, attackChainNodeService);
    Field sessionContextField = MapperApi.class.getSuperclass().getDeclaredField("mapper");
    sessionContextField.setAccessible(true);
    sessionContextField.set(reportApi, objectMapper);
    mvc = MockMvcBuilders.standaloneSetup(reportApi).build();

    attackChainRun = new AttackChainRun();
    attackChainRun.setName("Exercise name");
    attackChainRun.setId("exercise123");
    report = new Report();
    report.setId(UUID.randomUUID().toString());
    reportInput = new ReportInput();
    reportInput.setName("Report name");
  }

  @Nested
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  @DisplayName("Reports for exercise")
  class ReportsForAttackChainRun {
    @DisplayName("Create report")
    @Test
    void createReportForAttackChainRun() throws Exception {
      // -- PREPARE --
      when(attackChainRunService.attackChainRun(anyString())).thenReturn(attackChainRun);
      when(reportService.updateReport(any(Report.class), any(ReportInput.class)))
          .thenReturn(report);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  MockMvcRequestBuilders.post("/api/exercises/" + attackChainRun.getId() + "/reports")
                      .content(asJsonString(reportInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      verify(attackChainRunService).attackChainRun(attackChainRun.getId());
      assertNotNull(response);
      assertEquals(JsonPath.read(response, "$.report_id"), report.getId());
    }

    @DisplayName("Retrieve reports")
    @Test
    void retrieveReportForAttackChainRun() throws Exception {
      // PREPARE
      List<Report> reports = List.of(report);
      when(reportService.reportsFromAttackChainRun(anyString())).thenReturn(reports);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  MockMvcRequestBuilders.get("/api/exercises/fakeExercisesId123/reports")
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      verify(reportService).reportsFromAttackChainRun("fakeExercisesId123");
      assertNotNull(response);
      assertEquals(JsonPath.read(response, "$[0].report_id"), report.getId());
    }

    @DisplayName("Update Report")
    @Test
    void updateReportForAttackChainRun() throws Exception {
      // -- PREPARE --
      report.setAttackChainRun(attackChainRun);
      when(reportService.report(any())).thenReturn(report);
      when(reportService.updateReport(any(Report.class), any(ReportInput.class)))
          .thenReturn(report);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  MockMvcRequestBuilders.put(
                          "/api/exercises/" + attackChainRun.getId() + "/reports/" + report.getId())
                      .content(asJsonString(reportInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      report.setName("fake");
      verify(reportService).report(UUID.fromString(report.getId()));
      verify(reportService).updateReport(report, reportInput);
      assertNotNull(response);
      assertEquals(JsonPath.read(response, "$.report_id"), report.getId());
    }

    @DisplayName("Update report inject comment")
    @Test
    void updateReportAttackChainNodeCommentTest() throws Exception {
      // -- PREPARE --
      AttackChainNode attackChainNode = new AttackChainNode();
      attackChainNode.setTitle("Test inject");
      attackChainNode.setId(UUID.randomUUID().toString());
      attackChainNode.setAttackChainRun(attackChainRun);
      report.setAttackChainRun(attackChainRun);
      ReportAttackChainNodeCommentInput attackChainNodeCommentInput = new ReportAttackChainNodeCommentInput();
      attackChainNodeCommentInput.setAttackChainNodeId(attackChainNode.getId());
      attackChainNodeCommentInput.setComment("Comment test");

      when(reportService.report(any())).thenReturn(report);
      when(attackChainNodeService.attackChainNode(any())).thenReturn(attackChainNode);
      when(reportService.updateReportAttackChainNodeComment(
              any(Report.class), any(AttackChainNode.class), any(ReportAttackChainNodeCommentInput.class)))
          .thenReturn(null);

      // -- EXECUTE --
      String response =
          mvc.perform(
                  MockMvcRequestBuilders.put(
                          "/api/exercises/"
                              + attackChainRun.getId()
                              + "/reports/"
                              + report.getId()
                              + "/inject-comments")
                      .content(asJsonString(attackChainNodeCommentInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      verify(reportService).report(UUID.fromString(report.getId()));
      verify(attackChainNodeService).attackChainNode(attackChainNode.getId());
      verify(reportService).updateReportAttackChainNodeComment(report, attackChainNode, attackChainNodeCommentInput);
      assertNotNull(response);
    }

    @DisplayName("Delete Report")
    @Test
    void deleteReportForAttackChainRun() throws Exception {
      // -- PREPARE --
      report.setAttackChainRun(attackChainRun);
      when(reportService.report(any())).thenReturn(report);

      // -- EXECUTE --
      mvc.perform(
              MockMvcRequestBuilders.delete(
                      "/api/exercises/" + attackChainRun.getId() + "/reports/" + report.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(PaginationFixture.getDefault().textSearch("").build()))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      verify(reportService, times(1)).deleteReport(UUID.fromString(report.getId()));
    }
  }
}
