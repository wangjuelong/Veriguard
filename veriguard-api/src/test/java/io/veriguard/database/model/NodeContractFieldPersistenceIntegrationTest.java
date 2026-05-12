package io.veriguard.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.contract.DefenseLayer;
import io.veriguard.database.model.contract.NetworkProtocolFamily;
import io.veriguard.database.model.contract.SoftwareCategory;
import io.veriguard.database.model.contract.TargetOs;
import io.veriguard.database.repository.NodeContractRepository;
import io.veriguard.database.repository.NodeExecutorRepository;
import io.veriguard.utils.fixtures.NodeContractFixture;
import io.veriguard.utilstest.RabbitMQTestListener;
import jakarta.persistence.Query;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成测试：验证 A3 新增的 6 个 NodeContract 字段通过 JPA + Hibernate + PostgreSQL 完整 round-trip。
 *
 * <p>A3-3 ({@code NodeContractFieldExtensionTest}) 覆盖了纯 Jackson 层；本测试覆盖 ORM/DB 层：
 *
 * <ul>
 *   <li>枚举映射 (@Enumerated(STRING)) 在 DB 列里以 lowercase {@code name()} 形式落盘
 *   <li>{@code networkDependent} 原语 boolean 默认 false
 *   <li>{@code rollbackSteps} JsonNode 通过 hypersistence {@code JsonType} 进入 jsonb 列
 * </ul>
 *
 * <p>需 docker dev env (Postgres test container) 才能跑——与项目既有 {@link IntegrationTest} 基类要求一致。
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Transactional
class NodeContractFieldPersistenceIntegrationTest extends IntegrationTest {

  @Autowired private NodeContractRepository nodeContractRepository;
  @Autowired private NodeExecutorRepository nodeExecutorRepository;
  @Autowired private ObjectMapper objectMapper;

  /** 持久化一份带 {@link NodeExecutor} 的最小 contract；返回 contract id 以便后续重载。 */
  private String persistContract(NodeContract contract) {
    // NodeExecutor is a required FK on NodeContract; persist it first via repo to give it a row.
    nodeExecutorRepository.save(contract.getNodeExecutor());
    nodeContractRepository.save(contract);
    entityManager.flush();
    entityManager.clear();
    return contract.getId();
  }

  @Test
  @DisplayName("6 个新字段写入后从 DB 重载，全部 round-trip 等值")
  void persistAllSixFields_thenReload_assertRoundTrip() throws Exception {
    // -- ARRANGE --
    NodeContract contract = NodeContractFixture.createDefaultNodeContract();
    contract.setSoftwareCategory(SoftwareCategory.web_component);
    contract.setDefenseLayer(DefenseLayer.application);
    contract.setNetworkProtocolFamily(NetworkProtocolFamily.ipv6);
    contract.setTargetOs(TargetOs.linux);
    contract.setNetworkDependent(true);
    JsonNode rollback =
        objectMapper.readTree("[{\"action\":\"delete_file\",\"path\":\"/tmp/a3-test\"}]");
    contract.setRollbackSteps(rollback);

    // -- ACT --
    String id = persistContract(contract);
    Optional<NodeContract> reloaded = nodeContractRepository.findById(id);

    // -- ASSERT --
    assertThat(reloaded).as("contract reloads from DB").isPresent();
    NodeContract loaded = reloaded.get();
    assertThat(loaded.getSoftwareCategory()).isEqualTo(SoftwareCategory.web_component);
    assertThat(loaded.getDefenseLayer()).isEqualTo(DefenseLayer.application);
    assertThat(loaded.getNetworkProtocolFamily()).isEqualTo(NetworkProtocolFamily.ipv6);
    assertThat(loaded.getTargetOs()).isEqualTo(TargetOs.linux);
    assertThat(loaded.isNetworkDependent()).isTrue();
    assertThat(loaded.getRollbackSteps())
        .as("rollback_steps jsonb round-trips structurally")
        .isNotNull();
    assertThat(loaded.getRollbackSteps().isArray()).isTrue();
    assertThat(loaded.getRollbackSteps().get(0).get("action").asText()).isEqualTo("delete_file");
    assertThat(loaded.getRollbackSteps().get(0).get("path").asText()).isEqualTo("/tmp/a3-test");
  }

  @Test
  @DisplayName("未显式设值时：4 个枚举 = null、network_dependent = false、rollback_steps = null")
  void persistMinimalFields_thenReload_assertDefaults() throws Exception {
    // -- ARRANGE --
    NodeContract contract = NodeContractFixture.createDefaultNodeContract();
    // intentionally do not set any of the 6 new fields

    // -- ACT --
    String id = persistContract(contract);
    NodeContract loaded =
        nodeContractRepository
            .findById(id)
            .orElseThrow(() -> new AssertionError("contract should reload"));

    // -- ASSERT --
    assertThat(loaded.getSoftwareCategory()).as("softwareCategory defaults to null").isNull();
    assertThat(loaded.getDefenseLayer()).as("defenseLayer defaults to null").isNull();
    assertThat(loaded.getNetworkProtocolFamily())
        .as("networkProtocolFamily defaults to null")
        .isNull();
    assertThat(loaded.getTargetOs()).as("targetOs defaults to null").isNull();
    assertThat(loaded.isNetworkDependent())
        .as("primitive boolean networkDependent defaults to false")
        .isFalse();
    assertThat(loaded.getRollbackSteps()).as("rollbackSteps defaults to null").isNull();
  }

  @Test
  @DisplayName("枚举以 lowercase string 形式存盘——native query 验证 'network_device' 不是 UPPER_SNAKE_CASE")
  void enumValuesPersistAsLowercaseString_thenLoadCorrectly() throws Exception {
    // -- ARRANGE --
    NodeContract contract = NodeContractFixture.createDefaultNodeContract();
    // network_device is the only multi-word SoftwareCategory value — exercises the underscore.
    contract.setSoftwareCategory(SoftwareCategory.network_device);

    // -- ACT --
    String id = persistContract(contract);

    // Verify the raw DB column stores the literal lowercase enum name.
    Query nativeQuery =
        entityManager.createNativeQuery(
            "SELECT injector_contract_software_category "
                + "FROM injectors_contracts "
                + "WHERE injector_contract_id = ?");
    nativeQuery.setParameter(1, id);
    Object rawValue = nativeQuery.getSingleResult();

    // -- ASSERT (DB layer) --
    assertThat(rawValue)
        .as("DB column must store the literal lowercase enum name (not UPPER_SNAKE_CASE)")
        .isEqualTo("network_device");

    // -- ASSERT (ORM round-trip) --
    NodeContract loaded =
        nodeContractRepository
            .findById(id)
            .orElseThrow(() -> new AssertionError("contract should reload"));
    assertThat(loaded.getSoftwareCategory())
        .as("Hibernate decodes the lowercase string back to the enum constant")
        .isEqualTo(SoftwareCategory.network_device);
  }
}
