package io.veriguard.integration.sandbox.dto;

public record SampleSubmissionRequest(
    String sandboxPresetId,
    String sampleType,
    String originalFilename,
    String sampleSha256,
    byte[] content,
    String targetMachineName,
    Integer timeoutSeconds) {}
