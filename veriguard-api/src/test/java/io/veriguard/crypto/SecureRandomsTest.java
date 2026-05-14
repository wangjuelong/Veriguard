package io.veriguard.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SecureRandoms}. */
class SecureRandomsTest {

  @Test
  void strongOrDefaultReturnsNonNull() {
    SecureRandom rng = SecureRandoms.strongOrDefault();
    assertThat(rng).isNotNull();
  }

  @Test
  void strongOrDefaultReturnsWorkingRng() {
    // Verify the returned RNG actually produces non-zero output (sanity check that we didn't
    // accidentally return some default-init stub that returns all zeros).
    SecureRandom rng = SecureRandoms.strongOrDefault();
    byte[] buf = new byte[32];
    rng.nextBytes(buf);

    boolean allZero = true;
    for (byte b : buf) {
      if (b != 0) {
        allZero = false;
        break;
      }
    }
    assertThat(allZero).as("32 random bytes should not all be zero").isFalse();
  }
}
