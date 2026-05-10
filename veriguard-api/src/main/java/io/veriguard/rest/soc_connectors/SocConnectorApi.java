package io.veriguard.rest.soc_connectors;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.attackchain.soc.ConnectorNotFoundException;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SOC connector 状态 + 规则元信息 REST API（spec §6.3.6 / §4.5）.
 *
 * <p>给前端 Phase 11 组件 {@code SocConnectorStatusList} 与 {@code SocConnectorRulePicker}
 * 提供数据；同时也是 {@code ParameterSetEditDialog} 选 correlation rule 时的下拉来源。
 *
 * <p>所有端点都要 {@link Action#READ} / {@link ResourceType#PLATFORM_SETTING} —— 看 SOC
 * 状态等价于看平台集成配置。
 */
@RestController
@RequestMapping(SocConnectorApi.SOC_CONNECTOR_URI)
@RequiredArgsConstructor
public class SocConnectorApi {

  public static final String SOC_CONNECTOR_URI = "/api/soc_connectors";

  private final SocConnectorService socConnectorService;

  @GetMapping
  @Operation(summary = "List SOC connectors with health status")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<SocConnectorOutput> list() {
    return socConnectorService.list();
  }

  @PostMapping("/{connectorId}/refresh")
  @Operation(summary = "Refresh SOC connector health status")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public SocConnectorOutput refresh(@PathVariable @NotBlank String connectorId) {
    return socConnectorService.refresh(connectorId);
  }

  @GetMapping("/{connectorId}/rules")
  @Operation(summary = "List available correlation rules for a SOC connector")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<SocConnectorRuleOutput> listRules(@PathVariable @NotBlank String connectorId) {
    return socConnectorService.listRules(connectorId);
  }

  @ExceptionHandler(ConnectorNotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(ConnectorNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()));
  }
}
