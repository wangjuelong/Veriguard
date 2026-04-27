package io.veriguard.integration.sandbox.dto;

import java.time.Instant;

public record MachineSnapshot(
    String name, String label, String platform, String snapshot, String status, Instant fetchedAt) {}
