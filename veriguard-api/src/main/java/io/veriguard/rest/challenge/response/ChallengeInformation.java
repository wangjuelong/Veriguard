package io.veriguard.rest.challenge.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.Challenge;
import io.veriguard.database.model.InjectExpectation;
import lombok.Getter;

@Getter
public class ChallengeInformation {

  @JsonProperty("challenge_detail")
  private final PublicChallenge challenge;

  @JsonProperty("challenge_expectation")
  private final InjectExpectation expectation;

  @JsonProperty("challenge_attempt")
  private final int attempt;

  public ChallengeInformation(Challenge challenge, InjectExpectation expectation, int attempt) {
    this.challenge = new PublicChallenge(challenge);
    this.expectation = expectation;
    this.attempt = attempt;
  }
}
