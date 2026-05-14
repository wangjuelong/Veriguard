package io.veriguard.crypto;

import java.security.SecureRandom;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.springframework.stereotype.Service;

/**
 * Authenticated public-key encryption ("box") for Veriguard Agent (C1) wire protocol.
 *
 * <p>构造（NaCl-box 风格但用 IETF ChaCha20-Poly1305 不是 XSalsa20-Poly1305）：
 *
 * <ol>
 *   <li>ECDH(sender_priv, recipient_pub) → 32-byte shared secret K_shared
 *   <li>K_shared 直接作 ChaCha20-Poly1305 对称 key (无 HKDF — 双方共识 32B 入参 = 算法 key)
 *   <li>12-byte random nonce ({@link SecureRandom}) 与 AEAD ciphertext 一同存
 *   <li>无 AAD（associated data）— `.vpack` envelope 把 metadata 走 Ed25519 签名，与加密层正交
 * </ol>
 *
 * <p>Cross-language 兼容 — Rust agent 用 {@code x25519-dalek} + {@code chacha20poly1305} crate (IETF
 * 12-byte nonce 模式) 一一对应：
 *
 * <ul>
 *   <li>{@code x25519_dalek::x25519(priv, pub)} → 同等 ECDH
 *   <li>{@code chacha20poly1305::ChaCha20Poly1305::new_from_slice(K)} 一致初始化
 *   <li>nonce 12 字节匹配
 * </ul>
 *
 * <p>注意：BC 1.84 不带 XChaCha20-Poly1305 引擎；用 IETF ChaCha20-Poly1305 (12B nonce) 是 务实的双端对齐方案。spec
 * §3.5.2 中 {@code "cipher": "chacha20-poly1305"} 字段未指定 nonce 长度， 此处实现把 12B 锁为契约。
 */
@Service
public class X25519BoxService {

  /** X25519 私钥/公钥/共享密钥均 32 字节 (Curve25519 scalar / U coordinate). */
  public static final int KEY_LENGTH_BYTES = 32;

  /** IETF ChaCha20-Poly1305 nonce 长度 (96 bit). */
  public static final int NONCE_LENGTH_BYTES = 12;

  /** Poly1305 MAC tag 长度 (128 bit). */
  public static final int TAG_LENGTH_BITS = 128;

  private final SecureRandom secureRandom;

  public X25519BoxService() {
    this.secureRandom = SecureRandoms.strongOrDefault();
  }

  /** Generate a fresh X25519 keypair. */
  public X25519KeyPair generate() {
    X25519PrivateKeyParameters privateKey = new X25519PrivateKeyParameters(secureRandom);
    X25519PublicKeyParameters publicKey = privateKey.generatePublicKey();
    return new X25519KeyPair(privateKey.getEncoded(), publicKey.getEncoded());
  }

  /** Derive the X25519 public key from a 32-byte private scalar. */
  public byte[] derivePublicKey(byte[] privateKey) {
    requireLength(privateKey, KEY_LENGTH_BYTES, "privateKey");
    X25519PrivateKeyParameters params = new X25519PrivateKeyParameters(privateKey, 0);
    return params.generatePublicKey().getEncoded();
  }

  /**
   * X25519 scalar multiplication (ECDH).
   *
   * @param privateKey 32-byte private scalar
   * @param peerPublicKey 32-byte peer public U-coordinate
   * @return 32-byte shared secret
   */
  public byte[] scalarMult(byte[] privateKey, byte[] peerPublicKey) {
    requireLength(privateKey, KEY_LENGTH_BYTES, "privateKey");
    requireLength(peerPublicKey, KEY_LENGTH_BYTES, "peerPublicKey");
    X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(privateKey, 0);
    X25519PublicKeyParameters pub = new X25519PublicKeyParameters(peerPublicKey, 0);
    X25519Agreement agreement = new X25519Agreement();
    agreement.init(priv);
    byte[] shared = new byte[agreement.getAgreementSize()];
    agreement.calculateAgreement(pub, shared, 0);
    return shared;
  }

  /**
   * Seal {@code plaintext} for {@code recipientPublicKey}, signed (via shared secret) for {@code
   * senderPrivateKey}.
   *
   * @return ({@code ciphertext} including Poly1305 tag, 12-byte {@code nonce})
   */
  public SealedBox seal(byte[] plaintext, byte[] recipientPublicKey, byte[] senderPrivateKey) {
    if (plaintext == null) {
      throw new IllegalArgumentException("plaintext must not be null");
    }
    byte[] sharedSecret = scalarMult(senderPrivateKey, recipientPublicKey);
    byte[] nonce = new byte[NONCE_LENGTH_BYTES];
    secureRandom.nextBytes(nonce);
    byte[] ciphertext = chachaPoly1305(sharedSecret, nonce, plaintext, true);
    return new SealedBox(ciphertext, nonce);
  }

  /**
   * Open a sealed box.
   *
   * @throws BoxOpenException if Poly1305 verification fails (tampered ciphertext / wrong key)
   */
  public byte[] open(
      byte[] ciphertext, byte[] nonce, byte[] senderPublicKey, byte[] recipientPrivateKey) {
    requireLength(nonce, NONCE_LENGTH_BYTES, "nonce");
    if (ciphertext == null) {
      throw new IllegalArgumentException("ciphertext must not be null");
    }
    byte[] sharedSecret = scalarMult(recipientPrivateKey, senderPublicKey);
    try {
      return chachaPoly1305(sharedSecret, nonce, ciphertext, false);
    } catch (BoxOpenException ex) {
      throw ex;
    }
  }

  /**
   * One-shot ChaCha20-Poly1305 (IETF, 12B nonce, no AAD).
   *
   * @param key 32-byte symmetric key
   * @param nonce 12-byte unique nonce per (key, message)
   * @param input plaintext (if encrypting) or ciphertext+tag (if decrypting)
   * @param encrypt true for seal, false for open
   * @return ciphertext+tag (encrypt) or plaintext (decrypt)
   * @throws BoxOpenException on decrypt MAC failure
   */
  private static byte[] chachaPoly1305(byte[] key, byte[] nonce, byte[] input, boolean encrypt) {
    ChaCha20Poly1305 engine = new ChaCha20Poly1305();
    engine.init(encrypt, new AEADParameters(new KeyParameter(key), TAG_LENGTH_BITS, nonce));
    byte[] out = new byte[engine.getOutputSize(input.length)];
    int written = engine.processBytes(input, 0, input.length, out, 0);
    try {
      written += engine.doFinal(out, written);
    } catch (InvalidCipherTextException ex) {
      throw new BoxOpenException("ChaCha20-Poly1305 authentication failed", ex);
    }
    if (written == out.length) {
      return out;
    }
    byte[] trimmed = new byte[written];
    System.arraycopy(out, 0, trimmed, 0, written);
    return trimmed;
  }

  private static void requireLength(byte[] buf, int expected, String name) {
    if (buf == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    if (buf.length != expected) {
      throw new IllegalArgumentException(
          name + " must be " + expected + " bytes, was " + buf.length);
    }
  }

  /** Pair of 32-byte X25519 (privateKey, publicKey) byte arrays. */
  public record X25519KeyPair(byte[] privateKey, byte[] publicKey) {}

  /**
   * Output of {@link #seal}: ciphertext (incl. Poly1305 tag suffix) + 12-byte nonce.
   *
   * <p>Mutable arrays — callers should treat them as immutable except for explicit tamper tests.
   */
  public record SealedBox(byte[] ciphertext, byte[] nonce) {}

  /**
   * Thrown when {@link #open} fails authentication (tampered ciphertext, wrong key, wrong nonce).
   * Carries a meaningful message but never the failed inputs (defence in depth).
   */
  public static class BoxOpenException extends RuntimeException {
    public BoxOpenException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
