package io.veriguard.rest.payload.service;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static io.veriguard.rest.payload.PayloadUtils.validateArchitecture;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackPatternRepository;
import io.veriguard.database.repository.DomainRepository;
import io.veriguard.database.repository.PayloadRepository;
import io.veriguard.database.repository.TagRepository;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.payload.PayloadUtils;
import io.veriguard.rest.payload.form.PayloadUpdateInput;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PayloadUpdateService {

  private final PayloadUtils payloadUtils;

  private final PayloadService payloadService;

  private final TagRepository tagRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final DomainRepository domainRepository;
  private final PayloadRepository payloadRepository;
  private final DocumentService documentService;

  @Transactional(rollbackOn = Exception.class)
  public Payload updatePayload(String payloadId, PayloadUpdateInput input) {
    Payload payload =
        this.payloadRepository.findById(payloadId).orElseThrow(ElementNotFoundException::new);
    List<AttackPattern> attackPatterns =
        fromIterable(attackPatternRepository.findAllById(input.getAttackPatternsIds()));
    return update(input, payload, attackPatterns);
  }

  private Payload update(
      PayloadUpdateInput input, Payload existingPayload, List<AttackPattern> attackPatterns) {
    PayloadType payloadType = PayloadType.fromString(existingPayload.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = (Payload) Hibernate.unproxy(existingPayload);
    payloadUtils.copyProperties(input, payload);

    payload.setAttackPatterns(attackPatterns);
    // Somehow, loading tags can create a detached error on detection remediation.
    // Detaching the collection before and reattaching it after bypass the issue
    List<DetectionRemediation> originalDrs = new ArrayList<>(payload.getDetectionRemediations());
    payload.setDetectionRemediations(Collections.emptyList());
    payload.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    payload.setDomains(iterableToSet(domainRepository.findAllById(input.getDomainIds())));
    payload.setDetectionRemediations(originalDrs);

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    Payload saved = payloadRepository.save(payload);
    payloadService.updateInjectorContractsForPayload(saved);
    return saved;
  }
}
