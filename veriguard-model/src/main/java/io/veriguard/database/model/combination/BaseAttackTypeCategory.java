package io.veriguard.database.model.combination;

/**
 * 基础攻击类型类别 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR B5.
 *
 * <p>每个值与 V17__base_attack_types.sql 中 base_attack_type_category 列直接对齐.
 * 采用 lowercase 常量风格，与 {@link BypassDimensionCategory} / {@link
 * io.veriguard.database.model.contract.SoftwareCategory} 等枚举保持一致.
 */
public enum BaseAttackTypeCategory {
  /** Web 注入（sql / xss / xxe / ssti / oscommand 等）. */
  web_injection,
  /** Web 业务漏洞（csrf / open_redirect / upload / 路径穿越 / jwt 等）. */
  web_business,
  /** 凭证攻击（爆破 / 喷洒 / kerberoast / pass-the-hash 等）. */
  credential,
  /** 网络协议攻击（dns 隧道 / arp 欺骗 / smb relay 等）. */
  network_protocol,
  /** C2 / 远控（reverse shell / cobalt strike / sliver 等）. */
  c2,
  /** 主机持久化（systemd / cron / registry / 计划任务 等）. */
  persistence,
  /** 提权（sudo 滥用 / suid / uac bypass / 内核 exp 等）. */
  privilege_escalation,
  /** 信息探测（端口扫描 / smb 枚举 / wmi / ldap 枚举 等）. */
  discovery,
  /** 高危 CVE 利用（log4shell / spring4shell / fastjson rce 等）. */
  exploit_cve
}
