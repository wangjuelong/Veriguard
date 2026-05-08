package io.veriguard.rest.validation_parameter_set.exceptions;

import java.util.UUID;

public class CannotEditTemplateException extends RuntimeException {
  public CannotEditTemplateException(UUID id) {
    super("Cannot edit system template ValidationParameterSet: " + id);
  }
}
