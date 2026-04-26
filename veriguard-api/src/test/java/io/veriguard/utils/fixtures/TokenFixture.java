package io.veriguard.utils.fixtures;

import io.veriguard.database.model.Token;
import java.time.Instant;

public class TokenFixture {
  public static Token getTokenWithValue(String value) {
    Token token = new Token();
    token.setValue(value);
    token.setCreated(Instant.now());
    return token;
  }
}
