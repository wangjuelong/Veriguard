package io.veriguard.rest.security_validation;

/**
 * Input payload for {@link SandboxTaskService#submit(SandboxTaskSubmitInput)}. Decoupled from the
 * Spring multipart {@code MultipartFile} so the service stays test-friendly and reusable from
 * future non-HTTP callers (e.g. sample_inject Inject runner — PR B4).
 */
public record SandboxTaskSubmitInput(
    String sandboxId,
    String sampleType,
    String originalFilename,
    byte[] content,
    String targetMachine,
    Integer timeoutSeconds) {}
