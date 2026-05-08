package io.veriguard.rest.security_validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.VeriguardSandbox;
import io.veriguard.database.model.VeriguardSandbox.NetworkPolicy;
import io.veriguard.database.model.VeriguardSandbox.SampleType;
import io.veriguard.database.model.VeriguardSandbox.Status;
import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import io.veriguard.database.repository.VeriguardSandboxRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.InputValidationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SandboxServiceTest {

  @Mock private VeriguardSandboxRepository repository;
  @InjectMocks private SandboxService service;

  private SandboxInput baseInput;

  @BeforeEach
  void setUp() {
    baseInput =
        new SandboxInput(
            "勒索沙箱",
            "ransomware preset",
            NetworkPolicy.DENY_ALL,
            List.of(),
            true,
            List.of(SampleType.RANSOMWARE),
            Status.ACTIVE);
  }

  @Test
  void create_persists_sandbox_with_empty_network_rules() throws Exception {
    when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    SecurityValidationDtos.SandboxOutput output = service.createSandbox(baseInput);

    assertThat(output.networkRules()).isEmpty();
    assertThat(output.autoRestoreEnabled()).isTrue();
  }

  @Test
  void create_rejects_auto_restore_disabled() {
    SandboxInput input =
        new SandboxInput(
            baseInput.name(),
            baseInput.description(),
            baseInput.networkPolicy(),
            baseInput.networkRules(),
            false,
            baseInput.supportedSampleTypes(),
            baseInput.status());

    assertThatThrownBy(() -> service.createSandbox(input))
        .isInstanceOf(InputValidationException.class)
        .hasFieldOrPropertyWithValue("field", "sandbox_auto_restore_required");
  }

  @Test
  void create_rejects_empty_sample_types() {
    SandboxInput input =
        new SandboxInput(
            baseInput.name(),
            baseInput.description(),
            baseInput.networkPolicy(),
            baseInput.networkRules(),
            baseInput.autoRestoreEnabled(),
            List.of(),
            baseInput.status());

    assertThatThrownBy(() -> service.createSandbox(input))
        .isInstanceOf(InputValidationException.class)
        .hasFieldOrPropertyWithValue("field", "sandbox_supported_sample_types_empty");
  }

  @Test
  void create_translates_unique_violation_to_input_validation() {
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.createSandbox(baseInput))
        .isInstanceOf(InputValidationException.class)
        .hasFieldOrPropertyWithValue("field", "sandbox_name_duplicated");
  }

  @Test
  void create_accepts_multiple_network_rules() throws Exception {
    lenient().when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    VeriguardSandboxNetworkRule rule1 =
        new VeriguardSandboxNetworkRule(
            VeriguardSandboxNetworkRule.Direction.EGRESS,
            VeriguardSandboxNetworkRule.RuleAction.ALLOW,
            "TCP",
            "10.0.0.0/8",
            "443");
    SandboxInput input =
        new SandboxInput(
            baseInput.name(),
            baseInput.description(),
            baseInput.networkPolicy(),
            List.of(rule1, rule1),
            baseInput.autoRestoreEnabled(),
            baseInput.supportedSampleTypes(),
            baseInput.status());

    SecurityValidationDtos.SandboxOutput output = service.createSandbox(input);

    assertThat(output.networkRules()).hasSize(2);
  }

  @Test
  void update_persists_changes_to_existing_sandbox() throws Exception {
    VeriguardSandbox existing = new VeriguardSandbox();
    existing.setId("sb-001");
    existing.setName("旧名称");
    existing.setNetworkPolicy(NetworkPolicy.DENY_ALL);
    existing.setAutoRestoreEnabled(true);
    existing.setSupportedSampleTypes(List.of(SampleType.RANSOMWARE));
    existing.setStatus(Status.ACTIVE);
    when(repository.findById("sb-001")).thenReturn(Optional.of(existing));
    when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    SandboxInput input =
        new SandboxInput(
            "新名称",
            "updated description",
            NetworkPolicy.ALLOWLIST,
            List.of(),
            true,
            List.of(SampleType.MINER, SampleType.WORM),
            Status.INACTIVE);

    SecurityValidationDtos.SandboxOutput output = service.updateSandbox("sb-001", input);

    assertThat(output.name()).isEqualTo("新名称");
    assertThat(output.description()).isEqualTo("updated description");
    assertThat(output.networkPolicy()).isEqualTo(NetworkPolicy.ALLOWLIST);
    assertThat(output.status()).isEqualTo(Status.INACTIVE);
    assertThat(output.supportedSampleTypes()).containsExactly(SampleType.MINER, SampleType.WORM);

    ArgumentCaptor<VeriguardSandbox> captor = ArgumentCaptor.forClass(VeriguardSandbox.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("新名称");
  }

  @Test
  void update_throws_element_not_found_when_sandbox_missing() {
    when(repository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateSandbox("missing", baseInput))
        .isInstanceOf(ElementNotFoundException.class);
  }

  @Test
  void delete_removes_sandbox_by_id() {
    VeriguardSandbox existing = new VeriguardSandbox();
    existing.setId("sb-002");
    when(repository.findById("sb-002")).thenReturn(Optional.of(existing));

    service.deleteSandbox("sb-002");

    verify(repository, times(1)).delete(existing);
  }

  @Test
  void sandbox_returns_output_for_existing_id() {
    VeriguardSandbox existing = new VeriguardSandbox();
    existing.setId("sb-003");
    existing.setName("查询沙箱");
    existing.setNetworkPolicy(NetworkPolicy.DENY_ALL);
    existing.setAutoRestoreEnabled(true);
    existing.setSupportedSampleTypes(List.of(SampleType.RANSOMWARE));
    existing.setStatus(Status.ACTIVE);
    when(repository.findById("sb-003")).thenReturn(Optional.of(existing));

    SecurityValidationDtos.SandboxOutput output = service.sandbox("sb-003");

    assertThat(output.id()).isEqualTo("sb-003");
    assertThat(output.name()).isEqualTo("查询沙箱");
  }

  @Test
  void sandbox_throws_element_not_found_when_missing() {
    when(repository.findById("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.sandbox("nope")).isInstanceOf(ElementNotFoundException.class);
  }
}
