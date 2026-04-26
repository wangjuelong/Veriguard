package io.veriguard.security.token;

import io.jsonwebtoken.JwtException;
import io.veriguard.opencti.errors.ConnectorError;

public interface ExtractorBase {
  String extractToken(String value) throws ConnectorError, JwtException;
}
