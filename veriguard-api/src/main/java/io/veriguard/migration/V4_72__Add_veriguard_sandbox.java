package io.veriguard.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_72__Add_veriguard_sandbox extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE veriguard_sandboxes (
            veriguard_sandbox_id varchar(255) PRIMARY KEY,
            veriguard_sandbox_name varchar(255) NOT NULL,
            veriguard_sandbox_description text,
            veriguard_sandbox_provider_type varchar(255) NOT NULL,
            veriguard_sandbox_endpoint varchar(1024) NOT NULL,
            veriguard_sandbox_network_policy varchar(255) NOT NULL,
            veriguard_sandbox_network_rules jsonb NOT NULL DEFAULT '[]'::jsonb,
            veriguard_sandbox_auto_restore_enabled boolean NOT NULL,
            veriguard_sandbox_supported_sample_types jsonb NOT NULL DEFAULT '[]'::jsonb,
            veriguard_sandbox_status varchar(255) NOT NULL,
            veriguard_sandbox_created_at timestamp with time zone NOT NULL DEFAULT now(),
            veriguard_sandbox_updated_at timestamp with time zone NOT NULL DEFAULT now()
          );
          """);

      statement.execute(
          """
          CREATE INDEX idx_veriguard_sandboxes_status
          ON veriguard_sandboxes (veriguard_sandbox_status);
          """);

      statement.execute(
          """
          CREATE INDEX idx_veriguard_sandboxes_provider
          ON veriguard_sandboxes (veriguard_sandbox_provider_type);
          """);
    }
  }
}
