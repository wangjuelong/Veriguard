package io.veriguard.database.model.contract;

/** 软件分类 —— 用于 §3.4 漏洞利用、§3.5 边界 WAF 攻击效用按软件类别归类 */
public enum SoftwareCategory {
  web_component,
  security_product,
  application,
  domestic_commercial,
  cms,
  network_device,
  os,
  database
}
