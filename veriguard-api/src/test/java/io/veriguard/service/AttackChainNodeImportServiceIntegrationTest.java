package io.veriguard.service;

import static io.veriguard.utils.fixtures.import_mapper.RuleAttributeFixture.createRuleAttribute;
import static org.junit.jupiter.api.Assertions.*;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.ImportMapper;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeImporter;
import io.veriguard.database.model.AttackChain;
import io.veriguard.rest.attack_chain.response.ImportTestSummary;
import io.veriguard.utils.fixtures.AttackChainFixture;
import io.veriguard.utils.fixtures.XlsFixture;
import io.veriguard.utils.fixtures.import_mapper.ImportMapperFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
public class AttackChainNodeImportServiceIntegrationTest extends IntegrationTest {

  @Autowired private AttackChainNodeImportService attackChainNodeImportService;

  @DisplayName(
      "Expectation rule attributes with no mapped columns and no default value"
          + " should not produce expectations on the imported inject")
  @Test
  void given_unmappedExpectationRuleAttributes_should_notCreateExpectationsOnAttackChainNode()
      throws Exception {
    // -- ARRANGE --
    String importId = XlsFixture.createDefaultXlsFile();

    ImportMapper importMapper =
        ImportMapperFixture.createImportMapper(XlsFixture.DEFAULT_INJECT_TYPE);
    AttackChainNodeImporter importer = importMapper.getAttackChainNodeImporters().getFirst();
    importer.getRuleAttributes().add(createRuleAttribute("expectation_name"));
    importer.getRuleAttributes().add(createRuleAttribute("expectation_description"));
    importer.getRuleAttributes().add(createRuleAttribute("expectation_score"));

    AttackChain attackChain = AttackChainFixture.getScheduledAttackChain();

    // -- ACT --
    ImportTestSummary result =
        attackChainNodeImportService.importAttackChainNodeIntoAttackChainFromXLS(
            attackChain, importMapper, importId, XlsFixture.DEFAULT_SHEET_NAME, 0, false);

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(1, result.getTotalNumberOfAttackChainNodes());
    assertFalse(result.getAttackChainNodes().isEmpty());

    AttackChainNode importedAttackChainNode = result.getAttackChainNodes().getFirst();
    assertNotNull(importedAttackChainNode.getContent());
    assertNull(importedAttackChainNode.getContent().get("expectations"));
  }
}
