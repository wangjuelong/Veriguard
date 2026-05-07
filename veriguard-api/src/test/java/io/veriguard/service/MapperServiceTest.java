package io.veriguard.service;

import static io.veriguard.utils.StringUtils.duplicateString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.ImportMapper;
import io.veriguard.database.model.AttackChainNodeImporter;
import io.veriguard.database.repository.EndpointRepository;
import io.veriguard.database.repository.ImportMapperRepository;
import io.veriguard.database.repository.NodeContractRepository;
import io.veriguard.rest.mapper.form.*;
import io.veriguard.rest.tag.TagService;
import io.veriguard.utils.mockMapper.MockMapperUtils;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
public class MapperServiceTest extends IntegrationTest {

  @Mock private ImportMapperRepository importMapperRepository;
  @Mock private NodeContractRepository nodeContractRepository;
  @Mock private EndpointRepository endpointRepository;
  @Mock private ObjectMapper objectMapper;
  @Mock private EndpointService endpointService;
  @Mock private TagService tagService;

  private MapperService mapperService;

  @BeforeEach
  void before() {
    // Injecting mocks into the controller
    mapperService =
        new MapperService(
            importMapperRepository,
            nodeContractRepository,
            endpointRepository,
            endpointService,
            tagService,
            objectMapper);
  }

  // -- SCENARIOS --

  @DisplayName("Test create a mapper")
  @Test
  void createMapper() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    ImportMapperAddInput importMapperInput = new ImportMapperAddInput();
    importMapperInput.setName(importMapper.getName());
    importMapperInput.setAttackChainNodeTypeColumn(importMapper.getAttackChainNodeTypeColumn());
    importMapperInput.setImporters(
        importMapper.getAttackChainNodeImporters().stream()
            .map(
                attackChainNodeImporter -> {
                  AttackChainNodeImporterAddInput attackChainNodeImporterAddInput = new AttackChainNodeImporterAddInput();
                  attackChainNodeImporterAddInput.setAttackChainNodeTypeValue(attackChainNodeImporter.getImportTypeValue());
                  attackChainNodeImporterAddInput.setNodeContractId(
                      attackChainNodeImporter.getNodeContract().getId());

                  attackChainNodeImporterAddInput.setRuleAttributes(
                      attackChainNodeImporter.getRuleAttributes().stream()
                          .map(
                              ruleAttribute -> {
                                RuleAttributeAddInput ruleAttributeAddInput =
                                    new RuleAttributeAddInput();
                                ruleAttributeAddInput.setName(ruleAttribute.getName());
                                ruleAttributeAddInput.setColumns(ruleAttribute.getColumns());
                                ruleAttributeAddInput.setDefaultValue(
                                    ruleAttribute.getDefaultValue());
                                ruleAttributeAddInput.setAdditionalConfig(
                                    ruleAttribute.getAdditionalConfig());
                                return ruleAttributeAddInput;
                              })
                          .toList());
                  return attackChainNodeImporterAddInput;
                })
            .toList());
    when(importMapperRepository.save(any())).thenReturn(importMapper);
    // -- EXECUTE --
    ImportMapper importMapperResponse = mapperService.createAndSaveImportMapper(importMapperInput);

    // -- ASSERT --
    assertNotNull(importMapperResponse);
    assertEquals(importMapperResponse.getId(), importMapper.getId());
  }

  @DisplayName("Test duplicate a mapper")
  @Test
  void duplicateMapper() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    when(importMapperRepository.findById(any())).thenReturn(Optional.of(importMapper));
    ImportMapper importMapperSaved = MockMapperUtils.createImportMapper();
    when(importMapperRepository.save(any(ImportMapper.class))).thenReturn(importMapperSaved);

    // -- EXECUTE --
    ImportMapper response = mapperService.getDuplicateImportMapper(importMapper.getId());

    // -- ASSERT --
    ArgumentCaptor<ImportMapper> importMapperCaptor = ArgumentCaptor.forClass(ImportMapper.class);
    verify(importMapperRepository).save(importMapperCaptor.capture());

    ImportMapper capturedImportMapper = importMapperCaptor.getValue();
    // verify importMapper
    assertEquals(duplicateString(importMapper.getName()), capturedImportMapper.getName());
    assertEquals(importMapper.getAttackChainNodeTypeColumn(), capturedImportMapper.getAttackChainNodeTypeColumn());
    assertEquals(
        importMapper.getAttackChainNodeImporters().size(), capturedImportMapper.getAttackChainNodeImporters().size());
    // verify attackChainNodeImporter
    assertEquals("", capturedImportMapper.getAttackChainNodeImporters().get(0).getId());
    assertEquals(
        importMapper.getAttackChainNodeImporters().get(0).getImportTypeValue(),
        capturedImportMapper.getAttackChainNodeImporters().get(0).getImportTypeValue());
    assertEquals(
        importMapper.getAttackChainNodeImporters().get(0).getRuleAttributes().size(),
        capturedImportMapper.getAttackChainNodeImporters().get(0).getRuleAttributes().size());
    // verify ruleAttribute
    assertEquals(
        "", capturedImportMapper.getAttackChainNodeImporters().get(0).getRuleAttributes().get(0).getId());
    assertEquals(
        importMapper.getAttackChainNodeImporters().get(0).getRuleAttributes().get(0).getName(),
        capturedImportMapper.getAttackChainNodeImporters().get(0).getRuleAttributes().get(0).getName());

    assertEquals(response.getId(), importMapperSaved.getId());
  }

  @DisplayName("Test update a specific mapper by using new rule attributes and new inject importer")
  @Test
  void updateSpecificMapperWithNewElements() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    ImportMapperUpdateInput importMapperInput = new ImportMapperUpdateInput();
    importMapperInput.setName(importMapper.getName());
    importMapperInput.setAttackChainNodeTypeColumn(importMapper.getAttackChainNodeTypeColumn());
    importMapperInput.setImporters(
        importMapper.getAttackChainNodeImporters().stream()
            .map(
                attackChainNodeImporter -> {
                  AttackChainNodeImporterUpdateInput attackChainNodeImporterUpdateInput =
                      new AttackChainNodeImporterUpdateInput();
                  attackChainNodeImporterUpdateInput.setAttackChainNodeTypeValue(attackChainNodeImporter.getImportTypeValue());
                  attackChainNodeImporterUpdateInput.setNodeContractId(
                      attackChainNodeImporter.getNodeContract().getId());

                  attackChainNodeImporterUpdateInput.setRuleAttributes(
                      attackChainNodeImporter.getRuleAttributes().stream()
                          .map(
                              ruleAttribute -> {
                                RuleAttributeUpdateInput ruleAttributeUpdateInput =
                                    new RuleAttributeUpdateInput();
                                ruleAttributeUpdateInput.setName(ruleAttribute.getName());
                                ruleAttributeUpdateInput.setColumns(ruleAttribute.getColumns());
                                ruleAttributeUpdateInput.setDefaultValue(
                                    ruleAttribute.getDefaultValue());
                                ruleAttributeUpdateInput.setAdditionalConfig(
                                    ruleAttribute.getAdditionalConfig());
                                return ruleAttributeUpdateInput;
                              })
                          .toList());
                  return attackChainNodeImporterUpdateInput;
                })
            .toList());
    when(importMapperRepository.findById(any())).thenReturn(Optional.of(importMapper));
    when(importMapperRepository.save(any())).thenReturn(importMapper);
    when(nodeContractRepository.findAllById(any()))
        .thenReturn(
            importMapper.getAttackChainNodeImporters().stream()
                .map(AttackChainNodeImporter::getNodeContract)
                .toList());

    // -- EXECUTE --
    ImportMapper response =
        mapperService.updateImportMapper(importMapper.getId(), importMapperInput);

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(response.getId(), importMapper.getId());
  }

  @DisplayName(
      "Test update a specific mapper by creating rule attributes and updating new inject importer")
  @Test
  void updateSpecificMapperWithUpdatedAttackChainNodeImporter() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    ImportMapperUpdateInput importMapperInput = new ImportMapperUpdateInput();
    importMapperInput.setName(importMapper.getName());
    importMapperInput.setAttackChainNodeTypeColumn(importMapper.getAttackChainNodeTypeColumn());
    importMapperInput.setImporters(
        importMapper.getAttackChainNodeImporters().stream()
            .map(
                attackChainNodeImporter -> {
                  AttackChainNodeImporterUpdateInput attackChainNodeImporterUpdateInput =
                      new AttackChainNodeImporterUpdateInput();
                  attackChainNodeImporterUpdateInput.setAttackChainNodeTypeValue(attackChainNodeImporter.getImportTypeValue());
                  attackChainNodeImporterUpdateInput.setNodeContractId(
                      attackChainNodeImporter.getNodeContract().getId());
                  attackChainNodeImporterUpdateInput.setId(attackChainNodeImporter.getId());

                  attackChainNodeImporterUpdateInput.setRuleAttributes(
                      attackChainNodeImporter.getRuleAttributes().stream()
                          .map(
                              ruleAttribute -> {
                                RuleAttributeUpdateInput ruleAttributeUpdateInput =
                                    new RuleAttributeUpdateInput();
                                ruleAttributeUpdateInput.setName(ruleAttribute.getName());
                                ruleAttributeUpdateInput.setColumns(ruleAttribute.getColumns());
                                ruleAttributeUpdateInput.setDefaultValue(
                                    ruleAttribute.getDefaultValue());
                                ruleAttributeUpdateInput.setAdditionalConfig(
                                    ruleAttribute.getAdditionalConfig());
                                return ruleAttributeUpdateInput;
                              })
                          .toList());
                  return attackChainNodeImporterUpdateInput;
                })
            .toList());
    when(importMapperRepository.findById(any())).thenReturn(Optional.of(importMapper));
    when(importMapperRepository.save(any())).thenReturn(importMapper);
    when(nodeContractRepository.findAllById(any()))
        .thenReturn(
            importMapper.getAttackChainNodeImporters().stream()
                .map(AttackChainNodeImporter::getNodeContract)
                .toList());

    // -- EXECUTE --
    ImportMapper response =
        mapperService.updateImportMapper(importMapper.getId(), importMapperInput);

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(response.getId(), importMapper.getId());
  }

  @DisplayName(
      "Test update a specific mapper by updating rule attributes and updating inject importer")
  @Test
  void updateSpecificMapperWithUpdatedElements() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    ImportMapperUpdateInput importMapperInput = new ImportMapperUpdateInput();
    importMapperInput.setName(importMapper.getName());
    importMapperInput.setAttackChainNodeTypeColumn(importMapper.getAttackChainNodeTypeColumn());
    importMapperInput.setImporters(
        importMapper.getAttackChainNodeImporters().stream()
            .map(
                attackChainNodeImporter -> {
                  AttackChainNodeImporterUpdateInput attackChainNodeImporterUpdateInput =
                      new AttackChainNodeImporterUpdateInput();
                  attackChainNodeImporterUpdateInput.setAttackChainNodeTypeValue(attackChainNodeImporter.getImportTypeValue());
                  attackChainNodeImporterUpdateInput.setNodeContractId(
                      attackChainNodeImporter.getNodeContract().getId());
                  attackChainNodeImporterUpdateInput.setId(attackChainNodeImporter.getId());

                  attackChainNodeImporterUpdateInput.setRuleAttributes(
                      attackChainNodeImporter.getRuleAttributes().stream()
                          .map(
                              ruleAttribute -> {
                                RuleAttributeUpdateInput ruleAttributeUpdateInput =
                                    new RuleAttributeUpdateInput();
                                ruleAttributeUpdateInput.setName(ruleAttribute.getName());
                                ruleAttributeUpdateInput.setColumns(ruleAttribute.getColumns());
                                ruleAttributeUpdateInput.setDefaultValue(
                                    ruleAttribute.getDefaultValue());
                                ruleAttributeUpdateInput.setAdditionalConfig(
                                    ruleAttribute.getAdditionalConfig());
                                ruleAttributeUpdateInput.setId(ruleAttribute.getId());
                                return ruleAttributeUpdateInput;
                              })
                          .toList());
                  return attackChainNodeImporterUpdateInput;
                })
            .toList());
    when(importMapperRepository.findById(any())).thenReturn(Optional.of(importMapper));
    when(importMapperRepository.save(any())).thenReturn(importMapper);
    when(nodeContractRepository.findAllById(any()))
        .thenReturn(
            importMapper.getAttackChainNodeImporters().stream()
                .map(AttackChainNodeImporter::getNodeContract)
                .toList());

    // -- EXECUTE --
    ImportMapper response =
        mapperService.updateImportMapper(importMapper.getId(), importMapperInput);

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(response.getId(), importMapper.getId());
  }
}
