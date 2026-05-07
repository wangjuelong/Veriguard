package io.veriguard.utils.fixtures.composers;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.rest.exercise.service.AttackChainRunService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttackChainRunComposer extends ComposerBase<AttackChainRun> {
  @Autowired private AttackChainRunRepository attackChainRunRepository;
  @Autowired private AttackChainRunService attackChainRunService;
  @Autowired private NodeContractRepository nodeContractRepository;
  @Autowired private EntityManager entityManager;

  public class Composer extends InnerComposerBase<AttackChainRun> {
    private final AttackChainRun attackChainRun;
    private final List<AttackChainNodeComposer.Composer> attackChainNodeComposers = new ArrayList<>();
    private final List<LessonsCategoryComposer.Composer> categoryComposers = new ArrayList<>();
    private final List<TeamComposer.Composer> teamComposers = new ArrayList<>();
    private final List<ObjectiveComposer.Composer> objectiveComposers = new ArrayList<>();
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private final List<DocumentComposer.Composer> documentComposers = new ArrayList<>();
    private final List<VariableComposer.Composer> variableComposers = new ArrayList<>();
    private final List<PauseComposer.Composer> pauseComposers = new ArrayList<>();
    private Optional<SecurityCoverageComposer.Composer> securityCoverageComposer = Optional.empty();
    private Optional<SecurityCoverageSendJobComposer.Composer> securityCoverageSendJobComposer =
        Optional.empty();

    public Composer(AttackChainRun attackChainRun) {
      this.attackChainRun = attackChainRun;
    }

    public Composer withVariable(VariableComposer.Composer variableComposer) {
      variableComposers.add(variableComposer);
      List<Variable> variables = attackChainRun.getVariables();
      variables.add(variableComposer.get());
      variableComposer.get().setAttackChainRun(attackChainRun);
      this.attackChainRun.setVariables(variables);
      return this;
    }

    public Composer withSecurityCoverage(
        SecurityCoverageComposer.Composer securityCoverageWrapper) {
      securityCoverageComposer = Optional.of(securityCoverageWrapper);
      this.attackChainRun.setSecurityCoverage(securityCoverageWrapper.get());
      return this;
    }

    public Composer withAttackChainNodes(List<AttackChainNodeComposer.Composer> attackChainNodeComposers) {
      attackChainNodeComposers.forEach(this::withAttackChainNode);
      return this;
    }

    public Composer withSecurityCoverageSendJob(
        SecurityCoverageSendJobComposer.Composer securityCoverageSendJobWrapper) {
      this.securityCoverageSendJobComposer = Optional.of(securityCoverageSendJobWrapper);
      securityCoverageSendJobWrapper.get().setSimulation(this.attackChainRun);
      return this;
    }

    public Composer withAttackChainNode(AttackChainNodeComposer.Composer attackChainNodeComposer) {
      attackChainNodeComposers.add(attackChainNodeComposer);
      List<AttackChainNode> attackChainNodes = attackChainRun.getAttackChainNodes();
      attackChainNodeComposer.get().setAttackChainRun(attackChainRun);
      attackChainNodes.add(attackChainNodeComposer.get());
      this.attackChainRun.setAttackChainNodes(attackChainNodes);
      return this;
    }

    public Composer withLessonCategory(LessonsCategoryComposer.Composer categoryComposer) {
      this.categoryComposers.add(categoryComposer);
      List<LessonsCategory> tempCategories = this.attackChainRun.getLessonsCategories();
      tempCategories.add(categoryComposer.get());
      this.attackChainRun.setLessonsCategories(tempCategories);
      return this;
    }

    public Composer withTeam(TeamComposer.Composer teamComposer) {
      this.teamComposers.add(teamComposer);
      List<Team> tempTeams = this.attackChainRun.getTeams();
      tempTeams.add(teamComposer.get());
      this.attackChainRun.setTeams(tempTeams);
      return this;
    }

    // special composition that attackChainNodes the currently set users in currently set teams as
    // "ExerciseTeamUsers"
    public Composer withTeamUsers() {
      List<AttackChainRunTeamUser> tempTeamUsers = new ArrayList<>();
      this.attackChainRun
          .getTeams()
          .forEach(
              team ->
                  team.getUsers()
                      .forEach(
                          user -> {
                            AttackChainRunTeamUser attackChainRunTeamUser = new AttackChainRunTeamUser();
                            attackChainRunTeamUser.setAttackChainRun(attackChainRun);
                            attackChainRunTeamUser.setUser(user);
                            attackChainRunTeamUser.setTeam(team);
                            tempTeamUsers.add(attackChainRunTeamUser);
                          }));
      this.attackChainRun.setTeamUsers(tempTeamUsers);
      return this;
    }

    public Composer withObjective(ObjectiveComposer.Composer objectiveComposer) {
      this.objectiveComposers.add(objectiveComposer);
      List<Objective> tempObjectives = this.attackChainRun.getObjectives();
      tempObjectives.add(objectiveComposer.get());
      this.attackChainRun.setObjectives(tempObjectives);
      return this;
    }

    public Composer withTag(TagComposer.Composer tagComposer) {
      this.tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.attackChainRun.getTags();
      tempTags.add(tagComposer.get());
      this.attackChainRun.setTags(tempTags);
      return this;
    }

    public Composer withDocument(DocumentComposer.Composer documentComposer) {
      this.documentComposers.add(documentComposer);
      List<Document> tempDocuments = this.attackChainRun.getDocuments();
      tempDocuments.add(documentComposer.get());
      this.attackChainRun.setDocuments(tempDocuments);
      return this;
    }

    public Composer withPause(PauseComposer.Composer pauseComposer) {
      this.pauseComposers.add(pauseComposer);
      List<Pause> tempPauses = this.attackChainRun.getPauses();
      tempPauses.add(pauseComposer.get());
      this.attackChainRun.setPauses(tempPauses);
      return this;
    }

    public Composer withId(String id) {
      this.attackChainRun.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      attackChainRunRepository.save(attackChainRun);
      this.categoryComposers.forEach(LessonsCategoryComposer.Composer::persist);
      this.teamComposers.forEach(TeamComposer.Composer::persist);
      this.attackChainNodeComposers.forEach(AttackChainNodeComposer.Composer::persist);
      this.objectiveComposers.forEach(ObjectiveComposer.Composer::persist);
      this.tagComposers.forEach(TagComposer.Composer::persist);
      this.documentComposers.forEach(DocumentComposer.Composer::persist);
      this.variableComposers.forEach(VariableComposer.Composer::persist);
      this.pauseComposers.forEach(PauseComposer.Composer::persist);
      this.securityCoverageComposer.ifPresent(SecurityCoverageComposer.Composer::persist);
      this.securityCoverageSendJobComposer.ifPresent(
          SecurityCoverageSendJobComposer.Composer::persist);
      attackChainRunService.createAttackChainRun(attackChainRun);
      return this;
    }

    @Override
    public Composer delete() {
      attackChainRunRepository.delete(attackChainRun);
      this.securityCoverageSendJobComposer.ifPresent(
          SecurityCoverageSendJobComposer.Composer::delete);
      this.securityCoverageComposer.ifPresent(SecurityCoverageComposer.Composer::delete);
      this.variableComposers.forEach(VariableComposer.Composer::delete);
      this.documentComposers.forEach(DocumentComposer.Composer::delete);
      this.tagComposers.forEach(TagComposer.Composer::delete);
      this.objectiveComposers.forEach(ObjectiveComposer.Composer::delete);
      this.attackChainNodeComposers.forEach(AttackChainNodeComposer.Composer::delete);
      this.teamComposers.forEach(TeamComposer.Composer::delete);
      this.categoryComposers.forEach(LessonsCategoryComposer.Composer::delete);
      this.pauseComposers.forEach(PauseComposer.Composer::delete);
      return this;
    }

    @Override
    public AttackChainRun get() {
      return this.attackChainRun;
    }
  }

  public Composer forAttackChainRun(AttackChainRun attackChainRun) {
    generatedItems.add(attackChainRun);
    return new Composer(attackChainRun);
  }
}
