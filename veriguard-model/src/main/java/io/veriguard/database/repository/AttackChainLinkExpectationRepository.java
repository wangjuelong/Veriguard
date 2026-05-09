package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainLinkExpectation;
import io.veriguard.database.model.LinkExpectationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackChainLinkExpectationRepository
    extends JpaRepository<AttackChainLinkExpectation, UUID> {

  List<AttackChainLinkExpectation> findByAttackChainRunId(String attackChainRunId);

  List<AttackChainLinkExpectation> findByAttackChainRunIdAndStatus(
      String attackChainRunId, LinkExpectationStatus status);
}
