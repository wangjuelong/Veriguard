package io.veriguard.rest.attack_chain.export;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(NON_NULL)
public class AttackChainFileExport {

  @JsonProperty("export_version")
  private int version;

  @JsonProperty("attack_chain_information")
  private AttackChain attackChain;

  @JsonProperty("attack_chain_teams")
  private List<Team> teams = new ArrayList<>();

  @JsonProperty("attack_chain_objectives")
  private List<Objective> objectives = new ArrayList<>();

  @JsonProperty("attack_chain_users")
  private List<User> users = new ArrayList<>();

  @JsonProperty("attack_chain_organizations")
  private List<Organization> organizations = new ArrayList<>();

  @JsonProperty("attack_chain_nodes")
  private List<AttackChainNode> attackChainNodes = new ArrayList<>();

  @JsonProperty("attack_chain_tags")
  private List<Tag> tags = new ArrayList<>();

  @JsonProperty("attack_chain_documents")
  private List<Document> documents = new ArrayList<>();

  @JsonProperty("attack_chain_lessons_categories")
  private List<LessonsCategory> lessonsCategories = new ArrayList<>();

  @JsonProperty("attack_chain_lessons_questions")
  private List<LessonsQuestion> lessonsQuestions = new ArrayList<>();

  @JsonIgnore public static final String SCENARIO_VARIABLES = "scenario_variables";

  @JsonProperty(SCENARIO_VARIABLES)
  private List<Variable> variables;
}
