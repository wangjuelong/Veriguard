package io.veriguard.export;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.*;
import io.veriguard.rest.attack_chain_node.exports.AttackChainNodesFileExport;
import io.veriguard.rest.attack_chain_run.exports.AttackChainRunFileExport;
import io.veriguard.rest.attack_chain_run.exports.ExportOptions;
import io.veriguard.rest.attack_chain_run.exports.VariableMixin;
import io.veriguard.rest.attack_chain_run.exports.VariableWithValueMixin;
import lombok.Getter;

@Getter
public class FileExportBase {
  @JsonProperty("export_version")
  private int version = 1;

  @JsonIgnore protected int exportOptionsMask = ExportOptions.mask(false, false, false);

  @JsonIgnore public final ObjectMapper objectMapper;

  protected FileExportBase(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;

    this.objectMapper.addMixIn(Base.class, Mixins.Base.class);
    this.objectMapper.addMixIn(AttackChainRun.class, Mixins.AttackChainRun.class);
    this.objectMapper.addMixIn(Document.class, Mixins.Document.class);
    this.objectMapper.addMixIn(Objective.class, Mixins.Objective.class);
    this.objectMapper.addMixIn(LessonsCategory.class, Mixins.LessonsCategory.class);
    this.objectMapper.addMixIn(LessonsQuestion.class, Mixins.LessonsQuestion.class);
    this.objectMapper.addMixIn(User.class, Mixins.User.class);
    this.objectMapper.addMixIn(Organization.class, Mixins.Organization.class);
    this.objectMapper.addMixIn(AttackChainNode.class, Mixins.AttackChainNode.class);
    this.objectMapper.addMixIn(Tag.class, Mixins.Tag.class);
    this.objectMapper.addMixIn(NodeContract.class, Mixins.NodeContract.class);
    this.objectMapper.addMixIn(AttackPattern.class, Mixins.AttackPattern.class);
    this.objectMapper.addMixIn(Payload.class, Mixins.Payload.class);
    this.objectMapper.addMixIn(KillChainPhase.class, Mixins.KillChainPhase.class);

    // default options
    // variables with no value
    this.objectMapper.addMixIn(Variable.class, VariableMixin.class);
    // empty teams
    this.objectMapper.addMixIn(Team.class, Mixins.EmptyTeam.class);
  }

  public FileExportBase withOptions(int exportOptionsMask) {
    this.exportOptionsMask = exportOptionsMask;

    // disable users if not requested; note negation
    if (!ExportOptions.has(ExportOptions.WITH_PLAYERS, this.exportOptionsMask)) {
      this.objectMapper.addMixIn(
          AttackChainRunFileExport.class, Mixins.AttackChainRunFileExport.class);
      this.objectMapper.addMixIn(
          AttackChainNodesFileExport.class, Mixins.AttackChainNodesFileExport.class);
    }

    if (ExportOptions.has(ExportOptions.WITH_TEAMS, this.exportOptionsMask)) {
      this.objectMapper.addMixIn(
          Team.class,
          ExportOptions.has(ExportOptions.WITH_PLAYERS, this.exportOptionsMask)
              ? Mixins.Team.class
              : Mixins.EmptyTeam.class);
    }
    if (ExportOptions.has(ExportOptions.WITH_VARIABLE_VALUES, this.exportOptionsMask)) {
      this.objectMapper.addMixIn(Variable.class, VariableWithValueMixin.class);
    } else {
      this.objectMapper.addMixIn(Variable.class, VariableMixin.class);
    }
    return this;
  }
}
