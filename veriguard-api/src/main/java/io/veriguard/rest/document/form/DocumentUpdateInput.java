package io.veriguard.rest.document.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DocumentUpdateInput {

  @JsonProperty("document_description")
  private String description;

  @JsonProperty("document_tags")
  private List<String> tagIds;

  @JsonProperty("document_exercises")
  private List<String> attackChainRunIds = new ArrayList<>();

  @JsonProperty("document_scenarios")
  private List<String> attackChainIds = new ArrayList<>();
}
