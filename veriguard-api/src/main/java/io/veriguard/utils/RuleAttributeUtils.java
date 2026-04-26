package io.veriguard.utils;

import io.veriguard.database.model.RuleAttribute;

public class RuleAttributeUtils {

  private RuleAttributeUtils() {}

  // -- VALIDATION --

  /**
   * Checks whether a rule attribute has usable data, i.e., mapped columns or a non-blank default
   * value.
   */
  public static boolean hasColumnsOrDefaultValue(RuleAttribute ruleAttribute) {
    return (ruleAttribute.getColumns() != null && !ruleAttribute.getColumns().isEmpty())
        || !StringUtils.isBlank(ruleAttribute.getDefaultValue());
  }
}
