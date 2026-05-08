package io.veriguard.utils.mockMapper;

import io.veriguard.database.model.AttackChainNodeImporter;
import io.veriguard.database.model.ImportMapper;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.model.RuleAttribute;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class MockMapperUtils {

  public static ImportMapper createImportMapper() {
    ImportMapper importMapper = new ImportMapper();
    importMapper.setId(UUID.randomUUID().toString());
    importMapper.setName("Test");
    importMapper.setUpdateDate(Instant.now());
    importMapper.setCreationDate(Instant.now());
    importMapper.setAttackChainNodeTypeColumn("A");
    importMapper.setAttackChainNodeImporters(new ArrayList<>());

    importMapper.getAttackChainNodeImporters().add(createAttackChainNodeImporter());

    return importMapper;
  }

  public static AttackChainNodeImporter createAttackChainNodeImporter() {
    AttackChainNodeImporter attackChainNodeImporter = new AttackChainNodeImporter();
    attackChainNodeImporter.setId(UUID.randomUUID().toString());
    attackChainNodeImporter.setImportTypeValue("Test");
    NodeContract nodeContract = new NodeContract();
    nodeContract.setId(UUID.randomUUID().toString());
    attackChainNodeImporter.setNodeContract(nodeContract);
    attackChainNodeImporter.setRuleAttributes(new ArrayList<>());

    attackChainNodeImporter.getRuleAttributes().add(createRuleAttribute());
    return attackChainNodeImporter;
  }

  public static RuleAttribute createRuleAttribute() {
    RuleAttribute ruleAttribute = new RuleAttribute();
    ruleAttribute.setColumns("Test");
    ruleAttribute.setName("Test");
    ruleAttribute.setId(UUID.randomUUID().toString());
    ruleAttribute.setAdditionalConfig(Map.of("test", "test"));
    ruleAttribute.setDefaultValue("");
    return ruleAttribute;
  }
}
