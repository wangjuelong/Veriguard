package io.veriguard.database.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link OfflinePackResultEntity} — mirrors the {@code (pack_id,
 * ordinal)} pair declared in Flyway V21.
 *
 * <p>Hibernate requires composite PK classes to: be {@link Serializable}, have a no-arg
 * constructor, and implement {@link #equals(Object)} + {@link #hashCode()} pairwise on every PK
 * field. Lombok is intentionally not used so the class stays explicit at the JPA boundary.
 */
public class OfflinePackResultId implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID packId;
  private int ordinal;

  public OfflinePackResultId() {}

  public OfflinePackResultId(UUID packId, int ordinal) {
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
    if (!(o instanceof OfflinePackResultId other)) {
      return false;
    }
    return ordinal == other.ordinal && Objects.equals(packId, other.packId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(packId, ordinal);
  }
}
