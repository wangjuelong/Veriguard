package io.veriguard.rest.security_validation;

import static io.veriguard.rest.security_validation.SandboxMapper.toOutput;
import static io.veriguard.rest.security_validation.SandboxMapper.updateEntity;

import io.veriguard.database.model.VeriguardSandbox;
import io.veriguard.database.repository.VeriguardSandboxRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.InputValidationException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class SandboxService {

  private final VeriguardSandboxRepository sandboxRepository;

  public SandboxService(VeriguardSandboxRepository sandboxRepository) {
    this.sandboxRepository = sandboxRepository;
  }

  public SecurityValidationDtos.SandboxOutput createSandbox(SandboxInput input)
      throws InputValidationException {
    validate(input);
    VeriguardSandbox sandbox = new VeriguardSandbox();
    updateEntity(sandbox, input);
    return persist(sandbox);
  }

  public SecurityValidationDtos.SandboxOutput updateSandbox(String sandboxId, SandboxInput input)
      throws InputValidationException {
    validate(input);
    VeriguardSandbox sandbox = findSandbox(sandboxId);
    updateEntity(sandbox, input);
    return persist(sandbox);
  }

  @Transactional(readOnly = true)
  public List<SecurityValidationDtos.SandboxOutput> sandboxes() {
    return sandboxRepository.findAll().stream().map(SandboxMapper::toOutput).toList();
  }

  @Transactional(readOnly = true)
  public SecurityValidationDtos.SandboxOutput sandbox(String sandboxId) {
    return toOutput(findSandbox(sandboxId));
  }

  public void deleteSandbox(String sandboxId) {
    sandboxRepository.delete(findSandbox(sandboxId));
  }

  private SecurityValidationDtos.SandboxOutput persist(VeriguardSandbox sandbox)
      throws InputValidationException {
    try {
      return toOutput(sandboxRepository.save(sandbox));
    } catch (DataIntegrityViolationException ex) {
      log.warn(
          "Sandbox persist failed with data integrity violation, treating as duplicate name", ex);
      throw new InputValidationException(
          "sandbox_name_duplicated", "Sandbox with the same name already exists.");
    }
  }

  private VeriguardSandbox findSandbox(String sandboxId) {
    return sandboxRepository.findById(sandboxId).orElseThrow(ElementNotFoundException::new);
  }

  private void validate(SandboxInput input) throws InputValidationException {
    Objects.requireNonNull(input, "Sandbox input must not be null");
    if (!input.autoRestoreEnabled()) {
      throw new InputValidationException(
          "sandbox_auto_restore_required", "Sandbox auto restore must be enabled.");
    }
    if (input.supportedSampleTypes().isEmpty()) {
      throw new InputValidationException(
          "sandbox_supported_sample_types_empty",
          "At least one supported sample type is required.");
    }
  }
}
