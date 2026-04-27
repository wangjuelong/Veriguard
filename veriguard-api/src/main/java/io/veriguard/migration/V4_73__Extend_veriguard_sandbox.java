package io.veriguard.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_73__Extend_veriguard_sandbox extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE veriguard_sandboxes DROP COLUMN IF EXISTS veriguard_sandbox_endpoint;");
      statement.execute("ALTER TABLE veriguard_sandboxes DROP COLUMN IF EXISTS veriguard_sandbox_provider_type;");
      statement.execute(
          "ALTER TABLE veriguard_sandboxes "
              + "ADD CONSTRAINT uk_veriguard_sandboxes_name UNIQUE (veriguard_sandbox_name);");
    }
  }
}
