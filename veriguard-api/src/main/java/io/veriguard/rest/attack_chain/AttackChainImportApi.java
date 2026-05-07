package io.veriguard.rest.attack_chain;

import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.ImportMapper;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.repository.ImportMapperRepository;
import io.veriguard.rest.attack_chain.form.AttackChainNodesImportInput;
import io.veriguard.rest.attack_chain.response.ImportTestSummary;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.UnprocessableContentException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.AttackChainNodeImportService;
import io.veriguard.service.scenario.AttackChainService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AttackChainImportApi extends RestBehavior {

  private final AttackChainNodeImportService attackChainNodeImportService;
  private final ImportMapperRepository importMapperRepository;
  private final AttackChainService attackChainService;

  @PostMapping(SCENARIO_URI + "/{attackChainId}/xls/{importId}/dry")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Test the import of injects from an xls file")
  public ImportTestSummary dryRunImportXLSFile(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final AttackChainNodesImportInput input) {
    AttackChain attackChain = attackChainService.attackChain(attackChainId);

    // Getting the mapper to use
    ImportMapper importMapper =
        importMapperRepository
            .findById(UUID.fromString(input.getImportMapperId()))
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        String.format(
                            "The import mapper %s was not found", input.getImportMapperId())));

    return attackChainNodeImportService.importAttackChainNodeIntoAttackChainFromXLS(
        attackChain, importMapper, importId, input.getName(), input.getTimezoneOffset(), false);
  }

  @PostMapping(SCENARIO_URI + "/{attackChainId}/xls/{importId}/import")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Validate and import injects from an xls file")
  public ImportTestSummary validateImportXLSFile(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final AttackChainNodesImportInput input) {
    AttackChain attackChain = attackChainService.attackChain(attackChainId);

    if (input.getLaunchDate() != null) {
      attackChain.setRecurrenceStart(input.getLaunchDate().toInstant());
    }

    // Getting the mapper to use
    ImportMapper importMapper =
        importMapperRepository
            .findById(UUID.fromString(input.getImportMapperId()))
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        String.format(
                            "The import mapper %s was not found", input.getImportMapperId())));

    ImportTestSummary importTestSummary =
        attackChainNodeImportService.importAttackChainNodeIntoAttackChainFromXLS(
            attackChain, importMapper, importId, input.getName(), input.getTimezoneOffset(), true);
    attackChainService.updateAttackChain(attackChain);
    return importTestSummary;
  }

  @PostMapping(
      path = SCENARIO_URI + "/{attackChainId}/injects/import",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void attackChainNodesImport(
      @RequestPart("file") MultipartFile file,
      @PathVariable @NotBlank final String attackChainId,
      HttpServletResponse response)
      throws Exception {
    if (file == null || file.isEmpty()) {
      throw new UnprocessableContentException("Insufficient input: file is required");
    }
    this.attackChainNodeImportService.importAttackChainNodesForAttackChain(file, attackChainId);
  }
}
