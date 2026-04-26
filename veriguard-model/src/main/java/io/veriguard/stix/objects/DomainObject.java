package io.veriguard.stix.objects;

import io.veriguard.stix.types.BaseType;
import java.util.Map;

public class DomainObject extends ObjectBase {
  public DomainObject(Map<String, BaseType<?>> properties) {
    super(properties);
  }
}
