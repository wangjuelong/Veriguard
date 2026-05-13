package io.veriguard.datapack.packs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.combination.BaseAttackType;
import io.veriguard.database.model.combination.BaseAttackTypeCategory;
import io.veriguard.database.model.combination.BaseAttackTypeTargetLayer;
import io.veriguard.database.repository.BaseAttackTypeRepository;
import io.veriguard.datapack.DataPack;
import io.veriguard.service.DataPackService;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 基础攻击类型库初始数据 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR B5.
 *
 * <p>启动时从 {@code classpath:data/base_attack_types/250_base_attack_types.json} 读取 ≥ 250 类
 * 基础攻击型，按 name 去重后落库。借助 {@link DataPack} 框架确保仅执行一次（{@code data_packs}
 * 表登记 packId 后跳过）。
 *
 * <p>未知 category / target_layer 启动期 fail-fast 抛 {@link IllegalStateException}（符合
 * 项目 "No fallback code" 约定）。
 */
@Component
@Slf4j
public class V20260514_Base_attack_types_pack extends DataPack {

  private static final String DATA_RESOURCE = "data/base_attack_types/250_base_attack_types.json";

  private final BaseAttackTypeRepository repository;
  private final ObjectMapper objectMapper;

  public V20260514_Base_attack_types_pack(
      DataPackService dataPackService,
      BaseAttackTypeRepository repository,
      ObjectMapper objectMapper) {
    super(dataPackService);
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doProcess() {
    Resource resource = new ClassPathResource(DATA_RESOURCE);
    if (!resource.exists()) {
      throw new IllegalStateException(
          "Required resource not found on classpath: " + DATA_RESOURCE);
    }

    Map<String, Object> root;
    try (InputStream in = resource.getInputStream()) {
      root = objectMapper.readValue(in, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse base attack types seed JSON: " + DATA_RESOURCE, e);
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries =
        (List<Map<String, Object>>) root.get("base_attack_types");
    if (entries == null || entries.isEmpty()) {
      throw new IllegalStateException(
          "Seed JSON missing 'base_attack_types' array or is empty: " + DATA_RESOURCE);
    }

    int created = 0;
    int skipped = 0;
    for (Map<String, Object> entry : entries) {
      String name = requiredString(entry, "name");
      if (repository.findByName(name).isPresent()) {
        skipped++;
        continue;
      }
      BaseAttackType type = toEntity(entry);
      repository.save(type);
      created++;
    }

    log.info(
        "BaseAttackTypeSeeder: loaded {} entries from {} ({} created, {} skipped)",
        entries.size(),
        DATA_RESOURCE,
        created,
        skipped);
  }

  private BaseAttackType toEntity(Map<String, Object> entry) {
    String name = requiredString(entry, "name");
    String categoryRaw = requiredString(entry, "category");
    String displayLabel = requiredString(entry, "display_label");
    String targetLayerRaw = requiredString(entry, "target_layer");
    Integer severityScore = requiredInt(entry, "severity_score");

    BaseAttackTypeCategory category;
    try {
      category = BaseAttackTypeCategory.valueOf(categoryRaw);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Unknown base_attack_type category '"
              + categoryRaw
              + "' for entry '"
              + name
              + "' (allowed: "
              + java.util.Arrays.toString(BaseAttackTypeCategory.values())
              + ")");
    }

    BaseAttackTypeTargetLayer targetLayer;
    try {
      targetLayer = BaseAttackTypeTargetLayer.valueOf(targetLayerRaw);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Unknown base_attack_type target_layer '"
              + targetLayerRaw
              + "' for entry '"
              + name
              + "' (allowed: "
              + java.util.Arrays.toString(BaseAttackTypeTargetLayer.values())
              + ")");
    }

    if (severityScore < 0 || severityScore > 100) {
      throw new IllegalStateException(
          "severity_score must be in [0,100] for entry '" + name + "', got: " + severityScore);
    }

    BaseAttackType type = new BaseAttackType();
    type.setName(name);
    type.setCategory(category);
    type.setDisplayLabel(displayLabel);
    type.setTargetLayer(targetLayer);
    type.setSeverityScore(severityScore);
    type.setDescription((String) entry.get("description"));
    type.setDefaultPayload((String) entry.get("default_payload"));
    type.setAttackPatternId((String) entry.get("attack_pattern_id"));
    return type;
  }

  private static String requiredString(Map<String, Object> entry, String key) {
    Object v = entry.get(key);
    if (!(v instanceof String s) || s.isBlank()) {
      throw new IllegalStateException(
          "Seed entry missing required string field '" + key + "': " + entry);
    }
    return s;
  }

  private static Integer requiredInt(Map<String, Object> entry, String key) {
    Object v = entry.get(key);
    if (v instanceof Integer i) {
      return i;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    throw new IllegalStateException(
        "Seed entry missing required integer field '" + key + "': " + entry);
  }
}
