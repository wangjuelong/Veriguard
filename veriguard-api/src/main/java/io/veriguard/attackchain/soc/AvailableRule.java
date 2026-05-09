package io.veriguard.attackchain.soc;

/**
 * SOC 平台上可选规则的描述（spec §4.2）—— 给前端 "选择 correlation rule" 下拉填数据。
 *
 * @param ruleId SOC 平台内规则 ID
 * @param displayName 规则的友好名
 * @param description 规则描述（可空）
 * @param category 规则分类（如 {@code "Correlation"} / {@code "Detection"} / 自定义；可空）
 */
public record AvailableRule(
    String ruleId, String displayName, String description, String category) {

  public AvailableRule {
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("ruleId required");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName required");
    }
  }
}
