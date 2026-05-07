package io.veriguard.service;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.repository.AttackChainRunRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoadService {

  private final AttackChainRunRepository attackChainRunRepository;

  @Transactional
  public AttackChainRun attackChainRun(@NotBlank final String attackChainRunId) {
    AttackChainRun attackChainRun = this.attackChainRunRepository.findById(attackChainRunId).orElseThrow();
    Hibernate.initialize(attackChainRun.getTeams());
    Hibernate.initialize(attackChainRun.getTeamUsers());
    Hibernate.initialize(attackChainRun.getTags());
    Hibernate.initialize(attackChainRun.getObjectives());
    Hibernate.initialize(attackChainRun.getDocuments());
    Hibernate.initialize(attackChainRun.getLessonsCategories());
    attackChainRun
        .getLessonsCategories()
        .forEach(
            lessonsCategory -> {
              Hibernate.initialize(lessonsCategory.getQuestions());
              Hibernate.initialize(lessonsCategory.getTeams());
            });
    Hibernate.initialize(attackChainRun.getAttackChainNodes());
    return attackChainRun;
  }
}
