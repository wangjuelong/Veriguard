package io.veriguard.executors;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.utils.InjectionUtils.isInInjectableRange;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.model.ExecutionProcess;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.springframework.transaction.annotation.Transactional;

public abstract class NodeExecutor {

  protected final NodeExecutorContext context;

  protected NodeExecutor(NodeExecutorContext context) {
    this.context = context;
  }

  public abstract ExecutionProcess process(Execution execution, ExecutableNode injection)
      throws Exception;

  public StatusPayload getPayloadOutput(String externalId) {
    return null;
  }

  @Transactional
  public Execution execute(ExecutableNode executableAttackChainNode) {
    Execution execution = new Execution(executableAttackChainNode.isRuntime());
    try {
      boolean isScheduledAttackChainNode = !executableAttackChainNode.isDirect();
      // If empty content, attackChainNode must be rejected
      if (executableAttackChainNode.getInjection().getAttackChainNode().getContent() == null) {
        throw new UnsupportedOperationException("Inject is empty");
      }
      // If attackChainNode is too old, reject the execution
      if (isScheduledAttackChainNode
          && !isInInjectableRange(executableAttackChainNode.getInjection())) {
        throw new UnsupportedOperationException(
            "Inject is now too old for execution: id "
                + executableAttackChainNode.getInjection().getId()
                + ", launch date "
                + executableAttackChainNode.getInjection().getDate()
                + ", now date "
                + Instant.now());
      }
      // Process the execution
      ExecutionProcess executionProcess = process(execution, executableAttackChainNode);
      execution.setAsync(executionProcess.isAsync());
    } catch (Exception e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
    } finally {
      execution.stop();
    }
    return execution;
  }

  public Execution executeInjection(ExecutableNode executableAttackChainNode) {
    return execute(executableAttackChainNode);
  }

  // region utils

  public <T> T contentConvert(
      @NotNull final ExecutableNode injection, @NotNull final Class<T> converter) throws Exception {
    AttackChainNode attackChainNode = injection.getInjection().getAttackChainNode();
    ObjectNode content = attackChainNode.getContent();
    return this.context.getMapper().treeToValue(content, converter);
  }

  public List<DataAttachment> resolveAttachments(
      Execution execution, ExecutableNode injection, List<Document> documents) {
    List<DataAttachment> resolved = new ArrayList<>();
    // Add attachments from direct configuration
    injection
        .getDirectAttachments()
        .forEach(
            doc -> {
              try {
                byte[] content = IOUtils.toByteArray(doc.getInputStream());
                resolved.add(
                    new DataAttachment(
                        doc.getName(), doc.getOriginalFilename(), content, doc.getContentType()));
              } catch (Exception e) {
                String message = "Error getting direct attachment " + doc.getName();
                execution.addTrace(getNewErrorTrace(message, ExecutionTraceAction.EXECUTION));
              }
            });
    // Add attachments from configuration
    documents.forEach(
        attachment -> {
          String documentId = attachment.getId();
          Optional<Document> askedDocument =
              this.context.getDocumentRepository().findById(documentId);
          try {
            Document doc = askedDocument.orElseThrow();
            InputStream fileInputStream = this.context.getFileService().getFile(doc).orElseThrow();
            byte[] content = IOUtils.toByteArray(fileInputStream);
            resolved.add(new DataAttachment(documentId, doc.getName(), content, doc.getType()));
          } catch (Exception e) {
            // Can't fetch the attachments, ignore
            String docInfo = askedDocument.map(Document::getName).orElse(documentId);
            String message = "Error getting doc attachment " + docInfo;
            execution.addTrace(getNewErrorTrace(message, ExecutionTraceAction.EXECUTION));
          }
        });
    return resolved;
  }
  // endregion

}
