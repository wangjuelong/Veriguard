package io.veriguard.rest.mapper;

import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.ImportMapper;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.raw.RawPaginationImportMapper;
import io.veriguard.database.repository.ImportMapperRepository;
import io.veriguard.rest.attack_chain.form.AttackChainNodesImportTestInput;
import io.veriguard.rest.attack_chain.response.ImportPostSummary;
import io.veriguard.rest.attack_chain.response.ImportTestSummary;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.FileTooBigException;
import io.veriguard.rest.exception.ImportException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.mapper.form.ExportMapperInput;
import io.veriguard.rest.mapper.form.ImportMapperAddInput;
import io.veriguard.rest.mapper.form.ImportMapperUpdateInput;
import io.veriguard.service.AttackChainNodeImportService;
import io.veriguard.service.MapperService;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.constants.Constants;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

@RestController
@RequiredArgsConstructor
@Slf4j
public class MapperApi extends RestBehavior {

  private final ImportMapperRepository importMapperRepository;
  private final MapperService mapperService;
  private final AttackChainNodeImportService attackChainNodeImportService;

  // 25mb in byte
  private static final int MAXIMUM_FILE_SIZE_ALLOWED = 25 * 1000 * 1000;
  private static final List<String> ACCEPTED_FILE_TYPES = List.of("xls", "xlsx");

  @PostMapping("/api/mappers/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.MAPPER)
  public Page<RawPaginationImportMapper> getImportMapper(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
            this.importMapperRepository::findAll, searchPaginationInput, ImportMapper.class)
        .map(RawPaginationImportMapper::new);
  }

  @GetMapping("/api/mappers/{mapperId}")
  @RBAC(resourceId = "#mapperId", actionPerformed = Action.READ, resourceType = ResourceType.MAPPER)
  public ImportMapper getImportMapperById(@PathVariable String mapperId) {
    return importMapperRepository
        .findById(UUID.fromString(mapperId))
        .orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping("/api/mappers")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.MAPPER)
  public ImportMapper createImportMapper(
      @RequestBody @Valid final ImportMapperAddInput importMapperAddInput) {
    return mapperService.createAndSaveImportMapper(importMapperAddInput);
  }

  @PostMapping(value = "/api/mappers/export")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.MAPPER)
  public void exportMappers(
      @RequestBody @Valid final ExportMapperInput exportMapperInput, HttpServletResponse response) {
    try {
      String jsonMappers = mapperService.exportMappers(exportMapperInput.getIdsToExport());

      String rightNow = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
      String name =
          exportMapperInput
              .getName()
              .replace(Constants.IMPORTED_OBJECT_NAME_SUFFIX, "")
              .replace(" ", "");
      String exportFileName = name.length() > 15 ? name.substring(0, 15) : name;
      String filename = MessageFormat.format("{0}-{1}.json", exportFileName, rightNow);

      response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      response.setStatus(HttpServletResponse.SC_OK);

      response.getOutputStream().write(jsonMappers.getBytes(StandardCharsets.UTF_8));
      response.getOutputStream().flush();
      response.getOutputStream().close();
    } catch (IOException e) {
      throw new RuntimeException("Error during export", e);
    }
  }

  @Operation(description = "Export all datas from a specific target (endpoint,...)")
  @PostMapping(value = "/api/mappers/export/csv")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.MAPPER)
  @LogExecutionTime
  public void exportMappersCsv(
      @RequestParam TargetType targetType,
      @RequestBody @Valid final SearchPaginationInput input,
      HttpServletResponse response) {
    mapperService.exportMappersCsv(targetType, input, response);
  }

  @PostMapping("/api/mappers/import")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.MAPPER)
  public void importMappers(@RequestPart("file") @NotNull MultipartFile file)
      throws ImportException {
    try {
      mapperService.importMappers(
          mapper.readValue(file.getInputStream().readAllBytes(), new TypeReference<>() {}));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new ImportException("Mapper import", "Error during import");
    }
  }

  @PostMapping("/api/mappers/{mapperId}")
  @RBAC(
      resourceId = "#mapperId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.MAPPER)
  @Operation(summary = "Duplicate XLS mapper by id")
  public ImportMapper duplicateMapper(@PathVariable @NotBlank final String mapperId) {
    return mapperService.getDuplicateImportMapper(mapperId);
  }

  @PutMapping("/api/mappers/{mapperId}")
  @RBAC(
      resourceId = "#mapperId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.MAPPER)
  public ImportMapper updateImportMapper(
      @PathVariable String mapperId,
      @Valid @RequestBody ImportMapperUpdateInput importMapperUpdateInput) {
    return mapperService.updateImportMapper(mapperId, importMapperUpdateInput);
  }

  @DeleteMapping("/api/mappers/{mapperId}")
  @RBAC(
      resourceId = "#mapperId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.MAPPER)
  public void deleteImportMapper(@PathVariable String mapperId) {
    importMapperRepository.deleteById(UUID.fromString(mapperId));
  }

  @PostMapping("/api/mappers/store")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.MAPPER)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Import injects into an xls file")
  public ImportPostSummary importXLSFile(@RequestPart("file") @NotNull MultipartFile file) {
    validateUploadedFile(file);
    return attackChainNodeImportService.storeXlsFileForImport(file);
  }

  @PostMapping("/api/mappers/store/{importId}")
  @RBAC(
      resourceId = "#importId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.MAPPER)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Test the import of injects from an xls file")
  public ImportTestSummary testImportXLSFile(
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final AttackChainNodesImportTestInput input) {
    ImportMapper importMapper = mapperService.createImportMapper(input.getImportMapper());
    importMapper
        .getAttackChainNodeImporters()
        .forEach(
            attackChainNodeImporter -> {
              attackChainNodeImporter.setId(UUID.randomUUID().toString());
              attackChainNodeImporter
                  .getRuleAttributes()
                  .forEach(ruleAttribute -> ruleAttribute.setId(UUID.randomUUID().toString()));
            });
    AttackChain attackChain = new AttackChain();
    attackChain.setRecurrenceStart(Instant.now());
    return attackChainNodeImportService.importAttackChainNodeIntoAttackChainFromXLS(
        attackChain, importMapper, importId, input.getName(), input.getTimezoneOffset(), false);
  }

  // -- IMPORT --
  @Operation(
      description = "Import all datas from a specific target (endpoint,...) through a csv file")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.MAPPER)
  @PostMapping("/api/mappers/import/csv")
  @LogExecutionTime
  @Transactional(rollbackOn = Exception.class)
  public void importEndpoints(
      @RequestParam TargetType targetType, @RequestPart("file") @NotNull MultipartFile file)
      throws Exception {
    mapperService.importMappersCsv(file, targetType);
  }

  private void validateUploadedFile(MultipartFile file) {
    validateExtension(file);
    validateFileSize(file);
  }

  private void validateExtension(MultipartFile file) {
    String extension = FilenameUtils.getExtension(file.getOriginalFilename());
    if (!ACCEPTED_FILE_TYPES.contains(extension)) {
      throw new UnsupportedMediaTypeException(
          "Only the following file types are accepted : " + String.join(", ", ACCEPTED_FILE_TYPES));
    }
  }

  private void validateFileSize(MultipartFile file) {
    if (file.getSize() >= MAXIMUM_FILE_SIZE_ALLOWED) {
      throw new FileTooBigException("File size cannot be greater than 25 Mb");
    }
  }
}
