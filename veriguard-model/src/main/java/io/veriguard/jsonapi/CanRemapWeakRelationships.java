package io.veriguard.jsonapi;

import java.util.Map;

public interface CanRemapWeakRelationships {
  void remap(Map<String, String> map);
}
