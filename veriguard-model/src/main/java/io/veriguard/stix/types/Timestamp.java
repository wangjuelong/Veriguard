package io.veriguard.stix.types;

import java.time.Instant;

public class Timestamp extends BaseType<Instant> {
  public Timestamp(Instant value) {
    super(value);
  }
}
