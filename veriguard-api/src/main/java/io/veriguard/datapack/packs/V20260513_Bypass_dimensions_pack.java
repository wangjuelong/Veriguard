package io.veriguard.datapack.packs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.combination.transform.PayloadTransformRegistry;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import io.veriguard.database.repository.BypassDimensionRepository;
import io.veriguard.datapack.DataPack;
import io.veriguard.service.DataPackService;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 绕过维度库初始数据 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D1.
 *
 * <p>启动时从 {@code classpath:data/bypass_dimensions/120_dimensions.json} 读取 120 项维度,
 * 按 name 去重后落库. 借助 {@link DataPack} 框架确保仅执行一次（{@code data_packs}
 * 表登记 packId 后跳过）.
 *
 * <p>所有 transform_type 字段必须能在 {@link PayloadTransformRegistry} 找到对应实现,
 * 否则启动期 fail-fast 抛 {@link IllegalStateException}（符合项目 "No fallback code" 约定）.
 */
@Component
@Slf4j
public class V20260513_Bypass_dimensions_pack extends DataPack {

  private static final String DATA_RESOURCE = "data/bypass_dimensions/120_dimensions.json";

  private final BypassDimensionRepository repository;
  private final PayloadTransformRegistry transformRegistry;
  private final ObjectMapper objectMapper;

  public V20260513_Bypass_dimensions_pack(
      DataPackService dataPackService,
      BypassDimensionRepository repository,
      PayloadTransformRegistry transformRegistry,
      ObjectMapper objectMapper) {
    super(dataPackService);
    this.repository = repository;
    this.transformRegistry = transformRegistry;
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
          "Failed to parse bypass dimensions seed JSON: " + DATA_RESOURCE, e);
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("dimensions");
    if (entries == null || entries.isEmpty()) {
      throw new IllegalStateException(
          "Seed JSON missing 'dimensions' array or is empty: " + DATA_RESOURCE);
    }

    int created = 0;
    int skipped = 0;
    for (Map<String, Object> entry : entries) {
      String name = requiredString(entry, "name");
      if (repository.findByName(name).isPresent()) {
        skipped++;
        continue;
      }
      BypassDimension dim = toDimension(entry);
      repository.save(dim);
      created++;
    }

    log.info(
        "BypassDimensionSeeder: loaded {} entries from {} ({} created, {} skipped)",
        entries.size(),
        DATA_RESOURCE,
        created,
        skipped);
  }

  private BypassDimension toDimension(Map<String, Object> entry) {
    String name = requiredString(entry, "name");
    String categoryRaw = requiredString(entry, "category");
    String transformType = requiredString(entry, "transform_type");

    BypassDimensionCategory category;
    try {
      category = BypassDimensionCategory.valueOf(categoryRaw);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Unknown bypass_dimension category '"
              + categoryRaw
              + "' for entry '"
              + name
              + "' (allowed: "
              + java.util.Arrays.toString(BypassDimensionCategory.values())
              + ")");
    }

    // fail-fast: transform_type must resolve to a registered PayloadTransform
    if (!transformRegistry.knownTypes().contains(transformType)) {
      throw new IllegalStateException(
          "Unknown payload transform_type '"
              + transformType
              + "' for bypass dimension '"
              + name
              + "'. Known: "
              + transformRegistry.knownTypes());
    }

    BypassDimension dim = new BypassDimension();
    dim.setName(name);
    dim.setCategory(category);
    dim.setTransformType(transformType);
    dim.setDescription((String) entry.get("description"));

    @SuppressWarnings("unchecked")
    Map<String, Object> cfg = (Map<String, Object>) entry.get("transform_config");
    dim.setTransformConfig(cfg == null ? new HashMap<>() : new HashMap<>(cfg));
    return dim;
  }

  private static String requiredString(Map<String, Object> entry, String key) {
    Object v = entry.get(key);
    if (!(v instanceof String s) || s.isBlank()) {
      throw new IllegalStateException(
          "Seed entry missing required string field '" + key + "': " + entry);
    }
    return s;
  }
}
