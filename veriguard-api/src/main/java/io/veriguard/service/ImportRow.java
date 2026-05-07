package io.veriguard.service;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.rest.scenario.response.ImportMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ImportRow {
  private AttackChainNodeTime attackChainNodeTime;
  private List<ImportMessage> importMessages = new ArrayList<>();
  private AttackChainNode attackChainNode;
}
