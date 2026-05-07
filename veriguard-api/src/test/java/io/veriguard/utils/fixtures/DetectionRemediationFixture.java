package io.veriguard.utils.fixtures;

import io.veriguard.database.model.DetectionRemediation;
import io.veriguard.jsonapi.Relationship;
import io.veriguard.jsonapi.ResourceIdentifier;
import io.veriguard.jsonapi.ResourceObject;
import java.util.HashMap;
import java.util.Map;

public class DetectionRemediationFixture {

  public static DetectionRemediation createDefaultDetectionRemediation() {
    DetectionRemediation detectionRemediation = new DetectionRemediation();
    detectionRemediation.setValues("I have a rule");
    detectionRemediation.setAuthorRule(DetectionRemediation.AUTHOR_RULE.HUMAN);
    return detectionRemediation;
  }

  public static ResourceObject buildDetectionRemediationResource(
      String remediationId, String values, String collectorType, String collectorId) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("detection_remediation_values", values);
    attributes.put("author_rule", "HUMAN");

    Map<String, Relationship> relationships = new HashMap<>();
    if (collectorType != null && collectorId != null) {
      relationships.put("detection_remediation_collector_type",
          new Relationship(new ResourceIdentifier(collectorId, collectorType)));
    }

    return new ResourceObject(remediationId, "detection_remediations", attributes, relationships);
  }
}
