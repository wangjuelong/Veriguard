package io.veriguard.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link X25519BoxService}.
 *
 * <p>Crypto round-trip + cross-language compatibility validated via:
 *
 * <ul>
 *   <li>RFC 7748 §6.1 X25519 Alice/Bob test vector — ECDH shared secret 自验
 *   <li>seal/open round-trip 验证 sealed-box 整体可逆
 * </ul>
 */
class X25519BoxServiceTest {

  private X25519BoxService service;

  @BeforeEach
  void setUp() {
    service = new X25519BoxService();
  }

  @Test
  void sealOpenRoundtrip() {
    X25519BoxService.X25519KeyPair sender = service.generate();
    X25519BoxService.X25519KeyPair recipient = service.generate();
    byte[] plaintext = "secret tasks: install ransomware".getBytes();

    X25519BoxService.SealedBox box =
        service.seal(plaintext, recipient.publicKey(), sender.privateKey());
    byte[] decrypted =
        service.open(box.ciphertext(), box.nonce(), sender.publicKey(), recipient.privateKey());

    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void openWithWrongRecipientKeyThrows() {
    X25519BoxService.X25519KeyPair sender = service.generate();
    X25519BoxService.X25519KeyPair recipient = service.generate();
    X25519BoxService.X25519KeyPair other = service.generate();
    byte[] plaintext = "secret".getBytes();

    X25519BoxService.SealedBox box =
        service.seal(plaintext, recipient.publicKey(), sender.privateKey());

    assertThatThrownBy(
            () -> service.open(box.ciphertext(), box.nonce(), sender.publicKey(), other.privateKey()))
        .isInstanceOf(X25519BoxService.BoxOpenException.class);
  }

  @Test
  void openWithTamperedCiphertextThrows() {
    X25519BoxService.X25519KeyPair sender = service.generate();
    X25519BoxService.X25519KeyPair recipient = service.generate();
    byte[] plaintext = "secret".getBytes();

    X25519BoxService.SealedBox box =
        service.seal(plaintext, recipient.publicKey(), sender.privateKey());

    box.ciphertext()[0] ^= 0x01;

    assertThatThrownBy(
            () ->
                service.open(
                    box.ciphertext(), box.nonce(), sender.publicKey(), recipient.privateKey()))
        .isInstanceOf(X25519BoxService.BoxOpenException.class);
  }

  @Test
  void keyPairSizesAreCorrect() {
    X25519BoxService.X25519KeyPair pair = service.generate();

    assertThat(pair.privateKey()).hasSize(32);
    assertThat(pair.publicKey()).hasSize(32);
  }

  @Test
  void sealedBoxNonceLengthIsIetfChaCha20Poly1305() {
    X25519BoxService.X25519KeyPair sender = service.generate();
    X25519BoxService.X25519KeyPair recipient = service.generate();
    byte[] plaintext = "secret".getBytes();

    X25519BoxService.SealedBox box =
        service.seal(plaintext, recipient.publicKey(), sender.privateKey());

    // IETF ChaCha20-Poly1305 uses a 12-byte nonce (matches Rust chacha20poly1305 crate
    // ChaCha20Poly1305 IETF mode). Rust agent 端必须用 12-byte nonce 配对，否则 MAC 失败.
    assertThat(box.nonce()).hasSize(12);
  }

  /**
   * RFC 7748 §6.1 X25519 Alice + Bob test vector.
   *
   * <pre>
   *   Alice priv: 77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a
   *   Alice pub:  8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a
   *   Bob priv:   5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb
   *   Bob pub:    de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f
   *   Shared:     4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742
   * </pre>
   *
   * <p>该测试若失败则 Java 端 X25519 ECDH 算 shared secret 与 RFC 7748 不一致，
   * 跨语言 box 加解密链立刻崩溃 (双方派生不同对称 key).
   */
  @Test
  void x25519SharedSecretMatchesRfc7748TestVector() {
    byte[] alicePriv =
        Hex.decode("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
    byte[] alicePub =
        Hex.decode("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a");
    byte[] bobPriv =
        Hex.decode("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb");
    byte[] bobPub =
        Hex.decode("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f");
    byte[] expectedShared =
        Hex.decode("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742");

    byte[] aliceDerivedPub = service.derivePublicKey(alicePriv);
    byte[] bobDerivedPub = service.derivePublicKey(bobPriv);
    byte[] sharedAB = service.scalarMult(alicePriv, bobPub);
    byte[] sharedBA = service.scalarMult(bobPriv, alicePub);

    assertThat(aliceDerivedPub).isEqualTo(alicePub);
    assertThat(bobDerivedPub).isEqualTo(bobPub);
    assertThat(sharedAB).isEqualTo(expectedShared);
    assertThat(sharedBA).isEqualTo(expectedShared);
  }
}
