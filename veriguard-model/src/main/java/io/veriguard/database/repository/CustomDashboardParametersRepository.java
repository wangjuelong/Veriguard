package io.veriguard.database.repository;

import io.veriguard.database.model.CustomDashboardParameters;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomDashboardParametersRepository
    extends CrudRepository<CustomDashboardParameters, String>,
        JpaSpecificationExecutor<CustomDashboardParameters> {}
