package io.veriguard.database.model.combination;

/**
 * 绕过维度类别 —— IPv6 安全验证系统 §3.6 ★2 攻击组合.
 *
 * <p>每个值与 V11__attack_combination_init.sql 中 bypass_dimension_category 列直接对齐. 采用
 * lowercase 常量风格，与 {@link io.veriguard.database.model.contract.SoftwareCategory} /
 * {@link io.veriguard.database.model.contract.DefenseLayer} 等枚举保持一致.
 */
public enum BypassDimensionCategory {
  /** 编码（base64 / url / hex / utf-7 / utf-8-bom 等）. */
  encoding,
  /** 分块（HTTP chunked / 段间长延迟 / 包级分片）. */
  chunking,
  /** 大小写混淆（mixed / random / 同形 Unicode 大小写映射）. */
  casing,
  /** 参数顺序扰乱. */
  param_order,
  /** 噪声前后缀（空白 / tab / 控制字符 / 随机数据）. */
  noise,
  /** Unicode 变形（全角 / 同形异义 / 零宽 / 组合字符）. */
  unicode,
  /** 注释注入（SQL # / -- / /* *\/，XSS <!-- -->）. */
  comment,
  /** 其他（Host header / smuggling / case-flip / 协议层规避等）. */
  other
}
