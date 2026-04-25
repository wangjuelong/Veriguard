package io.openaev.database.repository;

import io.openaev.database.model.VeriguardSandbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VeriguardSandboxRepository
    extends JpaRepository<VeriguardSandbox, String>, JpaSpecificationExecutor<VeriguardSandbox> {}
