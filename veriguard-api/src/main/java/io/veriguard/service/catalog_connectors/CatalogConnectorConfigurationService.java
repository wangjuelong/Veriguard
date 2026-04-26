package io.veriguard.service.catalog_connectors;

import io.veriguard.database.repository.CatalogConnectorConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CatalogConnectorConfigurationService {
  private final CatalogConnectorConfigurationRepository catalogConnectorConfigurationRepository;
}
