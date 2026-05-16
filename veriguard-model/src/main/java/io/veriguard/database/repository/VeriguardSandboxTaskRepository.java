package io.veriguard.database.repository;

import io.veriguard.database.model.VeriguardSandboxTask;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VeriguardSandboxTaskRepository
    extends JpaRepository<VeriguardSandboxTask, String> {

  List<VeriguardSandboxTask> findByStatusInOrderByCreatedAtAsc(
      Collection<VeriguardSandboxTask.Status> statuses);

  Optional<VeriguardSandboxTask> findByCapeTaskId(Long capeTaskId);

  List<VeriguardSandboxTask> findAllByOrderByCreatedAtDesc();
}
