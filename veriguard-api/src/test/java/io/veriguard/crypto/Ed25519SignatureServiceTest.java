package io.veriguard.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Ed25519SignatureService}.
 *
 * <p>Crypto round-trip + cross-language compatibility validated via RFC 8032 §7.1 Test 1 vector
 * (empty message). 该 vector 不依赖 Rust agent fixture，可在本 PR 单独 verify Java 端
 * Ed25519 实现与 RFC 一致。Rust 端 ed25519-dalek 通过同 vector 自验。
 */
class Ed25519SignatureServiceTest {

  private Ed25519SignatureService service;

  @BeforeEach
  void setUp() {
    service = new Ed25519SignatureService();
  }

  @Test
  void signVerifyRoundtrip() {
    Ed25519SignatureService.Ed25519KeyPair pair = service.generate();
    byte[] message = "test".getBytes();

    byte[] sig = service.sign(pair.privateKey(), message);

    assertThat(service.verify(pair.publicKey(), message, sig)).isTrue();
  }

  @Test
  void verifyTamperedMessageReturnsFalse() {
    Ed25519SignatureService.Ed25519KeyPair pair = service.generate();
    byte[] message = "test".getBytes();
    byte[] sig = service.sign(pair.privateKey(), message);

    byte[] tampered = "tast".getBytes();

    assertThat(service.verify(pair.publicKey(), tampered, sig)).isFalse();
  }

  @Test
  void verifyTamperedSignatureReturnsFalse() {
    Ed25519SignatureService.Ed25519KeyPair pair = service.generate();
    byte[] message = "test".getBytes();
    byte[] sig = service.sign(pair.privateKey(), message);

    sig[0] ^= 0x01;

    assertThat(service.verify(pair.publicKey(), message, sig)).isFalse();
  }

  @Test
  void verifyWrongPublicKeyReturnsFalse() {
    Ed25519SignatureService.Ed25519KeyPair pair1 = service.generate();
    Ed25519SignatureService.Ed25519KeyPair pair2 = service.generate();
    byte[] message = "test".getBytes();
    byte[] sig = service.sign(pair1.privateKey(), message);

    assertThat(service.verify(pair2.publicKey(), message, sig)).isFalse();
  }

  @Test
  void keyPairSizesAreCorrect() {
    Ed25519SignatureService.Ed25519KeyPair pair = service.generate();

    assertThat(pair.privateKey()).hasSize(32);
    assertThat(pair.publicKey()).hasSize(32);
  }

  /**
   * RFC 8032 §7.1 Ed25519 Test 1 — empty message.
   *
   * <p>Test vector：
   *
   * <pre>
   *   secret key:  9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60
   *   public key:  d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a
   *   message:     (empty)
   *   signature:   e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b
   * </pre>
   *
   * <p>该测试若失败则 Java 端 Ed25519 实现与 RFC 不兼容，会导致 Rust agent 验签失败.
   */
  @Test
  void signWithRfc8032Test1Vector() {
    byte[] privateKey =
        Hex.decode("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
    byte[] expectedPublicKey =
        Hex.decode("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
    byte[] message = new byte[0];
    byte[] expectedSignature =
        Hex.decode(
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");

    byte[] signature = service.sign(privateKey, message);
    byte[] publicKey = service.derivePublicKey(privateKey);

    assertThat(publicKey).isEqualTo(expectedPublicKey);
    assertThat(signature).isEqualTo(expectedSignature);
    assertThat(service.verify(publicKey, message, signature)).isTrue();
  }
}
