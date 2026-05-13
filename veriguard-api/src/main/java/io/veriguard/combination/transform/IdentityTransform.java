package io.veriguard.combination.transform;

import java.util.Map;
import org.springframework.stereotype.Component;

/** 恒等变换：base payload 原样返回. category=other 的默认占位变换. */
@Component
public class IdentityTransform implements PayloadTransform {

  public static final String TYPE = "identity";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String apply(String basePayload, Map<String, Object> config) {
    if (basePayload == null) {
      throw new IllegalArgumentException("basePayload must not be null");
    }
    return basePayload;
  }
}
