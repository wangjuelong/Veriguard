package io.veriguard.database.repository;

import io.veriguard.database.model.LinkExpectationTrace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LinkExpectationTraceRepository extends JpaRepository<LinkExpectationTrace, UUID> {}
