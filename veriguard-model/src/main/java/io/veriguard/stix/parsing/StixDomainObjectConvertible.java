package io.veriguard.stix.parsing;

import io.veriguard.stix.objects.DomainObject;

public interface StixDomainObjectConvertible {
  DomainObject toStixDomainObject();
}
