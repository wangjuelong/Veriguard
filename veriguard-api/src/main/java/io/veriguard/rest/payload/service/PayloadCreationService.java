package io.veriguard.rest.payload.service;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static io.veriguard.rest.payload.PayloadUtils.validateArchitecture;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackPatternRepository;
import io.veriguard.database.repository.PayloadRepository;
import io.veriguard.database.repository.TagRepository;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.domain.DomainService;
import io.veriguard.rest.payload.PayloadUtils;
import io.veriguard.rest.payload.form.PayloadCreateInput;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class PayloadCreationService {

  private final PayloadUtils payloadUtils;

  private final PayloadService payloadService;

  private final TagRepository tagRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final PayloadRepository payloadRepository;
  private final DocumentService documentService;
  private final DomainService domainService;

  @Transactional(rollbackOn = Exception.class)
  public Payload createPayload(PayloadCreateInput input) {
    List<AttackPattern> attackPatterns =
        fromIterable(attackPatternRepository.findAllById(input.getAttackPatternsIds()));
    return create(input, attackPatterns);
  }

  private Payload create(PayloadCreateInput input, List<AttackPattern> attackPatterns) {
    PayloadType payloadType = PayloadType.fromString(input.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = payloadType.getPayloadSupplier().get();
    payloadUtils.copyProperties(input, payload);

    payload.setAttackPatterns(attackPatterns);
    payload.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    payload.setDomains(iterableToSet(domainService.findAllById(input.getDomainIds())));

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    Payload saved = payloadRepository.save(payload);
    payloadService.updateNodeContractsForPayload(saved);
    return saved;
  }
}
