package io.veriguard.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.OfflinePackAuditEntity;
import io.veriguard.database.repository.OfflinePackAuditRepository;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for {@link OfflinePackAuditService}. Task C.13. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfflinePackAuditServiceTest {

  @Mock private OfflinePackAuditRepository repository;

  private OfflinePackAuditService service;

  @BeforeEach
  void setUp() {
    service = new OfflinePackAuditService(repository);
  }

  private byte[] sha256Of(String input) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    return md.digest(input.getBytes());
  }

  @Test
  @DisplayName("recordExport: 写一行新 audit, exported_* 字段全填, imported_* 字段 NULL")
  void recordExport_persistsExportRow() throws Exception {
    UUID packId = UUID.randomUUID();
    byte[] hash = sha256Of("ciphertext-001");

    when(repository.save(any(OfflinePackAuditEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    OfflinePackAuditEntity saved =
        service.recordExport(
            packId, "agent-1", "veriguard-prod-001", "operator-zhang", "10.0.0.5", hash, 247);

    ArgumentCaptor<OfflinePackAuditEntity> captor =
        ArgumentCaptor.forClass(OfflinePackAuditEntity.class);
    verify(repository).save(captor.capture());
    OfflinePackAuditEntity actual = captor.getValue();

    assertThat(actual.getPackId()).isEqualTo(packId);
    assertThat(actual.getAgentId()).isEqualTo("agent-1");
    assertThat(actual.getPlatformId()).isEqualTo("veriguard-prod-001");
    assertThat(actual.getExportedBy()).isEqualTo("operator-zhang");
    assertThat(actual.getExportedFromIp()).isEqualTo("10.0.0.5");
    assertThat(actual.getExportedCiphertextSha256()).containsExactly(hash);
    assertThat(actual.getTaskCount()).isEqualTo(247);
    assertThat(actual.getIssuedAt()).isNotNull();

    assertThat(actual.getImportedAt()).isNull();
    assertThat(actual.getImportedBy()).isNull();
    assertThat(actual.getResultCount()).isNull();
    assertThat(actual.getRejectedCount()).isNull();

    assertThat(saved.getPackId()).isEqualTo(packId);
  }

  @Test
  @DisplayName("recordExport: task_count 越界（< 0 / > 1000）→ 抛")
  void recordExport_rejectsTaskCountOutOfBounds() throws Exception {
    UUID packId = UUID.randomUUID();
    byte[] hash = sha256Of("c");

    assertThatThrownBy(() -> service.recordExport(packId, "a", "p", "u", null, hash, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taskCount");

    assertThatThrownBy(() -> service.recordExport(packId, "a", "p", "u", null, hash, 1001))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taskCount");

    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("recordExport: ciphertext_sha256 长度 != 32 → 抛")
  void recordExport_rejectsInvalidSha256Length() {
    UUID packId = UUID.randomUUID();
    byte[] wrong = new byte[16];

    assertThatThrownBy(() -> service.recordExport(packId, "a", "p", "u", null, wrong, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SHA-256");

    assertThatThrownBy(() -> service.recordExport(packId, "a", "p", "u", null, null, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("recordImport: 找到 pack → 更新 import 字段, save once")
  void recordImport_updatesExistingRow() {
    UUID packId = UUID.randomUUID();
    OfflinePackAuditEntity existing = new OfflinePackAuditEntity();
    existing.setPackId(packId);
    existing.setAgentId("agent-1");

    when(repository.findById(packId)).thenReturn(Optional.of(existing));
    when(repository.save(any(OfflinePackAuditEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    Optional<OfflinePackAuditEntity> result =
        service.recordImport(packId, "operator-wang", "10.0.0.6", 247, 3);

    assertThat(result).isPresent();
    OfflinePackAuditEntity updated = result.get();
    assertThat(updated.getImportedBy()).isEqualTo("operator-wang");
    assertThat(updated.getImportedFromIp()).isEqualTo("10.0.0.6");
    assertThat(updated.getResultCount()).isEqualTo(247);
    assertThat(updated.getRejectedCount()).isEqualTo(3);
    assertThat(updated.getImportedAt()).isNotNull();
  }

  @Test
  @DisplayName("recordImport: 未找到 pack → 返回 empty, 不 save")
  void recordImport_unknownPackReturnsEmpty() {
    UUID packId = UUID.randomUUID();
    when(repository.findById(packId)).thenReturn(Optional.empty());

    Optional<OfflinePackAuditEntity> result =
        service.recordImport(packId, "operator-wang", "10.0.0.6", 247, 0);

    assertThat(result).isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("recordImport: 负数 count → 抛")
  void recordImport_rejectsNegativeCounts() {
    UUID packId = UUID.randomUUID();
    assertThatThrownBy(() -> service.recordImport(packId, "u", null, -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.recordImport(packId, "u", null, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
    verify(repository, never()).findById(any());
    verify(repository, never()).save(any());
  }
}
