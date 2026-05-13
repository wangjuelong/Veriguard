package io.veriguard.combination.cluster;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.Endpoint;
import org.springframework.stereotype.Component;

/**
 * 设备视角 cluster key 推导 —— IPv6 安全验证系统 §3.6 ★2 PR D3.
 *
 * <p>推导优先级（YAGNI，baseline 不引入新表 / 新字段）：
 * <ol>
 *   <li>Asset 是 Endpoint：取 Endpoint.hostname（已小写归一）</li>
 *   <li>否则：fall back 到 asset_name（用 asset 自身名作为退化的 device 标签）</li>
 *   <li>asset 或 name 为空：返回 "unknown-device-{asset_id}" 兜底（fail visibly，但不 throw）</li>
 * </ol>
 *
 * <p>未来由专门 PR 为防御设备（如 EDR 主机本身）建模时再扩展.
 */
@Component
public class DeviceKeyExtractor {

  /**
   * 推导设备 key.
   *
   * @param asset Asset 实体（可能是 Endpoint 子类）
   * @return 非空设备 key（hostname / asset_name / unknown 兜底之一）
   */
  public String deriveDeviceKey(Asset asset) {
    if (asset == null) {
      throw new IllegalArgumentException("asset must not be null when deriving device_key");
    }
    if (asset instanceof Endpoint endpoint) {
      String hostname = endpoint.getHostname();
      if (hostname != null && !hostname.isBlank()) {
        return hostname;
      }
    }
    String name = asset.getName();
    if (name != null && !name.isBlank()) {
      return name;
    }
    return "unknown-device-" + asset.getId();
  }
}
