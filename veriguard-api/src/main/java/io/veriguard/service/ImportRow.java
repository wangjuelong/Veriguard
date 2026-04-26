package io.veriguard.service;

import io.veriguard.database.model.Inject;
import io.veriguard.rest.scenario.response.ImportMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ImportRow {
  private InjectTime injectTime;
  private List<ImportMessage> importMessages = new ArrayList<>();
  private Inject inject;
}
