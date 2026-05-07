package io.veriguard.rest.attack_chain_node.exports;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.*;
import io.veriguard.export.FileExportBase;
import io.veriguard.rest.attack_chain_run.exports.ExportOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import org.hibernate.Hibernate;

@Getter
@JsonInclude(NON_NULL)
public class AttackChainNodesFileExport extends FileExportBase {
  @JsonProperty("inject_information")
  private List<AttackChainNode> attackChainNodes;

  @JsonProperty("inject_documents")
  private List<Document> getDocuments() throws IOException {
    List<Document> documents = new ArrayList<>();

    documents.addAll(
        attackChainNodes.stream()
            .flatMap(attackChainNode -> attackChainNode.getDocuments().stream().map(AttackChainNodeDocument::getDocument))
            .toList());
    documents.addAll(
        attackChainNodes.stream()
            .flatMap(
                attackChainNode -> {
                  if (attackChainNode.getPayload().isEmpty()) {
                    return Stream.of();
                  }
                  Payload pl = attackChainNode.getPayload().get();
                  return pl.getAttachedDocument().isPresent()
                      ? Stream.of(pl.getAttachedDocument().get())
                      : Stream.of();
                })
            .toList());

    return documents;
  }

  @JsonProperty("inject_tags")
  private List<Tag> getTags() throws IOException {
    List<Tag> allTags = new ArrayList<>();
    allTags.addAll(this.getTeams().stream().flatMap(team -> team.getTags().stream()).toList());
    allTags.addAll(this.getUsers().stream().flatMap(user -> user.getTags().stream()).toList());
    allTags.addAll(
        this.getOrganizations().stream()
            .flatMap(organization -> organization.getTags().stream())
            .toList());
    allTags.addAll(this.getDocuments().stream().flatMap(doc -> doc.getTags().stream()).toList());
    this.attackChainNodes.forEach(
        attackChainNode -> {
          allTags.addAll(attackChainNode.getTags());
          attackChainNode
              .getPayload()
              .ifPresent(
                  payload ->
                      payload.getOutputParsers().stream()
                          .flatMap(parser -> parser.getContractOutputElements().stream())
                          .flatMap(element -> element.getTags().stream())
                          .forEach(allTags::add));
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

  @JsonProperty("inject_teams")
  private List<Team> getTeams() {
    return ExportOptions.has(ExportOptions.WITH_TEAMS, this.exportOptionsMask)
        ? attackChainNodes.stream().flatMap(attackChainNode -> attackChainNode.getTeams().stream()).toList()
        : List.of();
  }

  @JsonProperty("inject_users")
  private List<User> getUsers() {
    return ExportOptions.has(ExportOptions.WITH_PLAYERS, this.exportOptionsMask)
        ? this.getTeams().stream().flatMap(team -> team.getUsers().stream()).toList()
        : List.of();
  }

  @JsonProperty("inject_organizations")
  private List<Organization> getOrganizations() {
    List<Organization> orgs = new ArrayList<>();
    orgs.addAll(
        this.getUsers().stream()
            .map(user -> (Organization) Hibernate.unproxy(user.getOrganization()))
            .filter(Objects::nonNull)
            .toList());
    orgs.addAll(
        this.getTeams().stream()
            .map(team -> (Organization) Hibernate.unproxy(team.getOrganization()))
            .filter(Objects::nonNull)
            .toList());
    return orgs;
  }

  @JsonIgnore
  public List<String> getAllDocumentIds() throws IOException {
    return new ArrayList<>(this.getDocuments().stream().map(Document::getId).toList());
  }

  private AttackChainNodesFileExport(List<AttackChainNode> attackChainNodes, ObjectMapper objectMapper) {
    super(objectMapper);
    this.attackChainNodes = attackChainNodes;
  }

  public static AttackChainNodesFileExport fromAttackChainNodes(List<AttackChainNode> attackChainNodes, ObjectMapper objectMapper) {
    return new AttackChainNodesFileExport(attackChainNodes, objectMapper);
  }

  @Override
  public AttackChainNodesFileExport withOptions(int exportOptionsMask) {
    return (AttackChainNodesFileExport) super.withOptions(exportOptionsMask);
  }
}
