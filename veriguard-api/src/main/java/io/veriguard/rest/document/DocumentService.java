package io.veriguard.rest.document;

import io.veriguard.database.model.Document;
import io.veriguard.database.raw.RawDocument;
import io.veriguard.database.repository.DocumentRepository;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.service.FileService;
import jakarta.validation.constraints.NotBlank;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final FileService fileService;

  // -- CRUD --

  public Document document(@NotBlank final String documentId) {
    return documentRepository
        .findById(documentId)
        .orElseThrow(() -> new ElementNotFoundException("Document not found"));
  }

  public void deleteDocument(String documentId) {
    Document document = document(documentId); // fetch or throw if not found

    boolean isUsedInFileDrop =
        document.getPayloadsByFileDrop() != null && !document.getPayloadsByFileDrop().isEmpty();
    boolean isUsedInExecutable =
        document.getPayloadsByExecutableFile() != null
            && !document.getPayloadsByExecutableFile().isEmpty();

    if (isUsedInFileDrop || isUsedInExecutable) {
      throw new BadRequestException(
          "Document is still in use for some payloads and cannot be deleted.");
    }

    List<Document> documents = documentRepository.removeById(documentId);

    // Remove document from minio
    documents.forEach(
        documentToRemove -> {
          try {
            fileService.deleteFile(documentToRemove.getTarget());
          } catch (Exception e) {
            log.warn(
                "File already removed or not found in minio: {}", documentToRemove.getTarget(), e);
          }
        });
  }

  public static String encodeFileName(String name) {
    return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
  }

  public List<Document> documentsForScenario(String scenarioId) {
    return this.documentRepository.findAllDistinctByScenarioId(scenarioId);
  }

  public List<Document> documentsForSimulation(String simulationId) {
    return this.documentRepository.findAllDistinctBySimulationId(simulationId);
  }

  public List<RawDocument> documentsForSecurityPlatform(@NotBlank String securityPlatformId) {
    return this.documentRepository.rawAllDocumentsBySecurityPlatformId(securityPlatformId);
  }

  public List<RawDocument> documentsForPayload(@NotBlank String payloadId) {
    return this.documentRepository.rawAllDocumentsByPayloadId(payloadId);
  }
}
