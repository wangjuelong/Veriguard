package io.veriguard.rest.attack_chain_run.exports;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.*;
import io.veriguard.export.FileExportBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

@Getter
@Setter
@JsonInclude(NON_NULL)
public class AttackChainRunFileExport extends FileExportBase {
  @JsonProperty("exercise_information")
  private final AttackChainRun attackChainRun;

  @JsonProperty("exercise_teams")
  private List<Team> teams;

  public List<Team> getTeams() {
    if (teams == null) {
      return this.attackChainRun != null
              && ExportOptions.has(ExportOptions.WITH_TEAMS, this.exportOptionsMask)
          ? this.attackChainRun.getTeams().stream().toList()
          : new ArrayList<>();
    }
    return teams;
  }

  @JsonProperty("exercise_objectives")
  private List<Objective> objectives;

  public List<Objective> getObjectives() {
    if (objectives == null) {
      return this.attackChainRun == null
          ? new ArrayList<>()
          : this.attackChainRun.getObjectives().stream().toList();
    }
    return objectives;
  }

  @JsonProperty("exercise_users")
  private List<User> users;

  public List<User> getUsers() {
    if (users == null) {
      return this.attackChainRun != null
              && ExportOptions.has(ExportOptions.WITH_PLAYERS, this.exportOptionsMask)
          ? this.attackChainRun.getTeams().stream()
              .flatMap(team -> team.getUsers().stream())
              .distinct()
              .toList()
          : new ArrayList<>();
    }
    return users;
  }

  @JsonProperty("exercise_organizations")
  private List<Organization> organizations;

  public List<Organization> getOrganizations() {
    if (organizations == null) {
      if (this.attackChainRun == null) {
        return new ArrayList<>();
      }
      List<Organization> orgs = new ArrayList<>();
      orgs.addAll(
          this.attackChainRun.getUsers().stream()
              .map(user -> (Organization) Hibernate.unproxy(user.getOrganization()))
              .filter(Objects::nonNull)
              .toList());
      orgs.addAll(
          this.attackChainRun.getTeams().stream()
              .map(team -> (Organization) Hibernate.unproxy(team.getOrganization()))
              .filter(Objects::nonNull)
              .toList());
      return orgs;
    }
    return organizations;
  }

  @JsonProperty("exercise_injects")
  private List<AttackChainNode> attackChainNodes;

  public List<AttackChainNode> getAttackChainNodes() {
    if (attackChainNodes == null) {
      return this.attackChainRun == null ? new ArrayList<>() : this.attackChainRun.getAttackChainNodes();
    }
    return attackChainNodes;
  }

  @JsonProperty("exercise_tags")
  private List<Tag> tags;

  public List<Tag> getTags() {
    if (tags == null) {
      if (this.attackChainRun == null) {
        return new ArrayList<>();
      } else {
        List<Tag> allTags = new ArrayList<>();
        allTags.addAll(this.attackChainRun.getTags().stream().toList());
        allTags.addAll(this.getTeams().stream().flatMap(team -> team.getTags().stream()).toList());
        allTags.addAll(this.getUsers().stream().flatMap(user -> user.getTags().stream()).toList());
        allTags.addAll(
            this.getOrganizations().stream()
                .flatMap(organization -> organization.getTags().stream())
                .toList());
        allTags.addAll(
            this.getDocuments().stream().flatMap(doc -> doc.getTags().stream()).toList());
        this.getAttackChainNodes()
            .forEach(
                attackChainNode -> {
                  allTags.addAll(attackChainNode.getTags());
                  attackChainNode
                      .getNodeContract()
                      .ifPresent(
                          nodeContract -> {
                            if (nodeContract.getPayload() != null) {
                              allTags.addAll(nodeContract.getPayload().getTags());
                            }
                          });
                });

        return allTags;
      }
    }
    return tags;
  }

  @JsonProperty("exercise_documents")
  private List<Document> documents;

  public List<Document> getDocuments() {
    if (documents == null) {
      if (this.attackChainRun == null) {
        return new ArrayList<>();
      }
      List<Document> docs = new ArrayList<>();
      docs.addAll(this.attackChainRun.getDocuments());
      docs.addAll(
          this.attackChainRun.getAttackChainNodes().stream()
              .flatMap(
                  attackChainNode -> {
                    if (attackChainNode.getPayload().isEmpty()) {
                      return Stream.of();
                    }
                    if (attackChainNode.getPayload().get().getAttachedDocument().isPresent()) {
                      return Stream.of(attackChainNode.getPayload().get().getAttachedDocument().get());
                    }
                    return Stream.of();
                  })
              .toList());
      return docs;
    }
    return documents;
  }

  @JsonProperty("exercise_lessons_categories")
  private List<LessonsCategory> lessonsCategories;

  public List<LessonsCategory> getLessonsCategories() {
    if (lessonsCategories == null) {
      return this.attackChainRun == null
          ? new ArrayList<>()
          : this.attackChainRun.getLessonsCategories().stream().toList();
    }
    return lessonsCategories;
  }

  @JsonProperty("exercise_lessons_questions")
  private List<LessonsQuestion> lessonsQuestions;

  public List<LessonsQuestion> getLessonsQuestions() {
    if (lessonsQuestions == null) {
      return this.attackChainRun == null
          ? new ArrayList<>()
          : this.getLessonsCategories().stream()
              .flatMap(category -> category.getQuestions().stream())
              .toList();
    }
    return lessonsQuestions;
  }

  @JsonIgnore public static final String EXERCISE_VARIABLES = "exercise_variables";

  @JsonProperty(EXERCISE_VARIABLES)
  private List<Variable> variables;

  public List<Variable> getVariables() {
    if (variables == null) {
      return this.attackChainRun == null
          ? new ArrayList<>()
          : this.attackChainRun.getVariables().stream().toList();
    }
    return variables;
  }

  @JsonIgnore
  public List<String> getAllDocumentIds() {
    return new ArrayList<>(this.getDocuments().stream().map(Document::getId).toList());
  }

  private AttackChainRunFileExport(AttackChainRun attackChainRun, ObjectMapper objectMapper) {
    super(objectMapper);
    this.attackChainRun = attackChainRun;
  }

  public static AttackChainRunFileExport fromAttackChainRun(AttackChainRun attackChainRun, ObjectMapper objectMapper) {
    return new AttackChainRunFileExport(attackChainRun, objectMapper);
  }

  @Override
  public AttackChainRunFileExport withOptions(int exportOptionsMask) {
    return (AttackChainRunFileExport) super.withOptions(exportOptionsMask);
  }
}
