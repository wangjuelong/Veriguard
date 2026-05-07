package io.veriguard.database.raw;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.Document;
import io.veriguard.database.model.Tag;
import java.util.List;
import lombok.Data;

@Data
public class RawPaginationDocument {

  String document_id;
  String document_name;
  String document_description;
  List<String> document_attackChainRuns;
  List<String> document_attackChains;
  String document_type;
  List<String> document_tags;
  boolean document_can_be_deleted = true;

  public RawPaginationDocument(final Document document) {
    this.document_id = document.getId();
    this.document_name = document.getName();
    this.document_description = document.getDescription();
    this.document_attackChainRuns =
        document.getAttackChainRuns().stream().map(AttackChainRun::getId).toList();
    this.document_attackChains =
        document.getAttackChains().stream().map(AttackChain::getId).toList();
    this.document_type = document.getType();
    this.document_tags = document.getTags().stream().map(Tag::getId).toList();
  }
}
