package io.veriguard.rest.validation_parameter_set;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.validation_parameter_set.exceptions.CannotDeleteTemplateException;
import io.veriguard.rest.validation_parameter_set.exceptions.CannotEditTemplateException;
import io.veriguard.rest.validation_parameter_set.exceptions.DuplicateNameException;
import io.veriguard.rest.validation_parameter_set.exceptions.ParameterSetNotFoundException;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ValidationParameterSetApi.URI)
@RequiredArgsConstructor
public class ValidationParameterSetApi extends RestBehavior {

  public static final String URI = "/api/validation_parameter_sets";

  private final ValidationParameterSetService service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.VALIDATION_PARAMETER_SET)
  @LogExecutionTime
  public ValidationParameterSetOutput create(@Valid @RequestBody ValidationParameterSetInput input) {
    return ValidationParameterSetOutput.from(service.create(input));
  }

  @GetMapping("/{id}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.VALIDATION_PARAMETER_SET)
  @LogExecutionTime
  public ValidationParameterSetOutput get(@PathVariable @NotBlank UUID id) {
    return ValidationParameterSetOutput.from(service.get(id));
  }

  @PutMapping("/{id}")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.VALIDATION_PARAMETER_SET)
  @LogExecutionTime
  public ValidationParameterSetOutput update(
      @PathVariable @NotBlank UUID id, @Valid @RequestBody ValidationParameterSetInput input) {
    return ValidationParameterSetOutput.from(service.update(id, input));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RBAC(actionPerformed = Action.DELETE, resourceType = ResourceType.VALIDATION_PARAMETER_SET)
  @LogExecutionTime
  public void delete(@PathVariable @NotBlank UUID id) {
    service.delete(id);
  }

  @PostMapping("/search")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.VALIDATION_PARAMETER_SET)
  @LogExecutionTime
  public Page<ValidationParameterSetOutput> search(
      @RequestBody @Valid SearchPaginationInput input) {
    return service.search(input).map(ValidationParameterSetOutput::from);
  }

  @PostMapping("/{id}/duplicate")
  @ResponseStatus(HttpStatus.CREATED)
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.VALIDATION_PARAMETER_SET)
  @LogExecutionTime
  public ValidationParameterSetOutput duplicate(
      @PathVariable @NotBlank UUID id, @Valid @RequestBody DuplicateRequest body) {
    return ValidationParameterSetOutput.from(service.duplicate(id, body.newName()));
  }

  // -- 错误响应 -------------------------------------------------------

  @ExceptionHandler(DuplicateNameException.class)
  ResponseEntity<ProblemDetail> handleDuplicate(DuplicateNameException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()));
  }

  @ExceptionHandler({CannotDeleteTemplateException.class, CannotEditTemplateException.class})
  ResponseEntity<ProblemDetail> handleTemplateProtection(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()));
  }

  @ExceptionHandler(ParameterSetNotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(ParameterSetNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()));
  }

  public record DuplicateRequest(@NotBlank String newName) {}
}
