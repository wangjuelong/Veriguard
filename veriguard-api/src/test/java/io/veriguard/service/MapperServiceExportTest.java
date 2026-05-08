package io.veriguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.ImportMapper;
import io.veriguard.database.repository.ImportMapperRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MapperServiceExportTest extends IntegrationTest {

  @Autowired private ImportMapperRepository importMapperRepository;

  @Autowired private MapperService mapperService;

  @DisplayName("Test exporting a mapper")
  @Test
  void exportMapper() throws Exception {
    // -- PREPARE --
    ImportMapper mapper = new ImportMapper();
    mapper.setName("Test Mapper");
    mapper.setAttackChainNodeTypeColumn("injectType");
    mapper.setAttackChainNodeImporters(new ArrayList<>());
    ImportMapper mapperSaved = this.importMapperRepository.save(mapper);

    // -- EXECUTE --
    String json = this.mapperService.exportMappers(List.of(mapperSaved.getId()));

    // -- ASSERT --
    assertEquals(
        "[{"
            + "\"import_mapper_name\":\"Test Mapper\","
            + "\"import_mapper_inject_type_column\":\"injectType\","
            + "\"import_mapper_inject_importers\":[]"
            + "}]",
        json);

    // -- CLEAN --
    this.importMapperRepository.delete(mapperSaved);
  }
}
