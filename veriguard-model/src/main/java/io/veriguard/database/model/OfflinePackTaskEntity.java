package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * JPA entity mirroring the {@code offline_pack_task} table (Flyway V22 / C1-Platform-3 follow-up).
 *
 * <p>One row per task drained for a {@code .vpack} export — captures the ordered {@code task_id}
 * list so {@code OfflinePackImportService} can correlate the agent's wire-schema results (a JSON
 * array, no embedded task_id field) back to platform-side task identifiers when the {@code
 * .vresults} envelope is imported hours / days later.
 *
 * <p>Distinct from {@link OfflinePackResultEntity}: result rows record what the agent reported on
 * import; task rows record what the platform sent on export. They share the {@code (pack_id,
 * ordinal)} key shape so an admin UI can join one against the other to render a per-task row with
 * "what we asked" + "what came back" side-by-side.
 */
@Getter
@Setter
@Entity
@Table(name = "offline_pack_task")
@IdClass(OfflinePackTaskId.class)
public class OfflinePackTaskEntity {

  @Id
  @Column(name = "pack_id")
  @JsonProperty("pack_id")
  private UUID packId;

  @Id
  @Column(name = "ordinal")
  @JsonProperty("ordinal")
  private int ordinal;

  @Column(name = "task_id", nullable = false)
  @JsonProperty("task_id")
  private String taskId;
}
