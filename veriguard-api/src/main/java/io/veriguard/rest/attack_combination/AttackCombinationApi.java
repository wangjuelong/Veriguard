package io.veriguard.rest.attack_combination;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.BypassDimensionPageOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.CombinationPreviewOutput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST 接口 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D1.
 *
 * <p>提供两个端点：
 * <ul>
 *   <li>GET /api/attack_combination/dimensions —— 维度列表（按 category 过滤 + 分页）</li>
 *   <li>POST /api/attack_combination/templates/preview —— 组合预览（笛卡尔积 size + 样例）</li>
 * </ul>
 *
 * <p>真正的 30 000 组合实例生成下沉到 PR D2.
 */
@RestController
public class AttackCombinationApi {

  public static final String DIMENSIONS_URI = "/api/attack_combination/dimensions";
  public static final String TEMPLATE_PREVIEW_URI = "/api/attack_combination/templates/preview";

  private final AttackCombinationService service;

  public AttackCombinationApi(AttackCombinationService service) {
    this.service = service;
  }

  @GetMapping(DIMENSIONS_URI)
  @Operation(summary = "List bypass dimensions (paged, optional category filter)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public BypassDimensionPageOutput listDimensions(
      @Parameter(description = "Optional category filter (encoding / chunking / casing / ...)")
          @RequestParam(value = "category", required = false)
          BypassDimensionCategory category,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(500) int size) {
    return service.listDimensions(Optional.ofNullable(category), page, size);
  }

  @PostMapping(TEMPLATE_PREVIEW_URI)
  @Operation(
      summary =
          "Preview attack combinations (Cartesian product size + first N transformed payload samples)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public CombinationPreviewOutput previewTemplates(@Valid @RequestBody PreviewRequest request) {
    return service.preview(
        request.baseAttackTypes(), request.bypassDimensionIds(), request.previewBasePayload());
  }

  /** Preview 请求体 —— 直接 wire snake_case. */
  public record PreviewRequest(
      @NotEmpty
          @com.fasterxml.jackson.annotation.JsonProperty("base_attack_types")
          List<String> baseAttackTypes,
      @NotEmpty
          @com.fasterxml.jackson.annotation.JsonProperty("bypass_dimension_ids")
          List<String> bypassDimensionIds,
      @NotNull
          @com.fasterxml.jackson.annotation.JsonProperty("preview_base_payload")
          String previewBasePayload) {}
}
