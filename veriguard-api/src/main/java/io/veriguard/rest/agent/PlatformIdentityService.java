package io.veriguard.rest.agent;

import io.veriguard.crypto.Ed25519SignatureService;
import io.veriguard.crypto.X25519BoxService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Holds the platform's 4 long-lived crypto identities (P_sign + P_enc, both Ed25519 and X25519 key
 * pairs) — used by all Veriguard Agent (C1) crypto operations.
 *
 * <p>这是 C1-Platform-1 内的 scaffold 实现：keys 在 Spring 启动时 SecureRandom 生成。 <strong>生产场景</strong>必须替换为
 * HSM/KMS 持久化加载 (C1-Platform-2 后续 step)：
 *
 * <ul>
 *   <li>{@code P_sign_priv} 落 {@code /var/lib/veriguard/keys/p_sign.priv} chmod 0600
 *   <li>{@code P_enc_priv} 落 {@code /var/lib/veriguard/keys/p_enc.priv} chmod 0600
 *   <li>启动时从指定路径加载，配合 KMS unseal
 * </ul>
 *
 * <p>当前内存模式不持久化 — 单节点 deploy 重启即 key 旋转 (开发友好，prod 不可用)。
 *
 * <p>线程安全 — 所有字段在 {@link #init} 后均不可变.
 */
@Service
public class PlatformIdentityService {

  private final Ed25519SignatureService ed25519;
  private final X25519BoxService x25519;

  @Getter
  @Value("${veriguard.agent.platform-id:veriguard-prod-001}")
  private String platformId;

  @Getter
  @Value("${veriguard.agent.platform-url:http://localhost:8080}")
  private String platformUrl;

  @Getter private byte[] platformSignPriv;
  @Getter private byte[] platformSignPub;
  @Getter private byte[] platformEncPriv;
  @Getter private byte[] platformEncPub;

  public PlatformIdentityService(Ed25519SignatureService ed25519, X25519BoxService x25519) {
    this.ed25519 = ed25519;
    this.x25519 = x25519;
  }

  @PostConstruct
  void init() {
    Ed25519SignatureService.Ed25519KeyPair signPair = ed25519.generate();
    platformSignPriv = signPair.privateKey();
    platformSignPub = signPair.publicKey();
    X25519BoxService.X25519KeyPair encPair = x25519.generate();
    platformEncPriv = encPair.privateKey();
    platformEncPub = encPair.publicKey();
  }
}
