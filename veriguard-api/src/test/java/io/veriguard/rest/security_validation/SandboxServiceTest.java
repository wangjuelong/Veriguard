package io.veriguard.rest.security_validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.VeriguardSandbox.NetworkPolicy;
import io.veriguard.database.model.VeriguardSandbox.SampleType;
import io.veriguard.database.model.VeriguardSandbox.Status;
import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import io.veriguard.database.repository.VeriguardSandboxRepository;
import io.veriguard.rest.exception.InputValidationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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
    when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.createSandbox(baseInput))
        .isInstanceOf(InputValidationException.class)
        .hasFieldOrPropertyWithValue("field", "sandbox_name_duplicated");
  }

  @Test
  void create_accepts_multiple_network_rules() throws Exception {
    lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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
}
