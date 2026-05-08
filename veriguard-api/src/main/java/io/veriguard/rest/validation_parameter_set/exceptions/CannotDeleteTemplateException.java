package io.veriguard.rest.validation_parameter_set.exceptions;

import java.util.UUID;

public class CannotDeleteTemplateException extends RuntimeException {
  public CannotDeleteTemplateException(UUID id) {
    super("Cannot delete system template ValidationParameterSet: " + id);
  }
}
