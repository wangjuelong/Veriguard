package io.veriguard.database.repository;

import io.veriguard.database.model.VeriguardSandbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VeriguardSandboxRepository
    extends JpaRepository<VeriguardSandbox, String>, JpaSpecificationExecutor<VeriguardSandbox> {}
