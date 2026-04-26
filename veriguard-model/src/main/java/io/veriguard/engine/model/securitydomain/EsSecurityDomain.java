package io.veriguard.engine.model.securitydomain;

import io.veriguard.annotation.EsQueryable;
import io.veriguard.annotation.Indexable;
import io.veriguard.annotation.Queryable;
import io.veriguard.engine.model.EsBase;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "domain", label = "Domain")
public class EsSecurityDomain extends EsBase {

  @Queryable(label = "domain color", filterable = true)
  @EsQueryable(keyword = true)
  private String domain_color;
}
