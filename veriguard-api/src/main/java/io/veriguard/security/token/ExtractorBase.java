package io.veriguard.security.token;

import io.jsonwebtoken.JwtException;
import io.veriguard.security.exception.AuthenticationException;

public interface ExtractorBase {
  String extractToken(String value) throws AuthenticationException, JwtException;
}
