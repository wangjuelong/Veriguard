package io.veriguard.database.repository;

import io.veriguard.database.model.ChallengeAttempt;
import io.veriguard.database.model.ChallengeAttemptId;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengeAttemptRepository
    extends CrudRepository<ChallengeAttempt, ChallengeAttemptId>,
        JpaSpecificationExecutor<ChallengeAttempt> {}
