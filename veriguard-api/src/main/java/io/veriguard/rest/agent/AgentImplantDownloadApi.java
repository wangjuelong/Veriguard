package io.veriguard.rest.agent;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Veriguard Agent (C1) — implant binary download endpoint.
 *
 * <p>{@code GET /api/agent/implant/download/{os}/{arch}} streams the bundled veriguard-implant
 * binary for the requested platform. The endpoint also emits an {@code X-SHA256} response header
 * with the file's hex SHA-256 digest for client-side integrity verification.
 *
 * <p>Binaries are bundled under {@code classpath:agents/veriguard-implant/<os>/<arch>/} and added
 * to the Spring Boot fat jar at build time. In dev/test environments these are typically empty
 * placeholder files (the real release pipeline rebuilds the implant fork from CI and drops binaries
 * here during {@code mvn package}).
 *
 * <p>If the binary is missing (e.g. a fresh checkout that has not yet pulled implant releases), the
 * endpoint returns 404 with a clear message guiding the operator to the expected path.
 */
@RestController
public class AgentImplantDownloadApi {

  public static final String DOWNLOAD_URI = "/api/agent/implant/download/{os}/{arch}";

  /** Allowed OS values — must match the bundled directory names. */
  static final String OS_PATTERN = "linux|macos|windows";

  /** Allowed arch values — must match the bundled directory names. */
  static final String ARCH_PATTERN = "x86_64|arm64";

  private static final String CLASSPATH_BASE = "classpath:agents/veriguard-implant/";

  private final PathMatchingResourcePatternResolver resourceResolver =
      new PathMatchingResourcePatternResolver();

  @GetMapping(value = DOWNLOAD_URI)
  @Operation(summary = "Download veriguard-implant binary (agent self-service)")
  @RBAC(skipRBAC = true)
  public ResponseEntity<?> download(
      @PathVariable("os") @NotBlank @Pattern(regexp = OS_PATTERN) String os,
      @PathVariable("arch") @NotBlank @Pattern(regexp = ARCH_PATTERN) String arch) {
    String osLower = os.toLowerCase(Locale.ROOT);
    String archLower = arch.toLowerCase(Locale.ROOT);

    String filename = "windows".equals(osLower) ? "veriguard-implant.exe" : "veriguard-implant";
    String resourcePath = CLASSPATH_BASE + osLower + "/" + archLower + "/" + filename;
    Resource binary = resourceResolver.getResource(resourcePath);

    if (!binary.exists() || !binary.isReadable()) {
      return ResponseEntity.status(404)
          .body(
              "implant binary not yet bundled; expected at agents/veriguard-implant/"
                  + osLower
                  + "/"
                  + archLower
                  + "/"
                  + filename);
    }

    byte[] content;
    try (InputStream in = binary.getInputStream()) {
      content = in.readAllBytes();
    } catch (IOException ex) {
      return ResponseEntity.status(500).body("Failed to read implant binary: " + ex.getMessage());
    }

    if (content.length == 0) {
      return ResponseEntity.status(404)
          .body(
              "implant binary placeholder is empty; expected at agents/veriguard-implant/"
                  + osLower
                  + "/"
                  + archLower
                  + "/"
                  + filename);
    }

    String sha256Hex = sha256Hex(content);

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header("X-SHA256", sha256Hex)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.length))
        .body(content);
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
