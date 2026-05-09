package io.veriguard.rest.attack_chain_run.form;

import static io.veriguard.config.AppConfig.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class AttackChainRunInput {

  public static final int EXERCISE_NAME_MAX_LENGTH = 255;

  @NotBlank(message = MANDATORY_MESSAGE)
  @Size(max = EXERCISE_NAME_MAX_LENGTH, message = MAX_255_MESSAGE)
  @JsonProperty("attack_chain_run_name")
  private String name;

  @JsonProperty("attack_chain_run_subtitle")
  private String subtitle;

  @Nullable
  @JsonProperty("attack_chain_run_category")
  private String category;

  @Nullable
  @JsonProperty("attack_chain_run_main_focus")
  private String mainFocus;

  @Nullable
  @JsonProperty("attack_chain_run_severity")
  private String severity;

  @Nullable
  @JsonProperty("attack_chain_run_description")
  private String description;

  @JsonProperty("attack_chain_run_tags")
  private List<String> tagIds = new ArrayList<>();

  @Email(message = EMAIL_FORMAT)
  @JsonProperty("attack_chain_run_mail_from")
  private String from;

  @JsonProperty("attack_chain_run_mails_reply_to")
  private List<String> replyTos;

  @JsonProperty("attack_chain_run_message_header")
  private String header;

  @JsonProperty("attack_chain_run_message_footer")
  private String footer;
}
