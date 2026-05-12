package io.veriguard.database.model.contract;

/** 防御层级 —— 用于 §5.1 攻击链编排按防御层级筛选用例 */
public enum DefenseLayer {
  BOUNDARY,
  TRAFFIC,
  APPLICATION,
  HOST,
  DATA
}
