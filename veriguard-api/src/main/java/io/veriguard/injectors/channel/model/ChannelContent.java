package io.veriguard.injectors.channel.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.injectors.email.model.EmailContent;
import io.veriguard.model.inject.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ChannelContent extends EmailContent {

  @JsonProperty("articles")
  private List<String> articles = new ArrayList<>();

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();

  @JsonProperty("emailing")
  private boolean emailing;
}
