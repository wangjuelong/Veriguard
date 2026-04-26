package io.veriguard.rest.inject;

import io.veriguard.aop.RBAC;
import io.veriguard.api.inject_result.dto.InjectResultPayloadExecutionOutput;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.utils.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InjectExecutionResultApi extends RestBehavior {

  public static final String INJECT_EXECUTION_URI = "/api/injects/{injectId}";

  private final InjectExecutionResultService injectExecutionService;

  @GetMapping(INJECT_EXECUTION_URI + "/execution-result")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public InjectResultPayloadExecutionOutput injectExecutionResultPayload(
      @PathVariable @NotBlank final String injectId,
      @RequestParam @NotBlank final String targetId,
      @RequestParam @NotNull final TargetType targetType) {
    return this.injectExecutionService.injectExecutionResultPayload(injectId, targetId, targetType);
  }
}
