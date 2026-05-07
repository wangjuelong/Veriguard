package io.veriguard.rest.attack_chain_node.service;

import static io.veriguard.service.ImportService.EXPORT_ENTRY_ATTACHMENT;
import static io.veriguard.service.ImportService.EXPORT_ENTRY_EXERCISE;
import static java.time.Instant.now;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.Document;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.repository.DocumentRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.attack_chain_run.exports.ExportOptions;
import io.veriguard.rest.attack_chain_node.exports.AttackChainNodesFileExport;
import io.veriguard.service.FileService;
import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AttackChainNodeExportService {
  @Resource protected ObjectMapper mapper;
  @Resource private DocumentRepository documentRepository;
  @Resource private FileService fileService;

  public String getZipFileName(int exportOptionsMask) {
    String infos =
        "("
            + (ExportOptions.has(ExportOptions.WITH_TEAMS, exportOptionsMask)
                ? "with_teams"
                : "no_teams")
            + " & "
            + (ExportOptions.has(ExportOptions.WITH_PLAYERS, exportOptionsMask)
                ? "with_players"
                : "no_players")
            + " & "
            + (ExportOptions.has(ExportOptions.WITH_VARIABLE_VALUES, exportOptionsMask)
                ? "with_variable_values"
                : "no_variable_values")
            + ")";
    return ("injects_" + now().toString()) + "_" + infos + ".zip";
  }

  public byte[] exportAttackChainNodesToZip(List<AttackChainNode> attackChainNodes, int exportOptionsMask) throws IOException {
    ObjectMapper objectMapper = mapper.copy();

    AttackChainNodesFileExport importExport =
        AttackChainNodesFileExport.fromAttackChainNodes(attackChainNodes, objectMapper).withOptions(exportOptionsMask);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ZipOutputStream zipExport = new ZipOutputStream(outputStream);
    ZipEntry zipEntry = new ZipEntry("injects.json");
    zipEntry.setComment(EXPORT_ENTRY_EXERCISE);
    zipExport.putNextEntry(zipEntry);
    zipExport.write(
        importExport
            .getObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsBytes(importExport));
    zipExport.closeEntry();
    // Add the actual files for the documents
    importExport.getAllDocumentIds().stream()
        .distinct()
        .forEach(
            docId -> {
              Document doc =
                  documentRepository.findById(docId).orElseThrow(ElementNotFoundException::new);
              Optional<InputStream> docStream = fileService.getFile(doc);
              if (docStream.isPresent()) {
                try {
                  ZipEntry zipDoc = new ZipEntry(doc.getTarget());
                  zipDoc.setComment(EXPORT_ENTRY_ATTACHMENT);
                  byte[] data = docStream.get().readAllBytes();
                  zipExport.putNextEntry(zipDoc);
                  zipExport.write(data);
                  zipExport.closeEntry();
                } catch (IOException e) {
                  log.error(e.getMessage(), e);
                }
              }
            });
    zipExport.finish();
    zipExport.close();

    return outputStream.toByteArray();
  }
}
