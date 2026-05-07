package io.veriguard.rest.document;

import static io.veriguard.config.VeriguardAnonymous.ANONYMOUS;
import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static io.veriguard.utils.mapper.DocumentMapper.toDocumentRelationsOutput;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawDocument;
import io.veriguard.database.raw.RawPaginationDocument;
import io.veriguard.database.repository.*;
import io.veriguard.rest.document.form.DocumentCreateInput;
import io.veriguard.rest.document.form.DocumentRelationsOutput;
import io.veriguard.rest.document.form.DocumentTagUpdateInput;
import io.veriguard.rest.document.form.DocumentUpdateInput;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.service.FileService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class DocumentApi extends RestBehavior {

  public static final String DOCUMENT_API = "/api/documents";
  private final TagRepository tagRepository;
  private final DocumentRepository documentRepository;
  private final AttackChainRunRepository attackChainRunRepository;
  private final AttackChainRepository attackChainRepository;
  private final UserRepository userRepository;
  private final NodeExecutorRepository nodeExecutorRepository;
  private final CollectorRepository collectorRepository;
  private final SecurityPlatformRepository securityPlatformRepository;

  private final DocumentService documentService;
  private final FileService fileService;
  private final AttackChainNodeService attackChainNodeService;

  @PostMapping(DOCUMENT_API)
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.DOCUMENT)
  @Transactional(rollbackOn = Exception.class)
  public Document uploadDocument(
      @Valid @RequestPart("input") DocumentCreateInput input,
      @RequestPart("file") MultipartFile file)
      throws Exception {
    String extension = FilenameUtils.getExtension(file.getOriginalFilename());
    String fileTarget = DigestUtils.md5Hex(file.getInputStream()) + "." + extension;
    Optional<Document> targetDocument = documentRepository.findByTarget(fileTarget);
    if (targetDocument.isPresent()) {
      Document document = targetDocument.get();
      // Compute attackChainRuns
      if (!document.getAttackChainRuns().isEmpty()) {
        Set<AttackChainRun> attackChainRuns = new HashSet<>(document.getAttackChainRuns());
        List<AttackChainRun> inputAttackChainRuns =
            fromIterable(attackChainRunRepository.findAllById(input.getAttackChainRunIds()));
        attackChainRuns.addAll(inputAttackChainRuns);
        document.setAttackChainRuns(attackChainRuns);
      }
      // Compute attackChains
      if (!document.getAttackChains().isEmpty()) {
        Set<AttackChain> attackChains = new HashSet<>(document.getAttackChains());
        List<AttackChain> inputAttackChains =
            fromIterable(attackChainRepository.findAllById(input.getAttackChainIds()));
        attackChains.addAll(inputAttackChains);
        document.setAttackChains(attackChains);
      }
      // Compute tags
      Set<Tag> tags = new HashSet<>(document.getTags());
      List<Tag> inputTags = fromIterable(tagRepository.findAllById(input.getTagIds()));
      tags.addAll(inputTags);
      document.setTags(tags);
      return documentRepository.save(document);
    } else {
      fileService.uploadFile(fileTarget, file);
      Document document = new Document();
      document.setTarget(fileTarget);
      document.setName(file.getOriginalFilename());
      document.setDescription(input.getDescription());
      if (!input.getAttackChainRunIds().isEmpty()) {
        document.setAttackChainRuns(
            iterableToSet(attackChainRunRepository.findAllById(input.getAttackChainRunIds())));
      }
      if (!input.getAttackChainIds().isEmpty()) {
        document.setAttackChains(
            iterableToSet(attackChainRepository.findAllById(input.getAttackChainIds())));
      }
      document.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
      document.setType(file.getContentType());
      return documentRepository.save(document);
    }
  }

  @PostMapping(DOCUMENT_API + "/upsert")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.DOCUMENT)
  @Transactional(rollbackOn = Exception.class)
  public Document upsertDocument(
      @Valid @RequestPart("input") DocumentCreateInput input,
      @RequestPart("file") MultipartFile file)
      throws Exception {
    String extension = FilenameUtils.getExtension(file.getOriginalFilename());
    String fileTarget = DigestUtils.md5Hex(file.getInputStream()) + "." + extension;
    Optional<Document> targetDocument = documentRepository.findByTarget(fileTarget);
    // Document already exists by hash
    if (targetDocument.isPresent()) {
      Document document = targetDocument.get();
      // Compute attackChainRuns
      if (!document.getAttackChainRuns().isEmpty()) {
        Set<AttackChainRun> attackChainRuns = new HashSet<>(document.getAttackChainRuns());
        List<AttackChainRun> inputAttackChainRuns =
            fromIterable(attackChainRunRepository.findAllById(input.getAttackChainRunIds()));
        attackChainRuns.addAll(inputAttackChainRuns);
        document.setAttackChainRuns(attackChainRuns);
      }
      // Compute attackChains
      if (!document.getAttackChains().isEmpty()) {
        Set<AttackChain> attackChains = new HashSet<>(document.getAttackChains());
        List<AttackChain> inputAttackChains =
            fromIterable(attackChainRepository.findAllById(input.getAttackChainIds()));
        attackChains.addAll(inputAttackChains);
        document.setAttackChains(attackChains);
      }
      // Compute tags
      Set<Tag> tags = new HashSet<>(document.getTags());
      List<Tag> inputTags = fromIterable(tagRepository.findAllById(input.getTagIds()));
      tags.addAll(inputTags);
      document.setTags(tags);
      return documentRepository.save(document);
    } else {
      Optional<Document> existingDocument =
          documentRepository.findByName(file.getOriginalFilename());
      if (existingDocument.isPresent()) {
        Document document = existingDocument.get();
        // Update doc
        fileService.uploadFile(fileTarget, file);
        document.setDescription(input.getDescription());

        // Compute attackChainRuns
        if (!document.getAttackChainRuns().isEmpty()) {
          Set<AttackChainRun> attackChainRuns = new HashSet<>(document.getAttackChainRuns());
          List<AttackChainRun> inputAttackChainRuns =
              fromIterable(attackChainRunRepository.findAllById(input.getAttackChainRunIds()));
          attackChainRuns.addAll(inputAttackChainRuns);
          document.setAttackChainRuns(attackChainRuns);
        }
        // Compute attackChains
        if (!document.getAttackChains().isEmpty()) {
          Set<AttackChain> attackChains = new HashSet<>(document.getAttackChains());
          List<AttackChain> inputAttackChains =
              fromIterable(attackChainRepository.findAllById(input.getAttackChainIds()));
          attackChains.addAll(inputAttackChains);
          document.setAttackChains(attackChains);
        }
        // Compute tags
        Set<Tag> tags = new HashSet<>(document.getTags());
        List<Tag> inputTags = fromIterable(tagRepository.findAllById(input.getTagIds()));
        tags.addAll(inputTags);
        document.setTags(tags);
        return documentRepository.save(document);
      } else {
        fileService.uploadFile(fileTarget, file);
        Document document = new Document();
        document.setTarget(fileTarget);
        document.setName(file.getOriginalFilename());
        document.setDescription(input.getDescription());
        if (!input.getAttackChainRunIds().isEmpty()) {
          document.setAttackChainRuns(
              iterableToSet(attackChainRunRepository.findAllById(input.getAttackChainRunIds())));
        }
        if (!input.getAttackChainIds().isEmpty()) {
          document.setAttackChains(
              iterableToSet(attackChainRepository.findAllById(input.getAttackChainIds())));
        }
        document.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
        document.setType(file.getContentType());
        return documentRepository.save(document);
      }
    }
  }

  @GetMapping("/api/documents")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOCUMENT)
  public List<RawDocument> documents() {
    return documentRepository.rawAllDocuments();
  }

  @PostMapping(DOCUMENT_API + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOCUMENT)
  public Page<RawPaginationDocument> searchDocuments(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    List<Document> securityPlatformLogos = securityPlatformRepository.securityPlatformLogo();
    return buildPaginationJPA(
            (Specification<Document> specification, Pageable pageable) ->
                this.documentRepository.findAll(specification, pageable),
            searchPaginationInput,
            Document.class)
        .map(
            (document) -> {
              var rawPaginationDocument = new RawPaginationDocument(document);
              rawPaginationDocument.setDocument_can_be_deleted(
                  !securityPlatformLogos.contains(document));
              return rawPaginationDocument;
            });
  }

  @GetMapping(DOCUMENT_API + "/{documentId}")
  @RBAC(
      resourceId = "#documentId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOCUMENT)
  public Document document(@PathVariable String documentId) {
    return documentRepository
        .findById(documentId)
        .orElseThrow(() -> new ElementNotFoundException("Document not found"));
  }

  @GetMapping(DOCUMENT_API + "/{documentId}/tags")
  @RBAC(
      resourceId = "#documentId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOCUMENT)
  public Set<Tag> documentTags(@PathVariable String documentId) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    return document.getTags();
  }

  @PutMapping(DOCUMENT_API + "/{documentId}/tags")
  @RBAC(
      resourceId = "#documentId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.DOCUMENT)
  public Document documentTags(
      @PathVariable String documentId, @RequestBody DocumentTagUpdateInput input) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    document.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return documentRepository.save(document);
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(DOCUMENT_API + "/{documentId}")
  @RBAC(
      resourceId = "#documentId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.DOCUMENT)
  public Document updateDocumentInformation(
      @PathVariable String documentId, @Valid @RequestBody DocumentUpdateInput input) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    document.setUpdateAttributes(input);
    document.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));

    // Get removed attackChainRuns
    Stream<String> askAttackChainRunIdsStream =
        document.getAttackChainRuns().stream()
            .filter(
                attackChainRun ->
                    !attackChainRun.isUserHasAccess(
                        userRepository
                            .findById(currentUser().getId())
                            .orElseThrow(
                                () -> new ElementNotFoundException("Current user not found"))))
            .map(AttackChainRun::getId);
    List<String> askAttackChainRunIds =
        Stream.concat(askAttackChainRunIdsStream, input.getAttackChainRunIds().stream()).distinct().toList();
    List<AttackChainRun> removedAttackChainRuns =
        document.getAttackChainRuns().stream()
            .filter(attackChainRun -> !askAttackChainRunIds.contains(attackChainRun.getId()))
            .toList();
    document.setAttackChainRuns(iterableToSet(attackChainRunRepository.findAllById(askAttackChainRunIds)));
    // In case of attackChainRun removal, all attackChainNode doc attachment for attackChainRun
    removedAttackChainRuns.forEach(
        attackChainRun -> attackChainNodeService.cleanAttackChainNodesDocAttackChainRun(attackChainRun.getId(), documentId));

    // Get removed attackChains
    Stream<String> askAttackChainIdsStream =
        document.getAttackChains().stream()
            .filter(
                attackChain ->
                    !attackChain.isUserHasAccess(
                        userRepository
                            .findById(currentUser().getId())
                            .orElseThrow(
                                () -> new ElementNotFoundException("Current user not found"))))
            .map(AttackChain::getId);
    List<String> askAttackChainIds =
        Stream.concat(askAttackChainIdsStream, input.getAttackChainIds().stream()).distinct().toList();
    List<AttackChain> removedAttackChains =
        document.getAttackChains().stream()
            .filter(attackChain -> !askAttackChainIds.contains(attackChain.getId()))
            .toList();
    document.setAttackChains(iterableToSet(attackChainRepository.findAllById(askAttackChainIds)));
    // In case of attackChain removal, all attackChainNode doc attachment for attackChain
    removedAttackChains.forEach(
        attackChain -> attackChainNodeService.cleanAttackChainNodesDocAttackChain(attackChain.getId(), documentId));

    // Save and return
    return documentRepository.save(document);
  }

  @GetMapping(DOCUMENT_API + "/{documentId}/file")
  @RBAC(
      resourceId = "#documentId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOCUMENT)
  public void downloadDocument(@PathVariable String documentId, HttpServletResponse response)
      throws IOException {
    Document document = documentService.document(documentId);

    String encodedFilename = DocumentService.encodeFileName(document.getName());

    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encodedFilename);
    response.addHeader(HttpHeaders.CONTENT_TYPE, document.getType());
    response.setStatus(HttpServletResponse.SC_OK);
    try (InputStream fileStream =
        fileService
            .getFile(document)
            .orElseThrow(() -> new ElementNotFoundException("File not found"))) {
      fileStream.transferTo(response.getOutputStream());
    }
  }

  @GetMapping(value = "/api/images/injectors/{injectorType}", produces = MediaType.IMAGE_PNG_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getNodeExecutorImage(@PathVariable String nodeExecutorType)
      throws IOException {
    Optional<InputStream> fileStream = fileService.getNodeExecutorImage(nodeExecutorType);
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  @GetMapping(value = "/api/images/injectors/id/{injectorId}", produces = MediaType.IMAGE_PNG_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getNodeExecutorImageFromId(
      @PathVariable String nodeExecutorId) throws IOException {
    NodeExecutor nodeExecutor =
        this.nodeExecutorRepository
            .findById(nodeExecutorId)
            .orElseThrow(() -> new ElementNotFoundException("Injector not found"));
    Optional<InputStream> fileStream = fileService.getNodeExecutorImage(nodeExecutor.getType());
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  @GetMapping(
      value = "/api/images/collectors/{collectorType}",
      produces = MediaType.IMAGE_PNG_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getCollectorImage(@PathVariable String collectorType)
      throws IOException {
    Optional<InputStream> fileStream = fileService.getCollectorImage(collectorType);
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  public void downloadCollectorImage(
      @PathVariable String collectorType, HttpServletResponse response) throws IOException {
    response.addHeader(
        HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + collectorType + ".png");
    response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE);
    response.setStatus(HttpServletResponse.SC_OK);
    try (InputStream fileStream =
        fileService
            .getCollectorImage(collectorType)
            .orElseThrow(() -> new ElementNotFoundException("File not found"))) {
      fileStream.transferTo(response.getOutputStream());
    }
  }

  @GetMapping(
      value = "/api/images/collectors/id/{collectorId}",
      produces = MediaType.IMAGE_PNG_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getCollectorImageFromId(
      @PathVariable String collectorId) throws IOException {
    Collector collector =
        this.collectorRepository
            .findById(collectorId)
            .orElseThrow(() -> new ElementNotFoundException("Collector not found"));
    Optional<InputStream> fileStream = fileService.getCollectorImage(collector.getType());
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  @GetMapping(value = "/api/images/security_platforms/id/{assetId}/{theme}")
  @RBAC(skipRBAC = true)
  public void getSecurityPlatformImageFromId(
      @PathVariable String assetId, @PathVariable String theme, HttpServletResponse response)
      throws IOException {
    SecurityPlatform securityPlatform =
        this.securityPlatformRepository
            .findById(assetId)
            .orElseThrow(() -> new ElementNotFoundException("Security platform not found"));
    if (theme.equals("dark") && securityPlatform.getLogoDark() != null) {
      downloadDocument(securityPlatform.getLogoDark().getId(), response);
    } else if (securityPlatform.getLogoLight() != null) {
      downloadDocument(securityPlatform.getLogoLight().getId(), response);
    } else {
      downloadCollectorImage("veriguard_fake_detector", response);
    }
  }

  @GetMapping(
      value = "/api/images/executors/icons/{executorId}",
      produces = MediaType.IMAGE_PNG_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getExecutorIconImage(@PathVariable String executorId)
      throws IOException {
    Optional<InputStream> fileStream = fileService.getExecutorIconImage(executorId);
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  @GetMapping(
      value = "/api/images/executors/banners/{executorId}",
      produces = MediaType.IMAGE_PNG_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getExecutorBannerImage(
      @PathVariable String executorId) throws IOException {
    Optional<InputStream> fileStream = fileService.getExecutorBannerImage(executorId);
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  private List<Document> getAttackChainRunPlayerDocuments(AttackChainRun attackChainRun) {
    return attackChainRun.getAttackChainNodes().stream()
        .flatMap(attackChainNode -> attackChainNode.getDocuments().stream().map(d -> d.getDocument()))
        .distinct()
        .toList();
  }

  private List<Document> getAttackChainPlayerDocuments(AttackChain attackChain) {
    return attackChain.getAttackChainNodes().stream()
        .flatMap(attackChainNode -> attackChainNode.getDocuments().stream().map(d -> d.getDocument()))
        .distinct()
        .toList();
  }

  @LogExecutionTime
  @Operation(summary = "Fetch the entities related to this document id")
  @GetMapping(DOCUMENT_API + "/{documentId}/relations")
  @RBAC(
      resourceId = "#documentId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOCUMENT)
  public DocumentRelationsOutput getDocumentRelations(@PathVariable String documentId) {
    return toDocumentRelationsOutput(documentService.document(documentId));
  }

  @Transactional(rollbackOn = Exception.class)
  @DeleteMapping(DOCUMENT_API + "/{documentId}")
  @RBAC(
      resourceId = "#documentId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.DOCUMENT)
  public void deleteDocument(@PathVariable String documentId) {
    documentService.deleteDocument(documentId);
  }

  // -- EXERCISE & SENARIO--
  @GetMapping("/api/player/{exerciseOrScenarioId}/documents")
  @RBAC(skipRBAC = true)
  public List<Document> playerDocuments(
      @PathVariable String attackChainRunOrAttackChainId, @RequestParam Optional<String> userId) {
    Optional<AttackChainRun> attackChainRunOpt = this.attackChainRunRepository.findById(attackChainRunOrAttackChainId);
    Optional<AttackChain> attackChainOpt = this.attackChainRepository.findById(attackChainRunOrAttackChainId);

    final User user = impersonateUser(userRepository, userId);
    if (user.getId().equals(ANONYMOUS)) {
      throw new UnsupportedOperationException("User must be logged or dynamic player is required");
    }

    if (attackChainRunOpt.isPresent()) {
      if (!attackChainRunOpt.get().isUserHasAccess(user)
          && !attackChainRunOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      return getAttackChainRunPlayerDocuments(attackChainRunOpt.get());
    } else if (attackChainOpt.isPresent()) {
      if (!attackChainOpt.get().isUserHasAccess(user)
          && !attackChainOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      return getAttackChainPlayerDocuments(attackChainOpt.get());
    } else {
      throw new IllegalArgumentException("Exercise or scenario ID not found");
    }
  }

  @GetMapping("/api/player/{exerciseOrScenarioId}/documents/{documentId}/file")
  @RBAC(skipRBAC = true)
  public void downloadPlayerDocument(
      @PathVariable String attackChainRunOrAttackChainId,
      @PathVariable String documentId,
      @RequestParam Optional<String> userId,
      HttpServletResponse response)
      throws IOException {
    Optional<AttackChainRun> attackChainRunOpt = this.attackChainRunRepository.findById(attackChainRunOrAttackChainId);
    Optional<AttackChain> attackChainOpt = this.attackChainRepository.findById(attackChainRunOrAttackChainId);

    final User user = impersonateUser(userRepository, userId);
    if (user.getId().equals(ANONYMOUS)) {
      throw new UnsupportedOperationException("User must be logged or dynamic player is required");
    }

    Document document = null;
    if (attackChainRunOpt.isPresent()) {
      if (!attackChainRunOpt.get().isUserHasAccess(user)
          && !attackChainRunOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      document =
          getAttackChainRunPlayerDocuments(attackChainRunOpt.get()).stream()
              .filter(doc -> doc.getId().equals(documentId))
              .findFirst()
              .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    } else if (attackChainOpt.isPresent()) {
      if (!attackChainOpt.get().isUserHasAccess(user)
          && !attackChainOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      document =
          getAttackChainPlayerDocuments(attackChainOpt.get()).stream()
              .filter(doc -> doc.getId().equals(documentId))
              .findFirst()
              .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    }

    if (document != null) {
      response.addHeader(
          HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + document.getName());
      response.addHeader(HttpHeaders.CONTENT_TYPE, document.getType());
      response.setStatus(HttpServletResponse.SC_OK);
      try (InputStream fileStream =
          fileService
              .getFile(document)
              .orElseThrow(() -> new ElementNotFoundException("File not found"))) {
        fileStream.transferTo(response.getOutputStream());
      }
    }
  }
}
