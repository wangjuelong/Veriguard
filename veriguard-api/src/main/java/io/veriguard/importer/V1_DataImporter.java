package io.veriguard.importer;

import static io.veriguard.database.specification.NodeContractSpecification.byPayloadExternalId;
import static io.veriguard.database.specification.NodeContractSpecification.byPayloadId;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static io.veriguard.rest.attack_chain.export.AttackChainFileExport.SCENARIO_VARIABLES;
import static io.veriguard.rest.attack_chain_run.exports.AttackChainRunFileExport.EXERCISE_VARIABLES;
import static io.veriguard.rest.payload.PayloadUtils.buildPayload;
import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import io.veriguard.database.model.*;
import io.veriguard.database.model.AttackChain.SEVERITY;
import io.veriguard.database.repository.*;
import io.veriguard.rest.attack_chain_node.form.AttackChainEdgeInput;
import io.veriguard.rest.attack_chain_run.exports.VariableWithValueMixin;
import io.veriguard.rest.domain.DomainService;
import io.veriguard.rest.domain.enums.PresetDomain;
import io.veriguard.rest.injector_contract.NodeContractContentUtils;
import io.veriguard.rest.payload.contract_output_element.ContractOutputElementInput;
import io.veriguard.rest.payload.form.*;
import io.veriguard.rest.payload.output_parser.OutputParserInput;
import io.veriguard.rest.payload.regex_group.RegexGroupInput;
import io.veriguard.rest.payload.service.PayloadCreationService;
import io.veriguard.service.FileService;
import io.veriguard.service.ImportEntry;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.scenario.AttackChainService;
import jakarta.activation.MimetypesFileTypeMap;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class V1_DataImporter implements Importer {

  // region variables
  @Resource protected ObjectMapper mapper;
  private final FileService documentService;
  private final DocumentRepository documentRepository;
  private final TagRepository tagRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final KillChainPhaseRepository killChainPhaseRepository;
  private final AttackChainRunRepository attackChainRunRepository;
  private final AttackChainService attackChainService;
  private final TeamRepository teamRepository;
  private final ObjectiveRepository objectiveRepository;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final NodeContractRepository nodeContractRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final AttackChainNodeDocumentRepository attackChainNodeDocumentRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final VariableRepository variableRepository;
  private final AttackChainEdgesRepository attackChainEdgesRepository;
  private final PayloadCreationService payloadCreationService;
  private final CollectorRepository collectorRepository;
  private final DomainService domainService;

  private final NodeContractContentUtils nodeContractContentUtils;

  @Qualifier("coreInjectorService")
  private final NodeExecutorService nodeExecutorService;

  // endregion

  private String handleAttackChainNodeContent(
      Map<String, Base> baseIds, String contract, JsonNode attackChainNodeNode) {
    if (contract == null) {
      return null;
    }
    String content = attackChainNodeNode.get("inject_content").toString();
    // 二开 移除 Channel/Challenge 注入器 — 不再需要 contract-specific 内容重写。
    return content;
  }

  private Set<Tag> computeTagsCompletion(
      Set<Tag> existingTags, List<String> lookingIds, Map<String, Base> baseIds) {
    Set<Tag> tags = new HashSet<>(existingTags);
    Set<Tag> tagsForOrganization =
        lookingIds.stream().map(baseIds::get).map(Tag.class::cast).collect(Collectors.toSet());
    tags.addAll(tagsForOrganization);
    return tags;
  }

  @Override
  @Transactional
  public void importData(
      JsonNode importNode,
      Map<String, ImportEntry> docReferences,
      AttackChainRun attackChainRun,
      AttackChain attackChain,
      Asset asset,
      AssetGroup assetGroup,
      String suffix) {
    Map<String, Base> baseIds = new HashMap<>();

    String prefix = "inject_";
    if (importNode.has("exercise_information")) {
      prefix = "exercise_";
    } else if (importNode.has("scenario_information")) {
      prefix = "scenario_";
    } else if (importNode.has("payload_information")) {
      prefix = "payload_";
    }
    importTags(importNode, prefix, baseIds);
    AttackChainRun savedAttackChainRun =
        Optional.ofNullable(importAttackChainRun(importNode, baseIds, suffix))
            .orElse(attackChainRun);
    AttackChain savedAttackChain =
        Optional.ofNullable(importAttackChain(importNode, baseIds, suffix)).orElse(attackChain);
    importDocuments(
        importNode, prefix, docReferences, savedAttackChainRun, savedAttackChain, baseIds);
    importDocument(
        importNode, prefix, docReferences, savedAttackChainRun, savedAttackChain, baseIds);

    // Should be done after tags & documents
    if (prefix.equals("payload_")) {
      importPayloadAsMain(importNode, baseIds);
    }

    importOrganizations(importNode, prefix, baseIds);
    importUsers(importNode, prefix, baseIds);
    importTeams(importNode, prefix, savedAttackChainRun, savedAttackChain, baseIds);
    importObjectives(importNode, prefix, savedAttackChainRun, savedAttackChain, baseIds);
    importLessons(importNode, prefix, savedAttackChainRun, savedAttackChain, baseIds);
    importAttackChainNodes(
        importNode, prefix, savedAttackChainRun, savedAttackChain, asset, assetGroup, baseIds);
    importVariables(importNode, savedAttackChainRun, savedAttackChain, baseIds);
  }

  // -- TAGS --

  private void importTags(JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "tags")
        .forEach(
            nodeTag -> {
              String id = nodeTag.get("tag_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String name = nodeTag.get("tag_name").textValue();

              List<Tag> existingTags = this.tagRepository.findByNameIgnoreCase(name);
              if (!existingTags.isEmpty()) {
                baseIds.put(id, existingTags.getFirst());
              } else {
                baseIds.put(id, this.tagRepository.save(createTag(nodeTag)));
              }
            });
  }

  private Tag createTag(JsonNode jsonNode) {
    Tag tag = new Tag();
    tag.setName(jsonNode.get("tag_name").textValue());
    tag.setColor(jsonNode.get("tag_color").textValue());
    return tag;
  }

  // -- DOMAINS --
  @VisibleForTesting
  protected List<String> importDomains(
      JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    List<String> domainIds = new ArrayList<>();
    resolveJsonElements(importNode, prefix + "domains")
        .forEach(
            nodeDomain -> {
              JsonNode idNode = nodeDomain.get("domain_id");
              if (idNode == null) {
                return;
              }
              String id = idNode.textValue();

              if (baseIds.get(id) != null) {
                // Already import
                domainIds.add(baseIds.get(id).getId());
                return;
              }

              Optional<Domain> existingDomain = this.domainService.findOptionalById(id);
              if (existingDomain.isPresent()) {
                baseIds.put(id, existingDomain.get());
                domainIds.add(existingDomain.get().getId());
              } else {
                Domain createdDomain =
                    this.domainService.upsert(
                        nodeDomain.get("domain_name").textValue(),
                        nodeDomain.get("domain_color").textValue());
                baseIds.put(createdDomain.getId(), createdDomain);
                domainIds.add(createdDomain.getId());
              }
            });

    // if no domain found we marked it as "TOCLASSIFY"
    if (domainIds.isEmpty()) {
      domainIds.add(
          domainService
              .findOptionalByName(PresetDomain.TOCLASSIFY.getName())
              .orElseThrow()
              .getId());
    }

    return domainIds;
  }

  // -- ATTACK PATTERN --
  private List<String> importAttackPattern(
      JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    ArrayList<String> attackPatternIds = new ArrayList<>();
    resolveJsonElements(importNode, prefix + "attack_patterns")
        .forEach(
            nodeAttackPattern -> {
              JsonNode idNode = nodeAttackPattern.get("attack_pattern_id");
              if (idNode == null) {
                return;
              }
              String id = idNode.textValue();

              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String name = nodeAttackPattern.get("attack_pattern_external_id").textValue();

              List<AttackPattern> existingAttackPattern =
                  this.attackPatternRepository.findAllByExternalIdInIgnoreCase(List.of(name));
              if (!existingAttackPattern.isEmpty()) {
                baseIds.put(id, existingAttackPattern.getFirst());
                attackPatternIds.add(existingAttackPattern.getFirst().getId());
              } else {
                AttackPattern attackPatternCreated =
                    this.attackPatternRepository.save(
                        createAttackPattern(
                            nodeAttackPattern,
                            importKillChainPhase(nodeAttackPattern, "attack_pattern_", baseIds)));
                baseIds.put(id, attackPatternCreated);
                attackPatternIds.add(attackPatternCreated.getId());
              }
            });
    return attackPatternIds;
  }

  private AttackPattern createAttackPattern(
      JsonNode jsonNode, List<KillChainPhase> killChainPhases) {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setStixId("attack-pattern--" + UUID.randomUUID());
    attackPattern.setName(jsonNode.get("attack_pattern_name").textValue());
    attackPattern.setDescription(jsonNode.get("attack_pattern_description").textValue());
    attackPattern.setExternalId(jsonNode.get("attack_pattern_external_id").textValue());
    attackPattern.setKillChainPhases(killChainPhases);
    return attackPattern;
  }

  private List<KillChainPhase> importKillChainPhase(
      JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    List<KillChainPhase> killChainPhases = new ArrayList<>();
    resolveJsonElements(importNode, prefix + "kill_chain_phases")
        .forEach(
            nodeKillChainPhase -> {
              JsonNode idNode = nodeKillChainPhase.get("phase_external_id");
              if (idNode == null) {
                return;
              }
              String id = idNode.textValue();

              if (baseIds.get(id) != null) {
                // Already imported
                return;
              }
              String name = nodeKillChainPhase.get("phase_external_id").textValue();

              List<KillChainPhase> existingKillChainPhases =
                  this.killChainPhaseRepository.findAllByExternalIdInIgnoreCase(List.of(name));
              if (!existingKillChainPhases.isEmpty()) {
                baseIds.put(id, existingKillChainPhases.getFirst());
                killChainPhases.add(existingKillChainPhases.getFirst());
              } else {
                KillChainPhase killChainPhaseCreated =
                    this.killChainPhaseRepository.save(createKillChainPhase(nodeKillChainPhase));
                baseIds.put(id, killChainPhaseCreated);
                killChainPhases.add(killChainPhaseCreated);
              }
            });
    return killChainPhases;
  }

  private KillChainPhase createKillChainPhase(JsonNode killChainPhaseNode) {
    KillChainPhase killChainPhase = new KillChainPhase();
    killChainPhase.setKillChainName(killChainPhaseNode.get("phase_kill_chain_name").textValue());
    killChainPhase.setShortName(killChainPhaseNode.get("phase_shortname").textValue());
    killChainPhase.setDescription(killChainPhaseNode.get("phase_description").textValue());
    killChainPhase.setName(killChainPhaseNode.get("phase_name").textValue());
    killChainPhase.setStixId(killChainPhaseNode.get("phase_stix_id").textValue());
    killChainPhase.setExternalId(killChainPhaseNode.get("phase_external_id").textValue());
    killChainPhase.setOrder(killChainPhaseNode.get("phase_order").asLong());
    return killChainPhase;
  }

  // -- EXERCISE --

  private AttackChainRun importAttackChainRun(
      JsonNode importNode, Map<String, Base> baseIds, String suffix) {
    JsonNode attackChainRunNode = importNode.get("exercise_information");
    if (attackChainRunNode == null) {
      return null;
    }

    AttackChainRun attackChainRun = new AttackChainRun();
    attackChainRun.setName(attackChainRunNode.get("exercise_name").textValue() + suffix);
    attackChainRun.setDescription(attackChainRunNode.get("exercise_description").textValue());
    attackChainRun.setSubtitle(attackChainRunNode.get("exercise_subtitle").textValue());
    attackChainRun.setHeader(attackChainRunNode.get("exercise_message_header").textValue());
    attackChainRun.setFooter(attackChainRunNode.get("exercise_message_footer").textValue());
    attackChainRun.setFrom(attackChainRunNode.get("exercise_mail_from").textValue());
    attackChainRun.setTags(
        resolveJsonIds(attackChainRunNode, "exercise_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    return attackChainRunRepository.save(attackChainRun);
  }

  // -- SCENARIO --

  private AttackChain importAttackChain(
      JsonNode importNode, Map<String, Base> baseIds, String suffix) {
    JsonNode attackChainNode = importNode.get("scenario_information");
    if (attackChainNode == null) {
      return null;
    }

    AttackChain attackChain = new AttackChain();
    attackChain.setName(attackChainNode.get("scenario_name").textValue() + suffix);
    attackChain.setDescription(attackChainNode.get("scenario_description").textValue());
    attackChain.setSubtitle(attackChainNode.get("scenario_subtitle").textValue());
    attackChain.setCategory(attackChainNode.get("scenario_category").textValue());
    attackChain.setMainFocus(attackChainNode.get("scenario_main_focus").textValue());
    ofNullable(attackChainNode.get("scenario_severity"))
        .map(JsonNode::textValue)
        .ifPresent(severity -> attackChain.setSeverity(SEVERITY.valueOf(severity)));
    ofNullable(attackChainNode.get("scenario_recurrence"))
        .map(JsonNode::textValue)
        .ifPresent(attackChain::setRecurrence);
    ofNullable(attackChainNode.get("scenario_recurrence_start"))
        .map(JsonNode::textValue)
        .ifPresent(
            recurrenceStart -> attackChain.setRecurrenceStart(Instant.parse(recurrenceStart)));
    ofNullable(attackChainNode.get("scenario_recurrence_end"))
        .map(JsonNode::textValue)
        .ifPresent(recurrenceEnd -> attackChain.setRecurrenceEnd(Instant.parse(recurrenceEnd)));
    attackChain.setHeader(attackChainNode.get("scenario_message_header").textValue());
    attackChain.setFooter(attackChainNode.get("scenario_message_footer").textValue());
    attackChain.setFrom(attackChainNode.get("scenario_mail_from").textValue());
    attackChain.setTags(
        resolveJsonIds(attackChainNode, "scenario_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    attackChain.setDependencies(
        ofNullable(attackChainNode.get("scenario_dependencies"))
            .filter(JsonNode::isArray)
            .map(
                dependencies ->
                    StreamSupport.stream(dependencies.spliterator(), false)
                        .map(node -> AttackChain.Dependency.valueOf(node.textValue()))
                        .toArray(AttackChain.Dependency[]::new))
            .orElse(new AttackChain.Dependency[0]));

    return attackChainService.createAttackChain(attackChain);
  }

  private void importDocuments(
      JsonNode importNode,
      String prefix,
      Map<String, ImportEntry> docReferences,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Map<String, Base> baseIds) {
    Stream<JsonNode> documentsStream = resolveJsonElements(importNode, prefix + "documents");
    documentsStream.forEach(
        nodeDoc -> {
          String target = nodeDoc.get("document_target").textValue();
          ImportEntry entry = docReferences.get(target);

          if (entry != null) {
            handleDocumentWithEntry(
                nodeDoc, entry, target, savedAttackChainRun, savedAttackChain, baseIds);
          }
        });
    // Handle argument documents
    Stream<JsonNode> argumentDcumentsStream =
        resolveJsonElements(importNode, prefix + "arguments_documents");
    argumentDcumentsStream.forEach(
        nodeDoc -> {
          String target = nodeDoc.get("document_target").textValue();
          ImportEntry entry = docReferences.get(target);

          if (entry != null) {
            handleDocumentWithEntry(
                nodeDoc, entry, target, savedAttackChainRun, savedAttackChain, baseIds);
          }
        });
  }

  private void importDocument(
      JsonNode importNode,
      String prefix,
      Map<String, ImportEntry> docReferences,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Map<String, Base> baseIds) {

    if (importNode == null) {
      return;
    }

    JsonNode nodeDoc = importNode.path(prefix + "document");
    String target = nodeDoc.path("document_target").textValue();

    if (target != null) {
      ImportEntry entry = docReferences.get(target);
      if (entry != null) {
        handleDocumentWithEntry(
            nodeDoc, entry, target, savedAttackChainRun, savedAttackChain, baseIds);
      }
    }
  }

  private void handleDocumentWithEntry(
      JsonNode nodeDoc,
      ImportEntry entry,
      String target,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Map<String, Base> baseIds) {
    String contentType = new MimetypesFileTypeMap().getContentType(entry.getEntry().getName());
    Optional<Document> targetDocument = this.documentRepository.findByTarget(target);

    if (targetDocument.isPresent()) {
      updateExistingDocument(
          nodeDoc, targetDocument.get(), savedAttackChainRun, savedAttackChain, baseIds);
    } else {
      uploadNewDocument(
          nodeDoc, entry, target, savedAttackChainRun, savedAttackChain, contentType, baseIds);
    }
  }

  private void updateExistingDocument(
      JsonNode nodeDoc,
      Document document,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Map<String, Base> baseIds) {
    if (savedAttackChainRun != null) {
      Set<AttackChainRun> attackChainRuns = new HashSet<>(document.getAttackChainRuns());
      attackChainRuns.add(savedAttackChainRun);
      document.setAttackChainRuns(attackChainRuns);
    } else if (savedAttackChain != null) {
      Set<AttackChain> attackChains = new HashSet<>(document.getAttackChains());
      attackChains.add(savedAttackChain);
      document.setAttackChains(attackChains);
    }
    document.setTags(
        computeTagsCompletion(
            document.getTags(), resolveJsonIds(nodeDoc, "document_tags"), baseIds));
    Document savedDocument = this.documentRepository.save(document);
    baseIds.put(nodeDoc.get("document_id").textValue(), savedDocument);
  }

  private void uploadNewDocument(
      JsonNode nodeDoc,
      ImportEntry entry,
      String target,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      String contentType,
      Map<String, Base> baseIds) {
    try {
      this.documentService.uploadFile(
          target, entry.getData(), entry.getContentLength(), contentType);
    } catch (Exception e) {
      throw new ImportException(e);
    }

    Document document = new Document();
    document.setTarget(target);
    document.setName(nodeDoc.get("document_name").textValue());
    document.setDescription(nodeDoc.get("document_description").textValue());
    if (savedAttackChainRun != null) {
      document.setAttackChainRuns(new HashSet<>(Set.of(savedAttackChainRun)));
    } else if (savedAttackChain != null) {
      document.setAttackChains(new HashSet<>(Set.of(savedAttackChain)));
    }
    // need to get real database-bound ids for tags
    List<String> tagIds =
        resolveJsonIds(nodeDoc, "document_tags").stream()
            .filter(baseIds::containsKey)
            .map(tid -> baseIds.get(tid).getId())
            .toList();
    document.setTags(iterableToSet(tagRepository.findAllById(tagIds)));
    document.setType(contentType);
    Document savedDocument = this.documentRepository.save(document);
    baseIds.put(nodeDoc.get("document_id").textValue(), savedDocument);
  }

  // -- ORGANIZATION --

  private void importOrganizations(JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "organizations")
        .forEach(
            nodeOrganization -> {
              String id = nodeOrganization.get("organization_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String name = nodeOrganization.get("organization_name").textValue();

              List<Organization> existingOrganizations =
                  this.organizationRepository.findByNameIgnoreCase(name);

              if (!existingOrganizations.isEmpty()) {
                baseIds.put(id, existingOrganizations.getFirst());
              } else {
                baseIds.put(
                    id,
                    this.organizationRepository.save(
                        createOrganization(nodeOrganization, baseIds)));
              }
            });
  }

  private Organization createOrganization(JsonNode importNode, Map<String, Base> baseIds) {
    Organization organization = new Organization();
    organization.setName(importNode.get("organization_name").textValue());
    organization.setDescription(getNodeValue(importNode.get("organization_description")));
    organization.setTags(
        resolveJsonIds(importNode, "organization_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    return organization;
  }

  // -- USERS --

  private void importUsers(JsonNode importNode, String prefix, Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "users")
        .forEach(
            nodeUser -> {
              String id = nodeUser.get("user_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String email = nodeUser.get("user_email").textValue();

              User existingUser = this.userRepository.findByEmailIgnoreCase(email).orElse(null);

              baseIds.put(
                  id,
                  Objects.requireNonNullElseGet(
                      existingUser, () -> this.userRepository.save(createUser(nodeUser, baseIds))));
            });
  }

  private User createUser(JsonNode jsonNode, Map<String, Base> baseIds) {
    User user = new User();
    user.setEmail(jsonNode.get("user_email").textValue());
    user.setFirstname(jsonNode.get("user_firstname").textValue());
    user.setLastname(jsonNode.get("user_lastname").textValue());
    user.setLang(getNodeValue(jsonNode.get("user_lang")));
    user.setPhone(getNodeValue(jsonNode.get("user_phone")));
    user.setPgpKey(getNodeValue(jsonNode.get("user_pgp_key")));
    user.setCountry(getNodeValue(jsonNode.get("user_country")));
    user.setCity(getNodeValue(jsonNode.get("user_city")));
    Base userOrganization = baseIds.get(jsonNode.get("user_organization").textValue());
    if (userOrganization != null) {
      user.setOrganization((Organization) userOrganization);
    }
    user.setTags(
        resolveJsonIds(jsonNode, "user_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    return user;
  }

  // -- TEAMS --

  private void importTeams(
      JsonNode importNode,
      String prefix,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Map<String, Base> baseIds) {
    Map<String, Team> baseTeams =
        handlingTeams(importNode, prefix, baseIds, savedAttackChainRun, savedAttackChain);
    baseTeams
        .values()
        .forEach(
            (team) -> {
              if (savedAttackChainRun != null) {
                Set<AttackChainRun> attackChainRuns = new HashSet<>(team.getAttackChainRuns());
                attackChainRuns.add(savedAttackChainRun);
                team.setAttackChainRuns(attackChainRuns.stream().toList());
              } else if (savedAttackChain != null) {
                Set<AttackChain> attackChains = new HashSet<>(team.getAttackChains());
                attackChains.add(savedAttackChain);
                team.setAttackChains(attackChains.stream().toList());
              }
            });
    baseIds.putAll(baseTeams);
  }

  private Map<String, Team> handlingTeams(
      JsonNode importNode,
      String prefix,
      Map<String, Base> baseIds,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain) {
    Map<String, Team> baseTeams = new HashMap<>();

    resolveJsonElements(importNode, prefix + "teams")
        .forEach(
            nodeTeam -> {
              String id = nodeTeam.get("team_id").textValue();
              if (baseIds.get(id) != null) {
                // Already import
                return;
              }
              String name = nodeTeam.get("team_name").textValue();

              // Prevent duplication of team, based on the team name and not contextual
              List<Team> existingTeams =
                  this.teamRepository.findByNameIgnoreCaseAndNotContextual(name);

              if (!existingTeams.isEmpty()) {
                baseTeams.put(id, existingTeams.getFirst());
              } else {
                // skip creating contextual team if atomic testing
                if (nodeTeam.has("team_contextual")) {
                  boolean isContextual = nodeTeam.get("team_contextual").booleanValue();
                  if (isContextual && savedAttackChainRun == null && savedAttackChain == null) {
                    return;
                  }
                }

                Team team = createTeam(nodeTeam, baseIds);
                // Tags
                List<String> teamTagIds = resolveJsonIds(nodeTeam, "team_tags");
                Set<Tag> tagsForTeam =
                    teamTagIds.stream()
                        .map(baseIds::get)
                        .filter(Objects::nonNull)
                        .map(Tag.class::cast)
                        .collect(Collectors.toSet());
                team.setTags(tagsForTeam);
                // Users
                List<String> teamUserIds = resolveJsonIds(nodeTeam, "team_users");
                List<User> usersForTeam =
                    teamUserIds.stream()
                        .map(baseIds::get)
                        .filter(Objects::nonNull)
                        .map(User.class::cast)
                        .toList();
                team.setUsers(usersForTeam);
                Team savedTeam = this.teamRepository.save(team);
                baseTeams.put(id, savedTeam);
              }
            });
    return baseTeams;
  }

  private Team createTeam(JsonNode jsonNode, Map<String, Base> baseIds) {
    Team team = new Team();
    team.setName(jsonNode.get("team_name").textValue());
    team.setDescription(jsonNode.get("team_description").textValue());
    if (jsonNode.get("team_organization") != null) {
      Base teamOrganization = baseIds.get(jsonNode.get("team_organization").textValue());
      if (teamOrganization != null) {
        team.setOrganization((Organization) teamOrganization);
      }
    }
    if (jsonNode.has("team_contextual")) {
      team.setContextual(jsonNode.get("team_contextual").booleanValue());
    }
    return team;
  }

  // -- CHALLENGES / CHANNELS / ARTICLES 二开移除 --

  private void importObjectives(
      JsonNode importNode,
      String prefix,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "objectives")
        .forEach(
            nodeObjective -> {
              String id = nodeObjective.get("objective_id").textValue();
              Objective objective =
                  createObjective(nodeObjective, savedAttackChainRun, savedAttackChain);
              baseIds.put(id, this.objectiveRepository.save(objective));
            });
  }

  private Objective createObjective(
      JsonNode nodeObjective, AttackChainRun savedAttackChainRun, AttackChain savedAttackChain) {
    Objective objective = new Objective();
    objective.setTitle(nodeObjective.get("objective_title").textValue());
    objective.setDescription(nodeObjective.get("objective_description").textValue());
    objective.setPriority((short) nodeObjective.get("objective_priority").asInt(0));
    if (savedAttackChainRun != null) {
      objective.setAttackChainRun(savedAttackChainRun);
    } else if (savedAttackChain != null) {
      objective.setAttackChain(savedAttackChain);
    }

    return objective;
  }

  private void importLessons(
      JsonNode importNode,
      String prefix,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Map<String, Base> baseIds) {
    resolveJsonElements(importNode, prefix + "lessons_categories")
        .forEach(
            nodeLessonCategory -> {
              String id = nodeLessonCategory.get("lessonscategory_id").textValue();
              LessonsCategory lessonsCategory =
                  createLessonsCategory(
                      nodeLessonCategory, savedAttackChainRun, savedAttackChain, baseIds);
              baseIds.put(id, this.lessonsCategoryRepository.save(lessonsCategory));
            });
    resolveJsonElements(importNode, prefix + "lessons_questions")
        .forEach(
            nodeLessonQuestion -> {
              String id = nodeLessonQuestion.get("lessonsquestion_id").textValue();
              LessonsQuestion lessonsQuestion = createLessonsQuestion(nodeLessonQuestion, baseIds);
              baseIds.put(id, this.lessonsQuestionRepository.save(lessonsQuestion));
            });
  }

  private LessonsCategory createLessonsCategory(
      JsonNode nodeLessonCategory,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Map<String, Base> baseIds) {
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setName(nodeLessonCategory.get("lessons_category_name").textValue());
    lessonsCategory.setDescription(
        nodeLessonCategory.get("lessons_category_description").textValue());
    lessonsCategory.setOrder(nodeLessonCategory.get("lessons_category_order").intValue());
    if (savedAttackChainRun != null) {
      lessonsCategory.setAttackChainRun(savedAttackChainRun);
    } else if (savedAttackChain != null) {
      lessonsCategory.setAttackChain(savedAttackChain);
    }
    lessonsCategory.setTeams(
        resolveJsonIds(nodeLessonCategory, "lessons_category_teams").stream()
            .map(teamId -> (Team) baseIds.get(teamId))
            .filter(Objects::nonNull)
            .toList());

    return lessonsCategory;
  }

  private LessonsQuestion createLessonsQuestion(
      JsonNode nodeLessonQuestion, Map<String, Base> baseIds) {
    LessonsQuestion lessonsQuestion = new LessonsQuestion();
    lessonsQuestion.setContent(nodeLessonQuestion.get("lessons_question_content").textValue());
    lessonsQuestion.setExplanation(
        nodeLessonQuestion.get("lessons_question_explanation").textValue());
    lessonsQuestion.setOrder(nodeLessonQuestion.get("lessons_question_order").intValue());
    lessonsQuestion.setCategory(
        (LessonsCategory)
            baseIds.get(nodeLessonQuestion.get("lessons_question_category").textValue()));
    String categoryId = nodeLessonQuestion.get("lessons_question_category").asText();
    LessonsCategory lessonsCategory = (LessonsCategory) baseIds.get(categoryId);
    lessonsQuestion.setCategory(lessonsCategory);

    return lessonsQuestion;
  }

  private void importAttackChainNodes(
      JsonNode importNode,
      String prefix,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Asset asset,
      AssetGroup assetGroup,
      Map<String, Base> baseIds) {
    Supplier<Stream<JsonNode>> attackChainNodesStream =
        () ->
            importNode.has(prefix + "injects")
                ? resolveJsonElements(importNode, prefix + "injects")
                : Objects.equals(prefix, "inject_")
                    ? resolveJsonElements(importNode, prefix + "information")
                    : Stream.of();

    // Getting a list of all the children of the dependency
    List<String> children =
        attackChainNodesStream
            .get()
            .flatMap(
                jsonNode -> {
                  // List of dependencies of the attackChainNode
                  List<JsonNode> dependsOn =
                      StreamSupport.stream(jsonNode.get("inject_depends_on").spliterator(), false)
                          .toList();

                  // We return a stream containing all the children of the dependencies of the
                  // attackChainNode
                  return dependsOn.stream()
                      .map(
                          dependency ->
                              dependency
                                  .get("dependency_relationship")
                                  .get("inject_children_id")
                                  .asText());
                })
            .toList();

    // Getting a list of all the attackChainNodes that have no parents
    Stream<JsonNode> attackChainNodesNoParent =
        attackChainNodesStream
            .get()
            .filter(jsonNode -> !children.contains(jsonNode.get("inject_id").asText()));

    importAttackChainNodes(
        baseIds,
        savedAttackChainRun,
        savedAttackChain,
        asset,
        assetGroup,
        attackChainNodesNoParent.toList(),
        attackChainNodesStream.get().toList());
  }

  private void importAttackChainNodes(
      Map<String, Base> baseIds,
      AttackChainRun attackChainRun,
      AttackChain attackChain,
      Asset asset,
      AssetGroup assetGroup,
      List<JsonNode> attackChainNodesToAdd,
      List<JsonNode> allAttackChainNodes) {
    List<String> originalIds = new ArrayList<>();
    attackChainNodesToAdd.forEach(
        attackChainNodeNode -> {
          String attackChainNodeId = UUID.randomUUID().toString();
          String id = attackChainNodeNode.get("inject_id").textValue();
          String title = attackChainNodeNode.get("inject_title").textValue();
          String description = attackChainNodeNode.get("inject_description").textValue();
          String country = attackChainNodeNode.get("inject_country").textValue();
          String city = attackChainNodeNode.get("inject_city").textValue();
          boolean enabled =
              ofNullable(attackChainNodeNode.get("inject_enabled"))
                  .map(JsonNode::booleanValue)
                  .orElse(true);
          String nodeContractIdFromNode = null;
          JsonNode attackChainNodeContractNode =
              attackChainNodeNode.get("inject_injector_contract");
          if (attackChainNodeContractNode != null && !attackChainNodeContractNode.isNull()) {
            nodeContractIdFromNode =
                attackChainNodeContractNode.get("injector_contract_id").textValue();
          }

          // Check If attackChainNode contract exists
          if (nodeContractIdFromNode == null) {
            log.warn(
                "Import Inject Failed: Missing injector contract ID on inject: {}",
                attackChainNodeId);
            return;
          }
          Optional<NodeContract> nodeContract =
              this.nodeContractRepository.findById(nodeContractIdFromNode);

          String nodeContractId = null;

          // If not, rely on payload
          if (nodeContract.isEmpty()) {
            JsonNode payloadNode = attackChainNodeContractNode.get("injector_contract_payload");
            if (!payloadNode.isNull() && !payloadNode.isEmpty()) {
              String externalId = payloadNode.get("payload_external_id").textValue();
              // Rely on external collector
              if (hasText(externalId)) {
                Optional<NodeContract> nodeContractFromPayload =
                    this.nodeContractRepository.findOne(byPayloadExternalId(externalId));
                if (nodeContractFromPayload.isPresent()) {
                  nodeContractId = nodeContractFromPayload.get().getId();
                  // Create new payload
                } else {
                  log.info(
                      "Inject comes from a collector not set up in your environment, a new payload has been created.");
                  nodeContract = importPayload(payloadNode, baseIds);
                  nodeContractId = nodeContract.map(NodeContract::getId).orElse(null);
                }
                // Create new payload
              } else {
                nodeContract = importPayload(payloadNode, baseIds);
                nodeContractId = nodeContract.map(NodeContract::getId).orElse(null);
              }
            }
          } else {
            nodeContractId = nodeContract.get().getId();
          }

          if (nodeContractId == null) {
            if (attackChain != null
                && attackChain.getDependencies() != null
                && Arrays.asList(attackChain.getDependencies())
                    .contains(AttackChain.Dependency.STARTERPACK)) {
              // if we are importing the starter pack, we will create the nodeExecutor contract so
              // the
              // attackChainNodes are created before the nodeExecutor registered
              // once the nodeExecutor register the contract will be overriden and will be the one
              // provided by the nodeExecutor
              Payload createdPayload = nodeContract.map(ic -> ic.getPayload()).orElse(null);
              nodeContractId =
                  importNodeContractFromStarterPack(attackChainNodeContractNode, createdPayload)
                      .getId();
            } else {
              log.warn(
                  "Import Inject Failed: Unresolved injector contract ID on inject: {}",
                  attackChainNodeId);
            }
          }

          // If contract is not know, attackChainNode can't be imported
          String content =
              handleAttackChainNodeContent(baseIds, nodeContractId, attackChainNodeNode);
          Long dependsDuration = attackChainNodeNode.get("inject_depends_duration").asLong();
          boolean allTeams = attackChainNodeNode.get("inject_all_teams").booleanValue();
          if (attackChainRun != null) {
            attackChainNodeRepository.importSaveForAttackChainRun(
                attackChainNodeId,
                title,
                description,
                country,
                city,
                nodeContractId,
                allTeams,
                enabled,
                attackChainRun.getId(),
                dependsDuration,
                content);
          } else if (attackChain != null) {
            attackChainNodeRepository.importSaveForAttackChain(
                attackChainNodeId,
                title,
                description,
                country,
                city,
                nodeContractId,
                allTeams,
                enabled,
                attackChain.getId(),
                dependsDuration,
                content);
          } else {
            attackChainNodeRepository.importSaveStandAlone(
                attackChainNodeId,
                title,
                description,
                country,
                city,
                nodeContractId,
                allTeams,
                enabled,
                dependsDuration,
                content);
          }
          baseIds.put(id, new BaseHolder(attackChainNodeId));
          originalIds.add(id);

          // Once the attackChainNode has been saved, we deal with the dependencies
          ArrayNode attackChainNodeDependsOn =
              (ArrayNode) attackChainNodeNode.get("inject_depends_on");
          for (JsonNode dependsOnNode : attackChainNodeDependsOn) {
            // If there are dependencies where the added attackChainNode is the children, we add it
            // to the
            // database
            if (id.equals(
                dependsOnNode.get("dependency_relationship").get("inject_children_id").asText())) {
              AttackChainEdgeInput dependency =
                  mapper.convertValue(dependsOnNode, AttackChainEdgeInput.class);

              Optional<AttackChainNode> attackChainNodeParent =
                  attackChainNodeRepository.findById(
                      baseIds
                          .get(dependency.getRelationship().getAttackChainNodeParentId())
                          .getId());
              Optional<AttackChainNode> attackChainNodeChildren =
                  attackChainNodeRepository.findById(
                      baseIds
                          .get(dependency.getRelationship().getAttackChainNodeChildrenId())
                          .getId());

              if (attackChainNodeParent.isPresent() && attackChainNodeChildren.isPresent()) {
                AttackChainEdge attackChainEdge = new AttackChainEdge();
                attackChainEdge
                    .getCompositeId()
                    .setAttackChainNodeParent(attackChainNodeParent.get());
                attackChainEdge
                    .getCompositeId()
                    .setAttackChainNodeChildren(attackChainNodeChildren.get());
                attackChainEdge.setAttackChainEdgeCondition(dependency.getConditions());
                attackChainEdgesRepository.save(attackChainEdge);
              }
            }
          }
          // Tags
          List<String> attackChainNodeTagIds = resolveJsonIds(attackChainNodeNode, "inject_tags");
          attackChainNodeTagIds.forEach(
              tagId -> {
                Base base = baseIds.get(tagId);
                if (base == null || base.getId() == null) {
                  return;
                }
                attackChainNodeRepository.addTag(attackChainNodeId, base.getId());
              });
          // Teams
          List<String> attackChainNodeTeamIds = resolveJsonIds(attackChainNodeNode, "inject_teams");
          attackChainNodeTeamIds.forEach(
              teamId -> {
                Base base = baseIds.get(teamId);
                if (base == null || base.getId() == null) {
                  return;
                }
                attackChainNodeRepository.addTeam(attackChainNodeId, base.getId());
              });
          // Documents
          List<JsonNode> attackChainNodeDocuments =
              resolveJsonElements(attackChainNodeNode, "inject_documents").toList();
          attackChainNodeDocuments.forEach(
              jsonNode -> {
                String docId = jsonNode.get("document_id").textValue();
                if (hasText(docId) && baseIds.get(docId) != null) {
                  String documentId = baseIds.get(docId).getId();
                  boolean docAttached = jsonNode.get("document_attached").booleanValue();
                  attackChainNodeDocumentRepository.addAttackChainNodeDoc(
                      attackChainNodeId, documentId, docAttached);
                } else {
                  log.warn("Missing document in the exercise_documents property");
                }
              });

          // Define default AssetsGroup or Assets
          Optional<AttackChainNode> attackChainNodeOpt =
              attackChainNodeRepository.findById(attackChainNodeId);
          if (attackChainNodeOpt.isPresent()
              && attackChainNodeOpt.get().getNodeContract().isPresent()) {
            AttackChainNode attackChainNode = attackChainNodeOpt.get();
            if (assetGroup != null
                && nodeContractContentUtils.hasField(
                    attackChainNode.getNodeContract().get(), "asset_groups")) {
              attackChainNode.getAssetGroups().add(assetGroup);
            } else if (asset != null
                && nodeContractContentUtils.hasField(
                    attackChainNode.getNodeContract().get(), "assets")) {
              attackChainNode.getAssets().add(asset);
            }
            attackChainNodeRepository.save(attackChainNode);
          }
        });
    // Looking for children of created attackChainNodes
    List<JsonNode> childAttackChainNodes =
        allAttackChainNodes.stream()
            .filter(
                jsonNode -> {
                  ArrayNode attackChainNodeDependsOn =
                      (ArrayNode) jsonNode.get("inject_depends_on");

                  // We're getting the parents of this attackChainNode
                  List<String> parents =
                      StreamSupport.stream(attackChainNodeDependsOn.spliterator(), false)
                          .map(
                              dependency ->
                                  dependency
                                      .get("dependency_relationship")
                                      .get("inject_parent_id")
                                      .asText())
                          .toList();

                  // If the parents have been created in this pass, we need to take care of the
                  // children now
                  return originalIds.stream().anyMatch(parents::contains);
                })
            .toList();
    if (!childAttackChainNodes.isEmpty()) {
      importAttackChainNodes(
          baseIds,
          attackChainRun,
          attackChain,
          asset,
          assetGroup,
          childAttackChainNodes,
          allAttackChainNodes);
    }
  }

  /**
   * Used to create a dummy nodeExecutor to be able to import nodeExecutor contract from the
   * starterpack before the real contract is created by the real nodeExecutor
   *
   * @param importNode contract node
   * @return
   */
  private NodeExecutor createDummyNodeExecutor(JsonNode importNode) {

    return nodeExecutorService.createDummyNodeExecutor(
        importNode.get("injector_contract_injector_type").asText(),
        importNode.get("injector_contract_injector_type_name").asText());
  }

  /**
   * Import nodeExecutor contract from the starterpack before the real contract is created by the
   * real nodeExecutor, this contract will be overriden
   *
   * @param importNode contract node
   * @param payload to set on contract
   * @return
   */
  private NodeContract importNodeContractFromStarterPack(JsonNode importNode, Payload payload) {
    NodeContract nodeContract = new NodeContract();
    nodeContract.setId(importNode.get("injector_contract_id").textValue());
    nodeContract.setCustom(false);
    nodeContract.setContent(importNode.get("injector_contract_content").textValue());
    nodeContract.setNodeExecutor(createDummyNodeExecutor(importNode));
    nodeContract.setConvertedContent((ObjectNode) importNode.get("convertedContent"));
    nodeContract.setExternalId(importNode.get("injector_contract_external_id").textValue());
    nodeContract.setAtomicTesting(
        importNode.get("injector_contract_atomic_testing").booleanValue());
    nodeContract.setManual(importNode.get("injector_contract_manual").booleanValue());
    nodeContract.setNeedsExecutor(
        importNode.get("injector_contract_needs_executor").booleanValue());
    nodeContract.setPlatforms(
        Endpoint.PLATFORM_TYPE.fromJsonNode(importNode.get("injector_contract_platforms")));
    nodeContract.setLabels(
        new ObjectMapper()
            .convertValue(importNode.get("injector_contract_labels"), new TypeReference<>() {}));
    nodeContract.setPayload(payload);
    return nodeContractRepository.save(nodeContract);
  }

  public static ContractOutputType formatStringToContractOutputType(String value) {
    for (ContractOutputType type : ContractOutputType.values()) {
      if (type.getLabel().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown ContractOutputType: " + value);
  }

  private ContractOutputElementInput buildOuputElementFromJsonNode(
      JsonNode node, Map<String, Base> baseIds) {
    ContractOutputElementInput outputElement = new ContractOutputElementInput();
    outputElement.setFinding(node.get("contract_output_element_is_finding").asBoolean());
    outputElement.setRule(node.get("contract_output_element_rule").textValue());
    outputElement.setName(node.get("contract_output_element_name").textValue());
    outputElement.setKey(node.get("contract_output_element_key").textValue());
    outputElement.setType(
        formatStringToContractOutputType(node.get("contract_output_element_type").textValue()));
    importTags(node, "contract_output_element_tags", baseIds);
    outputElement.setTagIds(
        resolveJsonIds(node, "contract_output_element_tags").stream()
            .filter(baseIds::containsKey)
            .map(tid -> baseIds.get(tid).getId())
            .toList());
    ArrayNode regexGroupNodes = (ArrayNode) node.get("contract_output_element_regex_groups");
    for (JsonNode regexGroupNode : regexGroupNodes) {
      RegexGroupInput regexGroup = new RegexGroupInput();
      regexGroup.setField(regexGroupNode.get("regex_group_field").textValue());
      regexGroup.setIndexValues(regexGroupNode.get("regex_group_index_values").textValue());
      outputElement.getRegexGroups().add(regexGroup);
    }
    return outputElement;
  }

  private OutputParserInput buildOutputParserFromJsonNode(
      JsonNode node, Map<String, Base> baseIds) {
    OutputParserInput parser = new OutputParserInput();
    parser.setType(ParserType.valueOf(node.get("output_parser_type").textValue()));
    parser.setMode(ParserMode.valueOf(node.get("output_parser_mode").textValue()));
    ArrayNode outputElementNodes = (ArrayNode) node.get("output_parser_contract_output_elements");
    for (JsonNode outputElementNode : outputElementNodes) {
      parser
          .getContractOutputElements()
          .add(buildOuputElementFromJsonNode(outputElementNode, baseIds));
    }
    return parser;
  }

  private Set<OutputParserInput> buildOutputParsersFromPayloadJsonNode(
      JsonNode payloadNode, Map<String, Base> baseIds) {
    Set<OutputParserInput> outputParserInputs = new HashSet<>();
    if (!payloadNode.has("payload_output_parsers")) {
      return outputParserInputs;
    }

    ArrayNode outputParserNodes = (ArrayNode) payloadNode.get("payload_output_parsers");
    for (JsonNode outputParserNode : outputParserNodes) {
      outputParserInputs.add(buildOutputParserFromJsonNode(outputParserNode, baseIds));
    }
    return outputParserInputs;
  }

  private String importPayloadAsMain(
      @NotNull final JsonNode importNode, Map<String, Base> baseIds) {
    JsonNode payloadNode = importNode.get("payload_information");
    if (payloadNode == null) {
      return null;
    }

    if (payloadNode.has("executable_file")) {
      ((ObjectNode) payloadNode)
          .put("executable_file",
              baseIds.get(payloadNode.get("executable_file").textValue()).getId());
    }
    if (payloadNode.has("file_drop_file")) {
      ((ObjectNode) payloadNode)
          .put("file_drop_file", baseIds.get(payloadNode.get("file_drop_file").textValue()).getId());
    }

    if (payloadNode.has("payload_arguments")) {
      for (JsonNode argNode : payloadNode.get("payload_arguments")) {
        if (argNode.has("type") && "document".equals(argNode.get("type").asText())) {
          JsonNode defaultValueNode = argNode.get("default_value");
          if (defaultValueNode != null
              && !defaultValueNode.asText().isBlank()
              && baseIds.containsKey(defaultValueNode.asText())) {
            ((ObjectNode) argNode)
                .put("default_value", baseIds.get(defaultValueNode.asText()).getId());
          }
        }
      }
    }

    PayloadCreateInput payloadCreateInput = buildPayload(payloadNode);
    payloadCreateInput.setOutputParsers(
        buildOutputParsersFromPayloadJsonNode(payloadNode, baseIds));
    payloadCreateInput.setDomainIds(importDomains(payloadNode, "payload_", baseIds));

    List<String> attackPatternIds = importAttackPattern(payloadNode, "payload_", baseIds);

    payloadCreateInput.setAttackPatternsIds(attackPatternIds);
    payloadCreateInput.setDetectionRemediations(buildDetectionRemediationsJsonNode(payloadNode));
    Payload payload = this.payloadCreationService.createPayload(payloadCreateInput);
    payload.setTags(
        resolveJsonIds(payloadNode, "payload_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));
    Optional<NodeContract> nodeContractFromPayload =
        this.nodeContractRepository.findOne(byPayloadId(payload.getId()));
    if (nodeContractFromPayload.isPresent()) {
      return nodeContractFromPayload.get().getId();
    } else {
      log.warn("An error has occurred when importing the payload: {}", payload.getName());
      return null;
    }
  }

  private Optional<NodeContract> importPayload(
      @NotNull final JsonNode payloadNode, Map<String, Base> baseIds) {
    // swap executable file id or file drop file id
    if (payloadNode.has("executable_file")) {
      ((ObjectNode) payloadNode)
          .put("executable_file",
              baseIds.get(payloadNode.get("executable_file").textValue()).getId());
    }
    if (payloadNode.has("file_drop_file")) {
      ((ObjectNode) payloadNode)
          .put("file_drop_file", baseIds.get(payloadNode.get("file_drop_file").textValue()).getId());
    }

    PayloadCreateInput payloadCreateInput = buildPayload(payloadNode);
    payloadCreateInput.setOutputParsers(
        buildOutputParsersFromPayloadJsonNode(payloadNode, baseIds));

    payloadCreateInput.setDomainIds(importDomains(payloadNode, "payload_", baseIds));

    List<String> attackPatternIds = importAttackPattern(payloadNode, "payload_", baseIds);
    payloadCreateInput.setAttackPatternsIds(attackPatternIds);
    payloadCreateInput.setDetectionRemediations(buildDetectionRemediationsJsonNode(payloadNode));
    Payload payload = this.payloadCreationService.createPayload(payloadCreateInput);
    payload.setTags(
        resolveJsonIds(payloadNode, "payload_tags").stream()
            .map(baseIds::get)
            .map(Tag.class::cast)
            .collect(Collectors.toSet()));

    Optional<NodeContract> nodeContractFromPayload =
        this.nodeContractRepository.findOne(byPayloadId(payload.getId()));

    if (nodeContractFromPayload.isPresent()) {
      return nodeContractFromPayload;
    } else {
      log.warn("An error has occurred when importing the payload: {}", payload.getName());
      NodeContract nodeContract = new NodeContract();
      nodeContract.setPayload(payload);
      return Optional.of(nodeContract);
    }
  }

  private List<DetectionRemediationInput> buildDetectionRemediationsJsonNode(JsonNode payloadNode) {
    List<DetectionRemediationInput> detectionRemediationInputs = new ArrayList<>();

    JsonNode remediationsNode = payloadNode.get("payload_detection_remediations");
    if (remediationsNode == null || !remediationsNode.isArray()) {
      return detectionRemediationInputs;
    }

    for (JsonNode detectionNode : remediationsNode) {
      String valuesText = getTextValue(detectionNode, "detection_remediation_values");
      String type = getTextValue(detectionNode, "detection_remediation_collector_type");

      if (valuesText.isEmpty()) {
        continue;
      }

      Optional<Collector> collector = collectorRepository.findByType(type);
      if (collector.isPresent()) {
        detectionRemediationInputs.add(buildDetectionRemediationFromJsonNode(detectionNode));
      } else {
        log.warn("Import Detection Remediations: Missing Collector type: {}", type);
      }
    }

    return detectionRemediationInputs;
  }

  private DetectionRemediationInput buildDetectionRemediationFromJsonNode(JsonNode node) {
    DetectionRemediationInput detectionRemediation = new DetectionRemediationInput();
    detectionRemediation.setValues((node.get("detection_remediation_values").textValue()));
    detectionRemediation.setCollectorType(
        (node.get("detection_remediation_collector_type").textValue()));
    return detectionRemediation;
  }

  private String getTextValue(JsonNode node, String fieldName) {
    JsonNode fieldNode = node.get(fieldName);
    return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText().trim() : "";
  }

  private void importVariables(
      JsonNode importNode,
      AttackChainRun savedAttackChainRun,
      AttackChain savedAttackChain,
      Map<String, Base> baseIds) {
    Optional<Iterator<JsonNode>> variableNodesOpt = Optional.empty();
    if (ofNullable(importNode.get(EXERCISE_VARIABLES)).isPresent()) {
      variableNodesOpt = ofNullable(importNode.get(EXERCISE_VARIABLES)).map(JsonNode::elements);
    } else if (ofNullable(importNode.get(SCENARIO_VARIABLES)).isPresent()) {
      variableNodesOpt = ofNullable(importNode.get(SCENARIO_VARIABLES)).map(JsonNode::elements);
    }
    variableNodesOpt.ifPresent(
        variableNodes ->
            variableNodes.forEachRemaining(
                variableNode -> {
                  String id = VariableWithValueMixin.getId(variableNode);
                  Variable variable = VariableWithValueMixin.build(variableNode);
                  if (savedAttackChainRun != null) {
                    variable.setAttackChainRun(savedAttackChainRun);
                  } else if (savedAttackChain != null) {
                    variable.setAttackChain(savedAttackChain);
                  }
                  Variable variableSaved = this.variableRepository.save(variable);
                  baseIds.put(id, variableSaved);
                }));
  }

  private String getNodeValue(JsonNode importNode) {
    return ofNullable(importNode).map(JsonNode::textValue).orElse(null);
  }

  private static class BaseHolder implements Base {

    private String id;

    public BaseHolder(String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public void setId(String id) {
      this.id = id;
    }
  }
}
