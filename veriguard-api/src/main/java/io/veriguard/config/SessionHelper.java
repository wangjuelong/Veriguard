package io.veriguard.config;

import org.springframework.security.core.context.SecurityContextHolder;

public class SessionHelper {

  private SessionHelper() {}

  public static final String ANONYMOUS_USER = "anonymousUser";

  public static VeriguardPrincipal currentUser() {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      return new VeriguardAnonymous();
    }
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (ANONYMOUS_USER.equals(principal)) {
      return new VeriguardAnonymous();
    }
    return (VeriguardPrincipal) principal;
  }
}
