package io.veriguard.rest.exception;

public class UnprocessableContentException extends Exception {
  public UnprocessableContentException() {
    super();
  }

  public UnprocessableContentException(String errorMessage) {
    super(errorMessage);
  }
}
