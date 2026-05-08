package io.veriguard.rest.attack_chain_run.service;

import static java.time.Duration.between;
import static java.time.Instant.now;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.Pause;
import io.veriguard.database.repository.PauseRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Validated
@Service
public class PauseAttackChainRunService {
  private final PauseRepository pauseRepository;

  public void deleteAllPauseByAttackChainRunId(String attackChainRunId) {
    pauseRepository.deleteAllPauseByAttackChainRunId(attackChainRunId);
  }

  public void endPauseByAttackChainRun(Instant lastPause, AttackChainRun attackChainRun) {
    Pause pause = new Pause();
    pause.setDate(lastPause);
    pause.setAttackChainRun(attackChainRun);
    pause.setDuration(between(lastPause, now()).getSeconds());
    pauseRepository.save(pause);
  }
}
