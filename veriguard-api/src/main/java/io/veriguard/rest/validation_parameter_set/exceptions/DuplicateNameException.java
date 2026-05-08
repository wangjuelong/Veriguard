package io.veriguard.rest.validation_parameter_set.exceptions;

public class DuplicateNameException extends RuntimeException {
  public DuplicateNameException(String name) {
    super("ValidationParameterSet name already exists: " + name);
  }
}
