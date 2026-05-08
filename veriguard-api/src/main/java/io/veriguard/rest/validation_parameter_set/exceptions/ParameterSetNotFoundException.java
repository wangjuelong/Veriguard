package io.veriguard.rest.validation_parameter_set.exceptions;

import java.util.UUID;

public class ParameterSetNotFoundException extends RuntimeException {
  public ParameterSetNotFoundException(UUID id) {
    super("ValidationParameterSet not found: " + id);
  }
}
