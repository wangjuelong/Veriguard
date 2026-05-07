package io.veriguard.rest.attack_chain;

import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.ImportMapper;
import io.veriguard.database.repository.ImportMapperRepository;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.attack_chain.form.AttackChainNodesImportInput;
import io.veriguard.rest.attack_chain.response.ImportTestSummary;
import io.veriguard.service.AttackChainNodeImportService;
import io.veriguard.service.scenario.AttackChainService;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
@ExtendWith(MockitoExtension.class)
public class AttackChainImportApiTest extends IntegrationTest {

  private MockMvc mvc;

  @Mock private AttackChainNodeService attackChainNodeService;
  @Mock private AttackChainNodeImportService attackChainNodeImportService;
  @Mock private ImportMapperRepository importMapperRepository;
  @Mock private AttackChainService attackChainService;

  private AttackChainImportApi attackChainImportApi;

  private String SCENARIO_ID;

  @BeforeEach
  public void setUp() {
    // Injecting mocks into the controller
    attackChainImportApi =
        new AttackChainImportApi(attackChainNodeImportService, importMapperRepository, attackChainService);

    SCENARIO_ID = UUID.randomUUID().toString();

    mvc = MockMvcBuilders.standaloneSetup(attackChainImportApi).build();
  }

  @DisplayName("Test dry run import xls")
  @Test
  void testDryRunXls() throws Exception {
    // -- PREPARE --
    AttackChainNodesImportInput attackChainNodesImportInput = new AttackChainNodesImportInput();
    attackChainNodesImportInput.setImportMapperId(UUID.randomUUID().toString());
    attackChainNodesImportInput.setName("TEST");
    attackChainNodesImportInput.setTimezoneOffset(120);

    when(importMapperRepository.findById(any())).thenReturn(Optional.of(new ImportMapper()));
    when(attackChainNodeImportService.importAttackChainNodeIntoAttackChainFromXLS(
            any(), any(), any(), any(), anyInt(), anyBoolean()))
        .thenReturn(new ImportTestSummary());

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                MockMvcRequestBuilders.post(
                        "/api/attack_chains/{scenarioId}/xls/{importId}/dry",
                        SCENARIO_ID,
                        UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(attackChainNodesImportInput)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Test import xls")
  @Test
  void testImportXls() throws Exception {
    // -- PREPARE --
    AttackChainNodesImportInput attackChainNodesImportInput = new AttackChainNodesImportInput();
    attackChainNodesImportInput.setImportMapperId(UUID.randomUUID().toString());
    attackChainNodesImportInput.setName("TEST");
    attackChainNodesImportInput.setTimezoneOffset(120);

    when(importMapperRepository.findById(any())).thenReturn(Optional.of(new ImportMapper()));
    when(attackChainNodeImportService.importAttackChainNodeIntoAttackChainFromXLS(
            any(), any(), any(), any(), anyInt(), anyBoolean()))
        .thenReturn(new ImportTestSummary());

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                MockMvcRequestBuilders.post(
                        "/api/attack_chains/{scenarioId}/xls/{importId}/import",
                        SCENARIO_ID,
                        UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(attackChainNodesImportInput)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }
}
