package io.veriguard.utils.fixtures.import_mapper;

import static io.veriguard.utils.fixtures.NodeContractFixture.createDefaultNodeContract;
import static io.veriguard.utils.fixtures.import_mapper.RuleAttributeFixture.createRuleAttribute;

import io.veriguard.database.model.ImportMapper;
import io.veriguard.database.model.AttackChainNodeImporter;
import java.util.ArrayList;
import java.util.Map;

public class ImportMapperFixture {

  public static final String DEFAULT_MAPPER_NAME = "TestMapper";

  public static final String DEFAULT_INJECT_TYPE_COLUMN = "A";

  public static final String DEFAULT_TITLE_NAME = "title";
  public static final String DEFAULT_TITLE_COLUMN = "B";

  public static final String DEFAULT_DESCRIPTION_NAME = "description";
  public static final String DEFAULT_DESCRIPTION_COLUMN = "C";

  public static final String DEFAULT_TRIGGER_TIME_NAME = "trigger_time";
  public static final String DEFAULT_TRIGGER_TIME_COLUMN = "D";

  public static ImportMapper createImportMapper(String attackChainNodeTypeValue) {
    return createImportMapper(DEFAULT_MAPPER_NAME, attackChainNodeTypeValue, DEFAULT_INJECT_TYPE_COLUMN);
  }

  public static ImportMapper createImportMapper(
      String mapperName, String attackChainNodeTypeValue, String attackChainNodeTypeColumn) {
    ImportMapper importMapper = new ImportMapper();
    importMapper.setName(mapperName);
    importMapper.setAttackChainNodeTypeColumn(attackChainNodeTypeColumn);
    importMapper.setAttackChainNodeImporters(new ArrayList<>());
    importMapper.getAttackChainNodeImporters().add(createAttackChainNodeImporter(attackChainNodeTypeValue));
    return importMapper;
  }

  public static AttackChainNodeImporter createAttackChainNodeImporter(String importTypeValue) {
    AttackChainNodeImporter attackChainNodeImporter = new AttackChainNodeImporter();
    attackChainNodeImporter.setImportTypeValue(importTypeValue);
    attackChainNodeImporter.setNodeContract(createDefaultNodeContract());
    attackChainNodeImporter.setRuleAttributes(new ArrayList<>());
    attackChainNodeImporter
        .getRuleAttributes()
        .add(createRuleAttribute(DEFAULT_TITLE_NAME, DEFAULT_TITLE_COLUMN));
    attackChainNodeImporter
        .getRuleAttributes()
        .add(createRuleAttribute(DEFAULT_DESCRIPTION_NAME, DEFAULT_DESCRIPTION_COLUMN));
    attackChainNodeImporter
        .getRuleAttributes()
        .add(
            createRuleAttribute(
                DEFAULT_TRIGGER_TIME_NAME, DEFAULT_TRIGGER_TIME_COLUMN, Map.of("timePattern", "")));
    return attackChainNodeImporter;
  }
}
