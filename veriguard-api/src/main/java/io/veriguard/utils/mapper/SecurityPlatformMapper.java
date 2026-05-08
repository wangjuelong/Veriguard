package io.veriguard.utils.mapper;

import io.veriguard.database.model.SecurityPlatform;
import io.veriguard.rest.document.form.RelatedEntityOutput;
import java.util.Set;
import java.util.stream.Collectors;

public final class SecurityPlatformMapper {

  private SecurityPlatformMapper() {}

  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(
      Set<SecurityPlatform> securityPlatforms) {
    return securityPlatforms.stream()
        .map(securityPlatform -> toRelatedEntityOutput(securityPlatform))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(SecurityPlatform securityPlatform) {
    return RelatedEntityOutput.builder()
        .id(securityPlatform.getId())
        .name(securityPlatform.getName())
        .build();
  }
}
