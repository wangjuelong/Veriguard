package io.veriguard.rest.attack_chain.form;

import static io.veriguard.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.AttackChain.SEVERITY;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AttackChainInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("attack_chain_name")
  private String name;

  @JsonProperty("attack_chain_description")
  private String description;

  @JsonProperty("attack_chain_subtitle")
  private String subtitle;

  @Nullable
  @JsonProperty("attack_chain_category")
  private String category;

  @Nullable
  @JsonProperty("attack_chain_main_focus")
  private String mainFocus;

  @Nullable
  @JsonProperty("attack_chain_severity")
  private SEVERITY severity;

  @Nullable
  @JsonProperty("attack_chain_external_reference")
  private String externalReference;

  @Nullable
  @JsonProperty("attack_chain_external_url")
  private String externalUrl;

  @JsonProperty("attack_chain_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("attack_chain_mail_from")
  @Email
  private String from;

  @JsonProperty("attack_chain_mails_reply_to")
  private List<String> replyTos = new ArrayList<>();

  @JsonProperty("attack_chain_message_header")
  private String header;

  @JsonProperty("attack_chain_message_footer")
  private String footer;

  @JsonProperty("attack_chain_custom_dashboard")
  private String customDashboard;
}
