package io.veriguard.rest.inject.exports;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.*;
import io.veriguard.export.FileExportBase;
import io.veriguard.rest.exercise.exports.ExportOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import org.hibernate.Hibernate;

@Getter
@JsonInclude(NON_NULL)
public class InjectsFileExport extends FileExportBase {
  @JsonProperty("inject_information")
  private List<Inject> injects;

  @JsonProperty("inject_documents")
  private List<Document> getDocuments() throws IOException {
    List<Document> documents = new ArrayList<>();

    documents.addAll(
        injects.stream()
            .flatMap(inject -> inject.getDocuments().stream().map(InjectDocument::getDocument))
            .toList());
    documents.addAll(
        injects.stream()
            .flatMap(
                inject -> {
                  if (inject.getPayload().isEmpty()) {
                    return Stream.of();
                  }
                  Payload pl = inject.getPayload().get();
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
    this.injects.forEach(
        inject -> {
          allTags.addAll(inject.getTags());
          inject
              .getPayload()
              .ifPresent(
                  payload ->
                      payload.getOutputParsers().stream()
                          .flatMap(parser -> parser.getContractOutputElements().stream())
                          .flatMap(element -> element.getTags().stream())
                          .forEach(allTags::add));
          inject
              .getInjectorContract()
              .ifPresent(
                  injectorContract -> {
                    if (injectorContract.getPayload() != null) {
                      allTags.addAll(injectorContract.getPayload().getTags());
                    }
                  });
        });

    return allTags;
  }

  @JsonProperty("inject_teams")
  private List<Team> getTeams() {
    return ExportOptions.has(ExportOptions.WITH_TEAMS, this.exportOptionsMask)
        ? injects.stream().flatMap(inject -> inject.getTeams().stream()).toList()
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

  private InjectsFileExport(List<Inject> injects, ObjectMapper objectMapper) {
    super(objectMapper);
    this.injects = injects;
  }

  public static InjectsFileExport fromInjects(List<Inject> injects, ObjectMapper objectMapper) {
    return new InjectsFileExport(injects, objectMapper);
  }

  @Override
  public InjectsFileExport withOptions(int exportOptionsMask) {
    return (InjectsFileExport) super.withOptions(exportOptionsMask);
  }
}
