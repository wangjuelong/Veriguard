package io.veriguard.rest.exercise;

import static io.veriguard.rest.exercise.AttackChainRunApi.EXERCISE_URI;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.ImportMapper;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.repository.ImportMapperRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.UnprocessableContentException;
import io.veriguard.rest.exercise.service.AttackChainRunService;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.scenario.form.AttackChainNodesImportInput;
import io.veriguard.rest.scenario.response.ImportTestSummary;
import io.veriguard.service.AttackChainNodeImportService;
import io.swagger.v3.oas.annotations.Operation;
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
public class AttackChainRunImportApi extends RestBehavior {

  private final AttackChainNodeImportService attackChainNodeImportService;
  private final ImportMapperRepository importMapperRepository;
  private final AttackChainRunService attackChainRunService;

  @PostMapping(EXERCISE_URI + "/{exerciseId}/xls/{importId}/dry")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Test the import of injects from an xls file")
  public ImportTestSummary dryRunImportXLSFile(
      @PathVariable @NotBlank final String attackChainRunId,
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final AttackChainNodesImportInput input) {
    AttackChainRun attackChainRun = this.attackChainRunService.attackChainRun(attackChainRunId);

    // Getting the mapper to use
    ImportMapper importMapper =
        this.importMapperRepository
            .findById(UUID.fromString(input.getImportMapperId()))
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        String.format(
                            "The import mapper %s was not found", input.getImportMapperId())));

    return this.attackChainNodeImportService.importAttackChainNodeIntoAttackChainRunFromXLS(
        attackChainRun, importMapper, importId, input.getName(), input.getTimezoneOffset(), false);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/xls/{importId}/import")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Validate and import injects from an xls file")
  public ImportTestSummary validateImportXLSFile(
      @PathVariable @NotBlank final String attackChainRunId,
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final AttackChainNodesImportInput input) {
    AttackChainRun attackChainRun = this.attackChainRunService.attackChainRun(attackChainRunId);

    if (input.getLaunchDate() != null) {
      attackChainRun.setStart(input.getLaunchDate().toInstant());
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
        attackChainNodeImportService.importAttackChainNodeIntoAttackChainRunFromXLS(
            attackChainRun, importMapper, importId, input.getName(), input.getTimezoneOffset(), true);
    this.attackChainRunService.updateAttackChainRun(attackChainRun);
    return importTestSummary;
  }

  @PostMapping(
      path = EXERCISE_URI + "/{simulationId}/injects/import",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void attackChainNodesImport(
      @RequestPart("file") MultipartFile file,
      @PathVariable @NotBlank final String simulationId,
      HttpServletResponse response)
      throws Exception {
    if (file == null || file.isEmpty()) {
      throw new UnprocessableContentException("Insufficient input: file is required");
    }
    this.attackChainNodeImportService.importAttackChainNodesForSimulation(file, simulationId);
  }
}
