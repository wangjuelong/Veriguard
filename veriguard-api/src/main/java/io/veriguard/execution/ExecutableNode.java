package io.veriguard.execution;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.veriguard.database.model.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.hibernate.Hibernate;
import org.springframework.web.multipart.MultipartFile;

@Getter
public class ExecutableNode {

  private final Injection injection;
  private final int teamSize;
  private final boolean direct;
  private final boolean runtime;
  private final int documentSize;
  private final List<Team> teams;
  private final String attackChainRunId;
  private final List<Asset> assets;
  private final List<AssetGroup> assetGroups;
  private final List<ExecutionContext> users;

  @JsonIgnore private final List<MultipartFile> directAttachments = new ArrayList<>();

  public ExecutableNode(
      boolean runtime,
      boolean direct,
      Injection injection,
      List<Team> teams,
      List<Asset> assets,
      List<AssetGroup> assetGroups,
      List<ExecutionContext> users) {
    this.injection = injection;
    this.attackChainRunId = ofNullable(injection.getAttackChainRun()).map(AttackChainRun::getId).orElse(null);
    this.runtime = runtime;
    this.direct = direct;
    this.users = users;
    this.teams = teams;
    this.teams.forEach(team -> Hibernate.initialize(team.getTags()));
    this.assets = assets;
    this.assets.forEach(asset -> Hibernate.initialize(asset.getTags()));
    this.assetGroups = assetGroups;
    this.assetGroups.forEach(assetGroup -> Hibernate.initialize(assetGroup.getTags()));
    this.teamSize = teams.size();
    this.documentSize = injection.getAttackChainNode().getDocuments().size();
  }

  public ExecutableNode(
      boolean runtime, boolean direct, Injection injection, List<ExecutionContext> users) {
    this(runtime, direct, injection, List.of(), List.of(), List.of(), users);
  }

  public void addDirectAttachment(MultipartFile file) {
    this.directAttachments.add(file);
  }
}
