package io.veriguard.injectors.email.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.model.attack_chain_node.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailContent {

  @JsonProperty("smtp_profile_id")
  private String smtpProfileId;

  @JsonProperty("subject")
  private String subject;

  @JsonProperty("body_text")
  private String bodyText;

  @JsonProperty("body_html")
  private String bodyHtml;

  @JsonProperty("from_alias")
  private String fromAlias;

  @JsonProperty("attachments")
  private List<String> attachments = new ArrayList<>();

  @JsonProperty("inline_links")
  private List<String> inlineLinks = new ArrayList<>();

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
