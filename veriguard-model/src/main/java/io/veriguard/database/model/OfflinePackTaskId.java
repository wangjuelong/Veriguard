package io.veriguard.database.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link OfflinePackTaskEntity} — mirrors the {@code (pack_id, ordinal)}
 * pair declared in Flyway V22. Same shape as {@link OfflinePackResultId}; the two ID classes are
 * kept distinct so the JPA mapper does not accidentally resolve one entity's key class for the
 * other.
 */
public class OfflinePackTaskId implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID packId;
  private int ordinal;

  public OfflinePackTaskId() {}

  public OfflinePackTaskId(UUID packId, int ordinal) {
    this.packId = packId;
    this.ordinal = ordinal;
  }

  public UUID getPackId() {
    return packId;
  }

  public void setPackId(UUID packId) {
    this.packId = packId;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof OfflinePackTaskId other)) {
      return false;
    }
    return ordinal == other.ordinal && Objects.equals(packId, other.packId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(packId, ordinal);
  }
}
