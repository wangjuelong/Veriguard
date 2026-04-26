package io.veriguard.rest.veriguard;

import static io.veriguard.rest.veriguard.VeriguardSandboxMapper.toOutput;
import static io.veriguard.rest.veriguard.VeriguardSandboxMapper.updateEntity;

import io.veriguard.database.model.VeriguardSandbox;
import io.veriguard.database.repository.VeriguardSandboxRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.InputValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class VeriguardCapabilityService {

  private static final int TEMPLATES_PER_ATTACK_TYPE = 15;

  private static final List<String> TRAFFIC_ATTACK_TYPES =
      List.of(
          "暴力破解",
          "反弹 shell",
          "内存注入 webshell",
          "隐秘隧道",
          "恶意域名解析",
          "webshell 命令执行",
          "高危漏洞利用",
          "远控木马执行",
          "权限绕过",
          "未授权访问",
          "信息泄露");

  private static final List<String> HOST_ATTACK_TYPES =
      List.of(
          "反弹 shell",
          "webshell 上传落盘",
          "命令执行",
          "隧道代理",
          "内存注入 webshell",
          "暴力破解",
          "远控木马执行",
          "系统提权",
          "网站篡改",
          "病毒样本落盘",
          "痕迹清理",
          "主机持久化");

  private final VeriguardSandboxRepository sandboxRepository;

  /** Returns the PRD-oriented module matrix used by the Veriguard management console. */
  @Transactional(readOnly = true)
  public VeriguardDtos.CapabilityMatrixOutput capabilityMatrix() {
    List<VeriguardDtos.CapabilityModuleOutput> modules =
        List.of(
            new VeriguardDtos.CapabilityModuleOutput(
                "traffic-validation",
                "2.1 流量安全验证",
                "已落地控制平面",
                true,
                List.of("边界覆盖度指标", "设备稳定性趋势", "NTA / IDS 用例目录", "多四元组用例模板"),
                List.of("流量回放引擎", "NTA / IDS 事件采集适配器")),
            new VeriguardDtos.CapabilityModuleOutput(
                "host-validation",
                "2.2 应用与服务器安全验证",
                "已落地控制平面",
                true,
                List.of("HIDS 攻击分类", "Atomic testing / Payload 映射", "Agent / Executor 执行通道"),
                List.of("HIDS 告警采集适配器")),
            new VeriguardDtos.CapabilityModuleOutput(
                "custom-validation",
                "2.3 自定义验证",
                "已落地控制平面",
                true,
                List.of("6 类自定义用例", "批量导入入口", "ATT&CK 与纵深防御维度", "动态筛选场景模型"),
                List.of("PCAP 解析器", "Web 攻击包构造执行器")),
            new VeriguardDtos.CapabilityModuleOutput(
                "attack-orchestration",
                "2.4 攻击编排",
                "已落地控制平面",
                true,
                List.of("路径图节点策略", "延迟与重复执行参数", "拦截结果条件", "SOC 规则匹配条件", "链路级结果"),
                List.of("SOC 查询适配器")),
            new VeriguardDtos.CapabilityModuleOutput(
                "sandbox-management",
                "2.5 沙箱管理",
                "已落地并持久化",
                true,
                List.of("沙箱平台 CRUD", "网络访问控制策略", "恶意样本类型", "自动还原强制校验"),
                List.of("虚拟化 / 容器沙箱驱动")));
    VeriguardDtos.CapabilitySummaryOutput summary =
        new VeriguardDtos.CapabilitySummaryOutput(
            modules.size(),
            (int)
                modules.stream()
                    .filter(VeriguardDtos.CapabilityModuleOutput::acceptanceReady)
                    .count(),
            modules.stream().mapToInt(module -> module.externalIntegrationsRequired().size()).sum(),
            totalUseCaseTemplates());
    return new VeriguardDtos.CapabilityMatrixOutput(modules, summary);
  }

  /** Returns the generated attack-use-case catalog aligned with the PRD attack categories. */
  @Transactional(readOnly = true)
  public VeriguardDtos.AttackCatalogOutput attackCatalog() {
    List<VeriguardDtos.UseCaseTemplateOutput> templates = generatedTemplates();
    return new VeriguardDtos.AttackCatalogOutput(
        attackTypes("traffic", TRAFFIC_ATTACK_TYPES),
        attackTypes("host", HOST_ATTACK_TYPES),
        List.of(
            "构造 web 攻击包",
            "上传 pcap 流量包",
            "上传样本文件",
            "配置执行的命令",
            "上传可执行文件并配置执行命令",
            "配置邮件形式"),
        templates.size(),
        TRAFFIC_ATTACK_TYPES.size() >= 10 && HOST_ATTACK_TYPES.size() >= 10,
        true,
        templates);
  }

  /** Returns the orchestration policy schema used by the frontend path editor. */
  @Transactional(readOnly = true)
  public VeriguardDtos.OrchestrationSchemaOutput orchestrationSchema() {
    return new VeriguardDtos.OrchestrationSchemaOutput(
        List.of(
            "node_delay_enabled",
            "node_delay_seconds",
            "node_repeat_enabled",
            "node_repeat_count",
            "node_repeat_interval_seconds",
            "node_continue_condition",
            "node_validation_parameters"),
        List.of("CONTINUE_REGARDLESS", "STOP_WHEN_BLOCKED"),
        List.of("ALWAYS", "WHEN_PREVIOUS_BLOCKED", "WHEN_PREVIOUS_NOT_BLOCKED", "AND", "OR"),
        List.of(
            "soc_rule_id",
            "soc_rule_name",
            "soc_time_window_seconds",
            "soc_source",
            "soc_field_conditions",
            "soc_minimum_event_count"),
        List.of("FULL_CHAIN_EFFECTIVE", "FULL_CHAIN_FAILED", "PARTIAL_FAILED"));
  }

  /** Creates a sandbox platform after enforcing PRD-required automatic restoration. */
  public VeriguardDtos.SandboxOutput createSandbox(VeriguardSandboxInput input)
      throws InputValidationException {
    validateSandboxInput(input);
    VeriguardSandbox sandbox = new VeriguardSandbox();
    updateEntity(sandbox, input);
    return toOutput(sandboxRepository.save(sandbox));
  }

  /** Lists sandbox platforms configured for malicious sample execution. */
  @Transactional(readOnly = true)
  public List<VeriguardDtos.SandboxOutput> sandboxes() {
    return sandboxRepository.findAll().stream().map(VeriguardSandboxMapper::toOutput).toList();
  }

  /** Returns one sandbox platform by identifier. */
  @Transactional(readOnly = true)
  public VeriguardDtos.SandboxOutput sandbox(String sandboxId) {
    return toOutput(findSandbox(sandboxId));
  }

  /** Updates a sandbox platform and keeps PRD-required restoration enforcement enabled. */
  public VeriguardDtos.SandboxOutput updateSandbox(String sandboxId, VeriguardSandboxInput input)
      throws InputValidationException {
    validateSandboxInput(input);
    VeriguardSandbox sandbox = findSandbox(sandboxId);
    updateEntity(sandbox, input);
    return toOutput(sandboxRepository.save(sandbox));
  }

  /** Deletes a sandbox platform configuration. */
  public void deleteSandbox(String sandboxId) {
    VeriguardSandbox sandbox = findSandbox(sandboxId);
    sandboxRepository.delete(sandbox);
  }

  private VeriguardSandbox findSandbox(String sandboxId) {
    return sandboxRepository.findById(sandboxId).orElseThrow(ElementNotFoundException::new);
  }

  private void validateSandboxInput(VeriguardSandboxInput input) throws InputValidationException {
    Objects.requireNonNull(input, "Sandbox input must not be null");
    if (!input.autoRestoreEnabled()) {
      throw new InputValidationException(
          "sandbox_auto_restore_enabled", "Sandbox auto restore must be enabled.");
    }
    if (input.networkRules() == null || input.networkRules().isEmpty()) {
      throw new InputValidationException(
          "sandbox_network_rules", "At least one network control rule is required.");
    }
    if (input.supportedSampleTypes() == null || input.supportedSampleTypes().isEmpty()) {
      throw new InputValidationException(
          "sandbox_supported_sample_types", "At least one sample type is required.");
    }
  }

  private int totalUseCaseTemplates() {
    return (TRAFFIC_ATTACK_TYPES.size() + HOST_ATTACK_TYPES.size()) * TEMPLATES_PER_ATTACK_TYPE;
  }

  private List<VeriguardDtos.AttackTypeOutput> attackTypes(
      String surface, List<String> attackTypes) {
    return attackTypes.stream()
        .map(
            type ->
                new VeriguardDtos.AttackTypeOutput(
                    surface, type, TEMPLATES_PER_ATTACK_TYPE, true))
        .toList();
  }

  private List<VeriguardDtos.UseCaseTemplateOutput> generatedTemplates() {
    List<VeriguardDtos.UseCaseTemplateOutput> templates = new ArrayList<>();
    appendTemplates(templates, "traffic", TRAFFIC_ATTACK_TYPES, "TRAFFIC_REPLAY", "上传 pcap 流量包");
    appendTemplates(templates, "host", HOST_ATTACK_TYPES, "HOST_AGENT", "配置执行的命令");
    return templates;
  }

  private void appendTemplates(
      List<VeriguardDtos.UseCaseTemplateOutput> templates,
      String surface,
      List<String> attackTypes,
      String executorKind,
      String customCaseType) {
    int index = 1;
    for (String attackType : attackTypes) {
      for (int offset = 0; offset < TEMPLATES_PER_ATTACK_TYPE; offset++) {
        templates.add(
            new VeriguardDtos.UseCaseTemplateOutput(
                "VG-" + surface.toUpperCase() + "-" + String.format("%03d", index++),
                surface,
                attackType,
                executorKind,
                "traffic".equals(surface),
                customCaseType));
      }
    }
  }
}
