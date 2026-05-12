package io.veriguard.database.repository;

import io.veriguard.database.model.SmtpProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SmtpProfileRepository
    extends CrudRepository<SmtpProfile, String>, JpaSpecificationExecutor<SmtpProfile> {

  Optional<SmtpProfile> findByName(String name);
}
