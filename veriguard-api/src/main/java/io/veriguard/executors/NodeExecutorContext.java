package io.veriguard.executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.config.VeriguardConfig;
import io.veriguard.database.repository.DocumentRepository;
import io.veriguard.service.FileService;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class NodeExecutorContext {
  private final VeriguardConfig veriguardConfig;
  private final ObjectMapper mapper;
  private final FileService fileService;
  private final DocumentRepository documentRepository;

  public NodeExecutorContext(
      VeriguardConfig veriguardConfig,
      ObjectMapper mapper,
      FileService fileService,
      DocumentRepository documentRepository) {
    this.veriguardConfig = veriguardConfig;
    this.mapper = mapper;
    this.fileService = fileService;
    this.documentRepository = documentRepository;
  }
}
