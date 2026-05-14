package io.veriguard.crypto;

import java.security.SecureRandom;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.stereotype.Service;

/**
 * Ed25519 detached signature service for Veriguard Agent (C1) wire protocol.
 *
 * <p>用途 — 平台侧两类签名场景：
 *
 * <ul>
 *   <li>出包 ({@code .vpack}) 用 {@code P_sign_priv} 签 envelope.{metadata + ciphertext}
 *   <li>poll/result HTTP 请求 Agent 用 {@code A_sign_priv} 签 body+timestamp，平台用 {@code A_sign_pub} 验
 * </ul>
 *
 * <p>实现 — BouncyCastle lightweight API：
 *
 * <ul>
 *   <li>{@link Ed25519PrivateKeyParameters} (32-byte seed) / {@link Ed25519PublicKeyParameters}
 *   <li>{@link Ed25519Signer} (RFC 8032 pure Ed25519，无 hash 前置)
 * </ul>
 *
 * <p>Cross-language 兼容 — Rust ed25519-dalek 同样实现 RFC 8032 pure Ed25519，签名 vector 一致.
 *
 * <p>线程安全 — 每次 sign/verify 新建 {@link Ed25519Signer} 实例，可在并发场景安全调用.
 */
@Service
public class Ed25519SignatureService {

  /** Ed25519 secret seed + derived public key 长度均为 32 字节. */
  public static final int KEY_LENGTH_BYTES = 32;

  /** Ed25519 detached signature 固定 64 字节. */
  public static final int SIGNATURE_LENGTH_BYTES = 64;

  /** Random source for keypair generation — {@link SecureRandom#getInstanceStrong()} 优先. */
  private final SecureRandom secureRandom;

  public Ed25519SignatureService() {
    this.secureRandom = pickRandom();
  }

  /**
   * Generate a fresh Ed25519 keypair using a strong {@link SecureRandom}.
   *
   * @return 32-byte seed (private) + 32-byte public key
   */
  public Ed25519KeyPair generate() {
    Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(secureRandom);
    Ed25519PublicKeyParameters publicKey = privateKey.generatePublicKey();
    return new Ed25519KeyPair(privateKey.getEncoded(), publicKey.getEncoded());
  }

  /**
   * Derive the public key from a private seed.
   *
   * @param privateKey 32-byte Ed25519 seed
   * @return 32-byte public key
   * @throws IllegalArgumentException if {@code privateKey} is not 32 bytes
   */
  public byte[] derivePublicKey(byte[] privateKey) {
    requireLength(privateKey, KEY_LENGTH_BYTES, "privateKey");
    Ed25519PrivateKeyParameters params = new Ed25519PrivateKeyParameters(privateKey, 0);
    return params.generatePublicKey().getEncoded();
  }

  /**
   * Sign {@code message} with {@code privateKey} (pure Ed25519, no pre-hash).
   *
   * @param privateKey 32-byte Ed25519 seed
   * @param message arbitrary-length message bytes
   * @return 64-byte detached signature
   * @throws IllegalArgumentException if {@code privateKey} length wrong
   */
  public byte[] sign(byte[] privateKey, byte[] message) {
    requireLength(privateKey, KEY_LENGTH_BYTES, "privateKey");
    Ed25519PrivateKeyParameters params = new Ed25519PrivateKeyParameters(privateKey, 0);
    Ed25519Signer signer = new Ed25519Signer();
    signer.init(true, params);
    signer.update(message, 0, message.length);
    return signer.generateSignature();
  }

  /**
   * Verify a detached Ed25519 signature.
   *
   * @param publicKey 32-byte Ed25519 public key
   * @param message arbitrary-length message bytes
   * @param signature 64-byte detached signature
   * @return {@code true} iff signature is valid for the (publicKey, message) pair
   * @throws IllegalArgumentException if input lengths are wrong
   */
  public boolean verify(byte[] publicKey, byte[] message, byte[] signature) {
    requireLength(publicKey, KEY_LENGTH_BYTES, "publicKey");
    requireLength(signature, SIGNATURE_LENGTH_BYTES, "signature");
    Ed25519PublicKeyParameters params = new Ed25519PublicKeyParameters(publicKey, 0);
    Ed25519Signer verifier = new Ed25519Signer();
    verifier.init(false, params);
    verifier.update(message, 0, message.length);
    return verifier.verifySignature(signature);
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

  /**
   * Prefer {@link SecureRandom#getInstanceStrong()} (blocking, /dev/random on Linux). 平台已被卡在
   * /dev/random 的极端旧环境 fall back 到默认 {@link SecureRandom} (urandom)；不放任 RuntimeException 让
   * Spring context 起不来.
   */
  private static SecureRandom pickRandom() {
    try {
      return SecureRandom.getInstanceStrong();
    } catch (Exception ex) {
      return new SecureRandom();
    }
  }

  /** Pair of 32-byte Ed25519 (privateSeed, publicKey) byte arrays. */
  public record Ed25519KeyPair(byte[] privateKey, byte[] publicKey) {}
}
