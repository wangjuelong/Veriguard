package io.veriguard.service;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.service.AttackChainNodeExpectationUtils.isExpectationValid;
import static io.veriguard.utils.RuleAttributeUtils.hasColumnsOrDefaultValue;
import static io.veriguard.utils.SecurityUtils.getSanitizedExtension;
import static io.veriguard.utils.SecurityUtils.validatePathTraversal;
import static io.veriguard.utils.StringUtils.isValidUUID;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.rest.attack_chain.response.ImportMessage;
import io.veriguard.rest.attack_chain.response.ImportPostSummary;
import io.veriguard.rest.attack_chain.response.ImportTestSummary;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.utils.AttackChainNodeImportUtils;
import io.veriguard.utils.AttackChainNodeUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@Service
@Slf4j
public class AttackChainNodeImportService {

  private final AttackChainNodeRepository attackChainNodeRepository;
  private final AttackChainTeamUserRepository attackChainTeamUserRepository;
  private final AttackChainRunTeamUserRepository attackChainRunTeamUserRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;

  private final AttackChainRunRepository attackChainRunRepository;
  private final AttackChainRepository attackChainRepository;
  private final ImportService importService;

  private final List<String> importReservedField = List.of("description", "title", "trigger_time");
  final Pattern relativeDayPattern = Pattern.compile("^.*[DJ]([+\\-]?[0-9]*)(.*)$");
  final Pattern relativeHourPattern = Pattern.compile("^.*[HT]([+\\-]?[0-9]*).*$");
  final Pattern relativeMinutePattern = Pattern.compile("^.*[M]([+\\-]?[0-9]*).*$");

  public static final String BASE_DIR = System.getProperty("java.io.tmpdir");
  static final int FILE_STORAGE_DURATION = 60;

  /**
   * Store a xls file for ulterior import. The file will be deleted on exit.
   *
   * @param file MultipartFile
   * @return ImportPostSummary containing the importId and the list of available sheets
   */
  public ImportPostSummary storeXlsFileForImport(MultipartFile file) {
    ImportPostSummary result = new ImportPostSummary();
    result.setAvailableSheets(new ArrayList<>());
    // Generating a UUID for identifying the file
    String fileID = UUID.randomUUID().toString();
    result.setImportId(fileID);
    try {
      // We're opening the file and listing the names of the sheets
      Workbook workbook = WorkbookFactory.create(file.getInputStream());
      for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
        result.getAvailableSheets().add(workbook.getSheetName(i));
      }

      // Writing the file in a temp dir
      Path tempDir = Files.createDirectory(validatePathTraversal(BASE_DIR, fileID));

      // Sanitize filename extracting only extension from the base name
      String extension = getSanitizedExtension(file);
      Path tempFile = Files.createTempFile(tempDir, null, "." + extension);
      Files.write(tempFile, file.getBytes());

      CompletableFuture.delayedExecutor(FILE_STORAGE_DURATION, TimeUnit.MINUTES)
          .execute(
              () -> {
                tempFile.toFile().delete();
                tempDir.toFile().delete();
              });

      // We're making sure the files are deleted when the backend restart
      tempDir.toFile().deleteOnExit();
      tempFile.toFile().deleteOnExit();
    } catch (Exception ex) {
      log.error("Error while importing an xls file", ex);
      throw new BadRequestException("File seems to be corrupt");
    }

    return result;
  }

  public ImportTestSummary importAttackChainNodeIntoAttackChainFromXLS(
      AttackChain attackChain,
      ImportMapper importMapper,
      String importId,
      String sheetName,
      int timezoneOffset,
      boolean saveAll) {
    return importAttackChainNodeIntoFromXLS(
        attackChain, null, importMapper, importId, sheetName, timezoneOffset, saveAll);
  }

  public ImportTestSummary importAttackChainNodeIntoAttackChainRunFromXLS(
      AttackChainRun attackChainRun,
      ImportMapper importMapper,
      String importId,
      String sheetName,
      int timezoneOffset,
      boolean saveAll) {
    return importAttackChainNodeIntoFromXLS(
        null, attackChainRun, importMapper, importId, sheetName, timezoneOffset, saveAll);
  }

  public ImportTestSummary importAttackChainNodeIntoFromXLS(
      AttackChain attackChain,
      AttackChainRun attackChainRun,
      ImportMapper importMapper,
      String importId,
      String sheetName,
      int timezoneOffset,
      boolean saveAll) {
    // We call the attackChainNode service to get the attackChainNodes to create as well as messages
    // on how things
    // went
    ImportTestSummary importTestSummary =
        importXls(importId, attackChain, attackChainRun, importMapper, sheetName, timezoneOffset);
    Optional<ImportMessage> hasCritical =
        importTestSummary.getImportMessage().stream()
            .filter(
                importMessage ->
                    importMessage.getMessageLevel() == ImportMessage.MessageLevel.CRITICAL)
            .findAny();
    importTestSummary.setTotalNumberOfAttackChainNodes(
        importTestSummary.getAttackChainNodes().size());
    if (hasCritical.isPresent()) {
      // If there are critical errors, we do not save and we
      // empty the list of attackChainNodes, we just keep the messages
      importTestSummary.setTotalNumberOfAttackChainNodes(0);
      importTestSummary.setAttackChainNodes(new ArrayList<>());
    } else if (importTestSummary.getAttackChainNodes().isEmpty()
        && !importTestSummary.getImportMessage().isEmpty()) {
      return importTestSummary;
    } else if (saveAll) {
      importTestSummary.setAttackChainNodes(
          importTestSummary.getAttackChainNodes().stream()
              .map(
                  attackChainNode -> {
                    attackChainNode.setListened(false);
                    return attackChainNode;
                  })
              .toList());
      Iterable<AttackChainNode> newAttackChainNodes =
          attackChainNodeRepository.saveAll(importTestSummary.getAttackChainNodes());
      if (attackChainRun != null) {
        computeAttackChainNodeInAttackChainRun(attackChainRun, newAttackChainNodes);
      } else if (attackChain != null) {
        computeAttackChainNodeInAttackChain(attackChain, newAttackChainNodes);
      } else {
        throw new IllegalArgumentException(
            "At least one of exercise or scenario should be present");
      }
      importTestSummary.setAttackChainNodes(new ArrayList<>());
    } else {
      importTestSummary.setAttackChainNodes(
          importTestSummary.getAttackChainNodes().stream().limit(5).toList());
    }

    return importTestSummary;
  }

  private void computeAttackChainNodeInAttackChainRun(
      @NotNull AttackChainRun attackChainRun,
      @NotNull Iterable<AttackChainNode> newAttackChainNodes) {
    newAttackChainNodes.forEach(
        attackChainNode -> {
          attackChainRun.getAttackChainNodes().add(attackChainNode);
          attackChainNode
              .getTeams()
              .forEach(
                  team -> {
                    if (!attackChainRun.getTeams().contains(team)) {
                      attackChainRun.getTeams().add(team);
                    }
                  });
          attackChainNode
              .getTeams()
              .forEach(
                  team ->
                      team.getUsers()
                          .forEach(
                              user -> {
                                if (!attackChainRun.getTeamUsers().stream()
                                    .map(AttackChainRunTeamUser::getUser)
                                    .toList()
                                    .contains(user)) {
                                  AttackChainRunTeamUserId compositeId =
                                      new AttackChainRunTeamUserId();
                                  compositeId.setAttackChainRunId(attackChainRun.getId());
                                  compositeId.setTeamId(team.getId());
                                  compositeId.setUserId(user.getId());
                                  boolean exists =
                                      attackChainRunTeamUserRepository
                                          .findById(compositeId)
                                          .isPresent();

                                  if (!exists) {
                                    AttackChainRunTeamUser attackChainRunTeamUser =
                                        new AttackChainRunTeamUser();
                                    attackChainRunTeamUser.setAttackChainRun(attackChainRun);
                                    attackChainRunTeamUser.setTeam(team);
                                    attackChainRunTeamUser.setUser(user);
                                    attackChainRunTeamUser =
                                        attackChainRunTeamUserRepository.save(
                                            attackChainRunTeamUser);
                                    attackChainRun.getTeamUsers().add(attackChainRunTeamUser);
                                  }
                                }
                              }));
        });
  }

  private void computeAttackChainNodeInAttackChain(
      @NotNull AttackChain attackChain, @NotNull Iterable<AttackChainNode> newAttackChainNodes) {
    newAttackChainNodes.forEach(
        attackChainNode -> {
          attackChain.getAttackChainNodes().add(attackChainNode);
          attackChainNode
              .getTeams()
              .forEach(
                  team -> {
                    if (!attackChain.getTeams().contains(team)) {
                      attackChain.getTeams().add(team);
                    }
                  });
          attackChainNode
              .getTeams()
              .forEach(
                  team ->
                      team.getUsers()
                          .forEach(
                              user -> {
                                if (!attackChain.getTeamUsers().stream()
                                    .map(AttackChainTeamUser::getUser)
                                    .toList()
                                    .contains(user)) {
                                  AttackChainTeamUserId compositeId = new AttackChainTeamUserId();
                                  compositeId.setAttackChainId(attackChain.getId());
                                  compositeId.setTeamId(team.getId());
                                  compositeId.setUserId(user.getId());
                                  boolean exists =
                                      attackChainTeamUserRepository
                                          .findById(compositeId)
                                          .isPresent();

                                  if (!exists) {
                                    AttackChainTeamUser attackChainTeamUser =
                                        new AttackChainTeamUser();
                                    attackChainTeamUser.setAttackChain(attackChain);
                                    attackChainTeamUser.setTeam(team);
                                    attackChainTeamUser.setUser(user);
                                    attackChainTeamUser =
                                        attackChainTeamUserRepository.save(attackChainTeamUser);
                                    attackChain.getTeamUsers().add(attackChainTeamUser);
                                  }
                                }
                              }));
        });
  }

  private ImportTestSummary importXls(
      String importId,
      AttackChain attackChain,
      AttackChainRun attackChainRun,
      ImportMapper importMapper,
      String sheetName,
      int timezoneOffset) {
    ImportTestSummary importTestSummary = new ImportTestSummary();

    try {
      // Validate importId is a valid UUID
      isValidUUID(importId);

      // We open the previously saved file
      // Ensure the resolved path is still within the temp directory
      Path importDir = validatePathTraversal(BASE_DIR, importId);

      Path file;
      try (Stream<Path> files = Files.list(importDir)) {
        file =
            files
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No file found in import directory"));
      }

      // We open the file and convert it to an apache POI object
      InputStream xlsFile = Files.newInputStream(file);
      Workbook workbook = WorkbookFactory.create(xlsFile);
      Sheet selectedSheet = workbook.getSheet(sheetName);

      Map<Integer, AttackChainNodeTime> mapInstantByRowIndex = new HashMap<>();

      // For performance reasons, we compile the pattern of the AttackChainNode Importers only once
      Map<String, Pattern> mapPatternByAttackChainNodeImport =
          importMapper.getAttackChainNodeImporters().stream()
              .collect(
                  Collectors.toMap(
                      AttackChainNodeImporter::getId,
                      attackChainNodeImporter ->
                          Pattern.compile(attackChainNodeImporter.getImportTypeValue())));

      Map<String, Pattern> mapPatternByAllTeams =
          importMapper.getAttackChainNodeImporters().stream()
              .flatMap(
                  attackChainNodeImporter -> attackChainNodeImporter.getRuleAttributes().stream())
              .filter(ruleAttribute -> Objects.equals(ruleAttribute.getName(), "teams"))
              .filter(
                  ruleAttribute ->
                      ruleAttribute.getAdditionalConfig() != null
                          && !Strings.isBlank(
                              ruleAttribute.getAdditionalConfig().get("allTeamsValue")))
              .collect(
                  Collectors.toMap(
                      ruleAttribute -> ruleAttribute.getAdditionalConfig().get("allTeamsValue"),
                      ruleAttribute ->
                          Pattern.compile(ruleAttribute.getAdditionalConfig().get("allTeamsValue")),
                      (first, second) -> first));

      // We also get the list of teams into a map to be able to get them easily later on
      // First, all the teams that are non-contextual
      Map<String, Team> mapTeamByName =
          StreamSupport.stream(teamRepository.findAll().spliterator(), false)
              .filter(team -> !team.getContextual())
              .collect(
                  Collectors.toMap(Team::getName, Function.identity(), (first, second) -> first));

      // Then we add the contextual teams of the attackChain
      List<Team> teams;
      if (attackChainRun != null) {
        teams = attackChainRun.getTeams();
      } else if (attackChain != null) {
        teams = attackChain.getTeams();
      } else {
        throw new IllegalArgumentException(
            "At least one of exercise or scenario should be present");
      }
      mapTeamByName.putAll(
          teams.stream()
              .filter(Team::getContextual)
              .collect(
                  Collectors.toMap(Team::getName, Function.identity(), (first, second) -> first)));

      ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(timezoneOffset * 60);

      AtomicInteger count = new AtomicInteger(0);
      // For each rows of the selected sheet
      selectedSheet
          .rowIterator()
          .forEachRemaining(
              row -> {
                Instant start;
                if (attackChain != null) {
                  start = attackChain.getRecurrenceStart();
                } else if (attackChainRun != null) {
                  start = attackChainRun.getStart().orElse(null);
                } else {
                  throw new IllegalArgumentException(
                      "At least one of exercise or scenario should be present");
                }
                ImportRow rowSummary =
                    importRow(
                        row,
                        importMapper,
                        start,
                        mapPatternByAttackChainNodeImport,
                        mapTeamByName,
                        mapPatternByAllTeams,
                        zoneOffset,
                        count);
                // We set the attackChainRun or attackChain
                AttackChainNode attackChainNode = rowSummary.getAttackChainNode();
                if (attackChain != null && attackChainNode != null) {
                  attackChainNode.setAttackChain(attackChain);
                } else if (attackChainRun != null && attackChainNode != null) {
                  attackChainNode.setAttackChainRun(attackChainRun);
                }
                rowSummary.setAttackChainNode(attackChainNode);

                importTestSummary.getImportMessage().addAll(rowSummary.getImportMessages());
                if (rowSummary.getAttackChainNode() != null) {
                  importTestSummary.getAttackChainNodes().add(rowSummary.getAttackChainNode());
                }
                if (rowSummary.getAttackChainNodeTime() != null) {
                  mapInstantByRowIndex.put(row.getRowNum(), rowSummary.getAttackChainNodeTime());
                }
              });

      // Now that we did our first pass, we do another one real quick to find out
      // the date relative to each others
      importTestSummary.getImportMessage().addAll(updateAttackChainNodeDates(mapInstantByRowIndex));
      importTestSummary.setTotalRowsAnalysed(count.get());
      // We get the earliest date
      Optional<Instant> earliestDate =
          mapInstantByRowIndex.values().stream()
              .map(AttackChainNodeTime::getDate)
              .filter(Objects::nonNull)
              .min(Comparator.naturalOrder());

      // If there is one, we update the date
      earliestDate.ifPresent(
          date -> {
            ZonedDateTime zonedDateTime = date.atZone(ZoneId.of("UTC"));
            Instant dayOfStart =
                ZonedDateTime.of(
                        zonedDateTime.getYear(),
                        zonedDateTime.getMonthValue(),
                        zonedDateTime.getDayOfMonth(),
                        0,
                        0,
                        0,
                        0,
                        ZoneId.of("UTC"))
                    .toInstant();
            if (attackChain != null) {
              attackChain.setRecurrenceStart(dayOfStart);
              attackChain.setRecurrence(
                  "0 "
                      + zonedDateTime.getMinute()
                      + " "
                      + zonedDateTime.getHour()
                      + " * * *"); // Every day now + 1 hour
              attackChain.setRecurrenceEnd(dayOfStart.plus(1, ChronoUnit.DAYS));
            } else if (attackChainRun != null) {
              attackChainRun.setStart(dayOfStart);
            } else {
              throw new IllegalArgumentException(
                  "At least one of exercise or scenario should be present");
            }
          });
    } catch (IOException ex) {
      log.error("Error while importing an xls file", ex);
      throw new BadRequestException();
    }

    // Sorting by the order of the enum declaration to have error messages first, then warn and then
    // info
    importTestSummary.getImportMessage().sort(Comparator.comparing(ImportMessage::getMessageLevel));

    return importTestSummary;
  }

  private ImportRow importRow(
      Row row,
      ImportMapper importMapper,
      Instant start,
      Map<String, Pattern> mapPatternByAttackChainNodeImport,
      Map<String, Team> mapTeamByName,
      Map<String, Pattern> mapPatternByAllTeams,
      ZoneOffset timezoneOffset,
      AtomicInteger count) {
    ImportRow importTestSummary = new ImportRow();
    // The column that differenciate the importer is the same for all so we get it right now
    int colTypeIdx =
        CellReference.convertColStringToIndex(importMapper.getAttackChainNodeTypeColumn());
    if (colTypeIdx < 0) {
      // If there are no values, we add an info message so they know there is a potential issue here
      importTestSummary
          .getImportMessages()
          .add(
              new ImportMessage(
                  ImportMessage.MessageLevel.INFO,
                  ImportMessage.ErrorCode.NO_POTENTIAL_MATCH_FOUND,
                  Map.of(
                      "column_type_num",
                      importMapper.getAttackChainNodeTypeColumn(),
                      "row_num",
                      String.valueOf(row.getRowNum()))));
      return importTestSummary;
    }

    // If the row is completely empty, we ignore it altogether and do not send a warn message
    if (AttackChainNodeUtils.checkIfRowIsEmpty(row)) {
      return importTestSummary;
    } else {
      count.getAndIncrement();
    }
    // First of all, we get the value of the differentiation cell
    Cell typeCell = row.getCell(colTypeIdx);
    if (typeCell == null) {
      // If there are no values, we add an info message so they know there is a potential issue here
      importTestSummary
          .getImportMessages()
          .add(
              new ImportMessage(
                  ImportMessage.MessageLevel.INFO,
                  ImportMessage.ErrorCode.NO_POTENTIAL_MATCH_FOUND,
                  Map.of(
                      "column_type_num",
                      importMapper.getAttackChainNodeTypeColumn(),
                      "row_num",
                      String.valueOf(row.getRowNum()))));
      return importTestSummary;
    }

    // We find the matching importers on the attackChainNode
    List<AttackChainNodeImporter> matchingAttackChainNodeImporters =
        importMapper.getAttackChainNodeImporters().stream()
            .filter(
                attackChainNodeImporter -> {
                  Matcher matcher =
                      mapPatternByAttackChainNodeImport
                          .get(attackChainNodeImporter.getId())
                          .matcher(
                              AttackChainNodeImportUtils.getValueAsString(
                                  row, importMapper.getAttackChainNodeTypeColumn()));
                  return matcher.find();
                })
            .toList();

    // If there are no match, we add a message for the user and we go to the next row
    if (matchingAttackChainNodeImporters.isEmpty()) {
      importTestSummary
          .getImportMessages()
          .add(
              new ImportMessage(
                  ImportMessage.MessageLevel.INFO,
                  ImportMessage.ErrorCode.NO_POTENTIAL_MATCH_FOUND,
                  Map.of(
                      "column_type_num",
                      importMapper.getAttackChainNodeTypeColumn(),
                      "row_num",
                      String.valueOf(row.getRowNum()))));
      return importTestSummary;
    }

    // If there are more than one match, we add a message for the user and use the first match
    if (matchingAttackChainNodeImporters.size() > 1) {
      String listMatchers =
          matchingAttackChainNodeImporters.stream()
              .map(AttackChainNodeImporter::getImportTypeValue)
              .collect(Collectors.joining(", "));
      importTestSummary
          .getImportMessages()
          .add(
              new ImportMessage(
                  ImportMessage.MessageLevel.WARN,
                  ImportMessage.ErrorCode.SEVERAL_MATCHES,
                  Map.of(
                      "column_type_num",
                      importMapper.getAttackChainNodeTypeColumn(),
                      "row_num",
                      String.valueOf(row.getRowNum()),
                      "possible_matches",
                      listMatchers)));
      return importTestSummary;
    }

    AttackChainNodeImporter matchingAttackChainNodeImporter =
        matchingAttackChainNodeImporters.get(0);
    NodeContract nodeContract = matchingAttackChainNodeImporter.getNodeContract();

    // Creating the attackChainNode
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setDependsDuration(0L);

    // Adding the description
    setAttributeValue(
        row, matchingAttackChainNodeImporter, "description", attackChainNode::setDescription);

    // Adding the title
    setAttributeValue(row, matchingAttackChainNodeImporter, "title", attackChainNode::setTitle);

    // Adding the trigger time
    RuleAttribute triggerTimeRuleAttribute =
        matchingAttackChainNodeImporter.getRuleAttributes().stream()
            .filter(ruleAttribute -> ruleAttribute.getName().equals("trigger_time"))
            .findFirst()
            .orElseGet(
                () -> {
                  RuleAttribute ruleAttributeDefault = new RuleAttribute();
                  ruleAttributeDefault.setAdditionalConfig(Map.of("timePattern", ""));
                  return ruleAttributeDefault;
                });

    String timePattern = triggerTimeRuleAttribute.getAdditionalConfig().get("timePattern");
    String dateAsString = Strings.EMPTY;
    if (triggerTimeRuleAttribute.getColumns() != null) {
      dateAsString =
          Arrays.stream(triggerTimeRuleAttribute.getColumns().split("\\+"))
              .map(
                  column ->
                      AttackChainNodeImportUtils.getDateAsStringFromCell(row, column, timePattern))
              .collect(Collectors.joining());
    }
    if (dateAsString.isBlank()) {
      dateAsString = triggerTimeRuleAttribute.getDefaultValue();
    }

    Matcher relativeDayMatcher = relativeDayPattern.matcher(dateAsString);
    Matcher relativeHourMatcher = relativeHourPattern.matcher(dateAsString);
    Matcher relativeMinuteMatcher = relativeMinutePattern.matcher(dateAsString);

    boolean relativeDays = relativeDayMatcher.matches();
    boolean relativeHour = relativeHourMatcher.matches();
    boolean relativeMinute = relativeMinuteMatcher.matches();

    AttackChainNodeTime attackChainNodeTime = new AttackChainNodeTime();
    attackChainNodeTime.setUnformattedDate(dateAsString);
    attackChainNodeTime.setLinkedAttackChainNode(attackChainNode);

    Temporal dateTime =
        AttackChainNodeImportUtils.getAttackChainNodeDate(attackChainNodeTime, timePattern);

    if (dateTime == null) {
      attackChainNodeTime.setRelativeDay(relativeDays);
      if (relativeDays
          && relativeDayMatcher.groupCount() > 0
          && !relativeDayMatcher.group(1).isBlank()) {
        try {
          attackChainNodeTime.setRelativeDayNumber(Integer.parseInt(relativeDayMatcher.group(1)));
        } catch (NumberFormatException ex) {
          log.warn(
              String.format("Can't format %s into an integer", relativeDayMatcher.group(1)), ex);
        }
      }
      attackChainNodeTime.setRelativeHour(relativeHour);
      if (relativeHour
          && relativeHourMatcher.groupCount() > 0
          && !relativeHourMatcher.group(1).isBlank()) {
        try {
          attackChainNodeTime.setRelativeHourNumber(Integer.parseInt(relativeHourMatcher.group(1)));
        } catch (NumberFormatException ex) {
          log.warn(
              String.format("Can't format %s into an integer", relativeHourMatcher.group(1)), ex);
        }
      }
      attackChainNodeTime.setRelativeMinute(relativeMinute);
      if (relativeMinute
          && relativeMinuteMatcher.groupCount() > 0
          && !relativeMinuteMatcher.group(1).isBlank()) {
        try {
          attackChainNodeTime.setRelativeMinuteNumber(
              Integer.parseInt(relativeMinuteMatcher.group(1)));
        } catch (NumberFormatException ex) {
          log.warn(
              String.format("Can't format %s into an integer", relativeMinuteMatcher.group(1)), ex);
        }
      }

      // Special case : a mix of relative day and absolute hour
      if (relativeDays
          && relativeDayMatcher.groupCount() > 1
          && !relativeDayMatcher.group(2).isBlank()) {
        Temporal date = null;
        try {
          date =
              LocalTime.parse(
                  relativeDayMatcher.group(2).trim(), attackChainNodeTime.getFormatter());
        } catch (DateTimeParseException firstException) {
          try {
            date = LocalTime.parse(relativeDayMatcher.group(2).trim(), DateTimeFormatter.ISO_TIME);
          } catch (DateTimeParseException exception) {
            // This is a "probably" a relative date
          }
        }
        if (date != null) {
          if (start != null) {
            attackChainNodeTime.setDate(
                start
                    .atZone(timezoneOffset)
                    .toLocalDateTime()
                    .withHour(date.get(ChronoField.HOUR_OF_DAY))
                    .withMinute(date.get(ChronoField.MINUTE_OF_HOUR))
                    .toInstant(timezoneOffset));
          } else {
            importTestSummary
                .getImportMessages()
                .add(
                    new ImportMessage(
                        ImportMessage.MessageLevel.CRITICAL,
                        ImportMessage.ErrorCode.ABSOLUTE_TIME_WITHOUT_START_DATE,
                        Map.of(
                            "column_type_num",
                            importMapper.getAttackChainNodeTypeColumn(),
                            "row_num",
                            String.valueOf(row.getRowNum()))));
          }
        }
      }
    }
    attackChainNodeTime.setSpecifyDays(
        relativeDays || attackChainNodeTime.getFormatter().equals(DateTimeFormatter.ISO_DATE_TIME));

    // We get the absolute dates available on our first pass
    if (!attackChainNodeTime.isRelativeDay()
        && !attackChainNodeTime.isRelativeHour()
        && !attackChainNodeTime.isRelativeMinute()
        && dateTime != null) {
      if (dateTime instanceof LocalDateTime) {
        Instant attackChainNodeDate =
            Instant.ofEpochSecond(((LocalDateTime) dateTime).toEpochSecond(timezoneOffset));
        attackChainNodeTime.setDate(attackChainNodeDate);
      } else if (dateTime instanceof LocalTime) {
        if (start != null) {
          attackChainNodeTime.setDate(
              start
                  .atZone(timezoneOffset)
                  .withHour(((LocalTime) dateTime).getHour())
                  .withMinute(((LocalTime) dateTime).getMinute())
                  .toInstant());
        } else {
          importTestSummary
              .getImportMessages()
              .add(
                  new ImportMessage(
                      ImportMessage.MessageLevel.CRITICAL,
                      ImportMessage.ErrorCode.ABSOLUTE_TIME_WITHOUT_START_DATE,
                      Map.of(
                          "column_type_num",
                          importMapper.getAttackChainNodeTypeColumn(),
                          "row_num",
                          String.valueOf(row.getRowNum()))));
          return importTestSummary;
        }
      }
    }

    // Initializing the content with a root node
    ObjectMapper mapper = new ObjectMapper();
    attackChainNode.setContent(mapper.createObjectNode());

    // Once it's done, we set the nodeContract
    attackChainNode.setNodeContract(nodeContract);

    // So far, we only support one expectation
    AtomicReference<AttackChainNodeExpectation> expectation = new AtomicReference<>();

    // For each rule attributes of the importer
    matchingAttackChainNodeImporter
        .getRuleAttributes()
        .forEach(
            ruleAttribute ->
                importTestSummary
                    .getImportMessages()
                    .addAll(
                        addFields(
                            attackChainNode,
                            ruleAttribute,
                            row,
                            mapTeamByName,
                            expectation,
                            importMapper,
                            mapPatternByAllTeams)));
    // The user is the one doing the import
    attackChainNode.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    // No attackChainRun yet
    attackChainNode.setAttackChainRun(null);
    // No dependencies
    attackChainNode.setDependsOn(null);

    if (expectation.get() != null && isExpectationValid(expectation.get())) {
      // We set the expectation
      ArrayNode expectationsNode = mapper.createArrayNode();
      ObjectNode expectationNode = mapper.createObjectNode();
      expectationNode.put("expectation_description", expectation.get().getDescription());
      expectationNode.put("expectation_name", expectation.get().getName());
      expectationNode.put("expectation_score", expectation.get().getExpectedScore());
      expectationNode.put("expectation_type", expectation.get().getType().name());
      expectationNode.put("expectation_expectation_group", false);
      expectationsNode.add(expectationNode);
      attackChainNode.getContent().set("expectations", expectationsNode);
    }

    importTestSummary.setAttackChainNode(attackChainNode);
    importTestSummary.setAttackChainNodeTime(attackChainNodeTime);
    return importTestSummary;
  }

  private void setAttributeValue(
      Row row,
      AttackChainNodeImporter matchingAttackChainNodeImporter,
      String attributeName,
      Consumer<String> setter) {
    RuleAttribute ruleAttribute =
        matchingAttackChainNodeImporter.getRuleAttributes().stream()
            .filter(attr -> attr.getName().equals(attributeName))
            .findFirst()
            .orElseThrow(ElementNotFoundException::new);

    if (ruleAttribute.getColumns() != null) {
      int colIndex = CellReference.convertColStringToIndex(ruleAttribute.getColumns());
      if (colIndex == -1) {
        return;
      }
      Cell valueCell = row.getCell(colIndex);

      if (valueCell == null) {
        setter.accept(ruleAttribute.getDefaultValue());
      } else {
        String cellValue =
            AttackChainNodeImportUtils.getValueAsString(row, ruleAttribute.getColumns());
        setter.accept(cellValue.isBlank() ? ruleAttribute.getDefaultValue() : cellValue);
      }
    }
  }

  private List<ImportMessage> addFields(
      AttackChainNode attackChainNode,
      RuleAttribute ruleAttribute,
      Row row,
      Map<String, Team> mapTeamByName,
      AtomicReference<AttackChainNodeExpectation> expectation,
      ImportMapper importMapper,
      Map<String, Pattern> mapPatternByAllTeams) {
    // If it's a reserved field, it's already taken care of
    if (importReservedField.contains(ruleAttribute.getName())) {
      return emptyList();
    }

    // For ease of use, we create a map of the available keys for the nodeExecutor
    Map<String, JsonNode> mapFieldByKey =
        StreamSupport.stream(
                (Objects.requireNonNull(
                    attackChainNode
                        .getNodeContract()
                        .map(
                            nodeContract ->
                                nodeContract.getConvertedContent().get("fields").spliterator())
                        .orElse(null))),
                false)
            .collect(
                Collectors.toMap(jsonNode -> jsonNode.get("key").asText(), Function.identity()));

    // Otherwise, the default type is text, but it can be overriden
    String type = "text";
    if (mapFieldByKey.get(ruleAttribute.getName()) != null) {
      type = mapFieldByKey.get(ruleAttribute.getName()).get("type").asText();
    } else if (ruleAttribute.getName().startsWith("expectation")) {
      type = "expectation";
    }
    switch (type) {
      case "text", "textarea":
        // If it is a text, we get the columns, split by "+" if there is a concatenation of columns
        // and then joins the result of the cells
        String columnValue = Strings.EMPTY;
        if (ruleAttribute.getColumns() != null) {
          columnValue =
              AttackChainNodeImportUtils.extractAndConvertStringColumnValue(
                  row, ruleAttribute, mapFieldByKey);
        }
        if (columnValue.isBlank()) {
          attackChainNode.getContent().put(ruleAttribute.getDefaultValue(), columnValue);
        } else {
          attackChainNode.getContent().put(ruleAttribute.getName(), columnValue);
        }
        break;
      case "team":
        // If the rule type is on a team field, we split by "+" if there is a concatenation of
        // columns
        // and then joins the result, split again by "," and use the list of results to get the
        // teams by their name
        List<String> columnValues = new ArrayList<>();
        String allTeamsValue =
            ruleAttribute.getAdditionalConfig() != null
                ? ruleAttribute.getAdditionalConfig().get("allTeamsValue")
                : null;
        if (ruleAttribute.getColumns() != null) {
          columnValues =
              Arrays.stream(
                      Arrays.stream(ruleAttribute.getColumns().split("\\+"))
                          .map(column -> AttackChainNodeImportUtils.getValueAsString(row, column))
                          .collect(Collectors.joining(","))
                          .split(","))
                  .toList();
        }
        if (columnValues.isEmpty() || columnValues.stream().allMatch(String::isEmpty)) {
          List<String> defaultValues =
              Arrays.stream(ruleAttribute.getDefaultValue().split(",")).toList();
          attackChainNode
              .getTeams()
              .addAll(
                  mapTeamByName.entrySet().stream()
                      .filter(nameTeamEntry -> defaultValues.contains(nameTeamEntry.getKey()))
                      .map(Map.Entry::getValue)
                      .toList());
        } else {
          List<ImportMessage> importMessages = new ArrayList<>();
          columnValues.forEach(
              teamName -> {
                attackChainNode.setAllTeams(false);
                Matcher allTeamsMatcher = null;
                if (mapPatternByAllTeams.get(allTeamsValue) != null) {
                  allTeamsMatcher = mapPatternByAllTeams.get(allTeamsValue).matcher(teamName);
                }
                if (allTeamsValue != null && allTeamsMatcher != null && allTeamsMatcher.find()) {
                  attackChainNode.setAllTeams(true);
                } else if (mapTeamByName.containsKey(teamName)) {
                  attackChainNode.getTeams().add(mapTeamByName.get(teamName));
                } else {
                  // The team does not exist, we create a new one
                  Team team = new Team();
                  team.setName(teamName);
                  team.setContextual(true);
                  team = teamRepository.save(team);
                  mapTeamByName.put(team.getName(), team);
                  attackChainNode.getTeams().add(team);

                  // We aldo add a message so the user knows there was a new team created
                  importMessages.add(
                      new ImportMessage(
                          ImportMessage.MessageLevel.WARN,
                          ImportMessage.ErrorCode.NO_TEAM_FOUND,
                          Map.of(
                              "column_type_num",
                              importMapper.getAttackChainNodeTypeColumn(),
                              "row_num",
                              String.valueOf(row.getRowNum()),
                              "team_name",
                              teamName)));
                }
              });
          if (!importMessages.isEmpty()) {
            return importMessages;
          }
        }
        break;
      case "expectation":
        if (!hasColumnsOrDefaultValue(ruleAttribute)) {
          break;
        }
        // If the rule type is of an expectation,
        if (expectation.get() == null) {
          expectation.set(new AttackChainNodeExpectation());
          expectation.get().setType(AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL);
        }
        if (ruleAttribute.getName().contains("_")) {
          if ("score".equals(ruleAttribute.getName().split("_")[1])) {
            if (ruleAttribute.getColumns() != null) {
              List<String> columns =
                  Arrays.stream(ruleAttribute.getColumns().split("\\+"))
                      .filter(column -> column != null && !column.isBlank())
                      .toList();
              if (!columns.isEmpty()
                  && columns.stream()
                      .allMatch(
                          column ->
                              row.getCell(CellReference.convertColStringToIndex(column))
                                      .getCellType()
                                  == CellType.NUMERIC)) {
                Double columnValueExpectation =
                    columns.stream()
                        .map(column -> AttackChainNodeImportUtils.getValueAsDouble(row, column))
                        .reduce(0.0, Double::sum);
                expectation.get().setExpectedScore(columnValueExpectation);
              } else {
                try {
                  expectation
                      .get()
                      .setExpectedScore(Double.parseDouble(ruleAttribute.getDefaultValue()));
                } catch (NumberFormatException exception) {
                  List<ImportMessage> importMessages = new ArrayList<>();
                  importMessages.add(
                      new ImportMessage(
                          ImportMessage.MessageLevel.WARN,
                          ImportMessage.ErrorCode.EXPECTATION_SCORE_UNDEFINED,
                          Map.of(
                              "column_type_num",
                              String.join(", ", columns),
                              "row_num",
                              String.valueOf(row.getRowNum()))));
                  return importMessages;
                }
              }
            } else {
              expectation
                  .get()
                  .setExpectedScore(Double.parseDouble(ruleAttribute.getDefaultValue()));
            }
          } else if ("name".equals(ruleAttribute.getName().split("_")[1])) {
            if (ruleAttribute.getColumns() != null) {
              String columnValueExpectation =
                  Arrays.stream(ruleAttribute.getColumns().split("\\+"))
                      .map(column -> AttackChainNodeImportUtils.getValueAsString(row, column))
                      .collect(Collectors.joining());
              expectation
                  .get()
                  .setName(
                      columnValueExpectation.isBlank()
                          ? ruleAttribute.getDefaultValue()
                          : columnValueExpectation);
            } else {
              expectation.get().setName(ruleAttribute.getDefaultValue());
            }
          } else if ("description".equals(ruleAttribute.getName().split("_")[1])) {
            if (ruleAttribute.getColumns() != null) {
              String columnValueExpectation =
                  Arrays.stream(ruleAttribute.getColumns().split("\\+"))
                      .map(column -> AttackChainNodeImportUtils.getValueAsString(row, column))
                      .collect(Collectors.joining());
              expectation
                  .get()
                  .setDescription(
                      columnValueExpectation.isBlank()
                          ? ruleAttribute.getDefaultValue()
                          : columnValueExpectation);
            } else {
              expectation.get().setDescription(ruleAttribute.getDefaultValue());
            }
          }
        }

        break;
      default:
        throw new UnsupportedOperationException();
    }
    return emptyList();
  }

  private List<ImportMessage> updateAttackChainNodeDates(
      Map<Integer, AttackChainNodeTime> mapInstantByRowIndex) {
    List<ImportMessage> importMessages = new ArrayList<>();
    // First of all, are there any absolute date
    boolean allDatesAreAbsolute =
        mapInstantByRowIndex.values().stream()
            .filter(attackChainNodeTime -> !attackChainNodeTime.getUnformattedDate().isBlank())
            .noneMatch(
                attackChainNodeTime ->
                    attackChainNodeTime.getDate() == null
                        || attackChainNodeTime.isRelativeDay()
                        || attackChainNodeTime.isRelativeHour()
                        || attackChainNodeTime.isRelativeMinute());
    boolean allDatesAreRelative =
        mapInstantByRowIndex.values().stream()
            .filter(attackChainNodeTime -> !attackChainNodeTime.getUnformattedDate().isBlank())
            .allMatch(
                attackChainNodeTime ->
                    attackChainNodeTime.getDate() == null
                        && (attackChainNodeTime.isRelativeDay()
                            || attackChainNodeTime.isRelativeHour()
                            || attackChainNodeTime.isRelativeMinute()));

    if (allDatesAreAbsolute) {
      processDateToAbsolute(mapInstantByRowIndex);
    } else if (allDatesAreRelative) {
      // All is relative and we just need to set depends relative to each others
      // First of all, we find the minimal relative number of days and hour
      int earliestDay =
          mapInstantByRowIndex.values().stream()
              .min(Comparator.comparing(AttackChainNodeTime::getRelativeDayNumber))
              .map(AttackChainNodeTime::getRelativeDayNumber)
              .orElse(0);
      int earliestHourOfThatDay =
          mapInstantByRowIndex.values().stream()
              .filter(
                  attackChainNodeTime -> attackChainNodeTime.getRelativeDayNumber() == earliestDay)
              .min(Comparator.comparing(AttackChainNodeTime::getRelativeHourNumber))
              .map(AttackChainNodeTime::getRelativeHourNumber)
              .orElse(0);
      int earliestMinuteOfThatHour =
          mapInstantByRowIndex.values().stream()
              .filter(
                  attackChainNodeTime ->
                      attackChainNodeTime.getRelativeDayNumber() == earliestDay
                          && attackChainNodeTime.getRelativeHourNumber() == earliestHourOfThatDay)
              .min(Comparator.comparing(AttackChainNodeTime::getRelativeMinuteNumber))
              .map(AttackChainNodeTime::getRelativeMinuteNumber)
              .orElse(0);
      long offsetAsMinutes =
          (((earliestDay * 24L) + earliestHourOfThatDay) * 60 + earliestMinuteOfThatHour) * -1;
      mapInstantByRowIndex.values().stream()
          .filter(
              attackChainNodeTime ->
                  attackChainNodeTime.getDate() == null
                      && (attackChainNodeTime.isRelativeDay()
                          || attackChainNodeTime.isRelativeHour()
                          || attackChainNodeTime.isRelativeMinute()))
          .forEach(
              attackChainNodeTime -> {
                long attackChainNodeTimeAsMinutes =
                    (((attackChainNodeTime.getRelativeDayNumber() * 24L)
                                + attackChainNodeTime.getRelativeHourNumber())
                            * 60)
                        + attackChainNodeTime.getRelativeMinuteNumber()
                        + offsetAsMinutes;
                attackChainNodeTime
                    .getLinkedAttackChainNode()
                    .setDependsDuration(attackChainNodeTimeAsMinutes * 60);
              });
    } else {
      // Worst case attackChain : there is a mix of relative and absolute dates
      // We will need to resolve this row by row in the order they are in the import file
      Optional<Map.Entry<Integer, AttackChainNodeTime>> sortedInstantMap =
          mapInstantByRowIndex.entrySet().stream().min(Map.Entry.comparingByKey());
      int firstRow;
      if (sortedInstantMap.isPresent()) {
        firstRow = sortedInstantMap.get().getKey();
      } else {
        firstRow = 0;
      }
      mapInstantByRowIndex.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEachOrdered(
              integerAttackChainNodeTimeEntry -> {
                AttackChainNodeTime attackChainNodeTime =
                    integerAttackChainNodeTimeEntry.getValue();

                if (attackChainNodeTime.getDate() != null) {
                  // Special case : we have an absolute time but a relative day
                  if (attackChainNodeTime.isRelativeDay()) {
                    attackChainNodeTime.setDate(
                        attackChainNodeTime
                            .getDate()
                            .plus(attackChainNodeTime.getRelativeDayNumber(), ChronoUnit.DAYS));
                  }
                  // Great, we already have an absolute date for this one
                  // If we are the first, good, nothing more to do
                  // Otherwise, we need to get the date of the first row to set the depends on
                  if (integerAttackChainNodeTimeEntry.getKey() != firstRow) {
                    Instant firstDate = mapInstantByRowIndex.get(firstRow).getDate();
                    attackChainNodeTime
                        .getLinkedAttackChainNode()
                        .setDependsDuration(
                            attackChainNodeTime.getDate().getEpochSecond()
                                - firstDate.getEpochSecond());
                  }
                } else {
                  // We don't have an absolute date so we need to deduce it from another row
                  if (attackChainNodeTime.getRelativeDayNumber() < 0
                      || (attackChainNodeTime.getRelativeDayNumber() == 0
                          && attackChainNodeTime.getRelativeHourNumber() < 0)
                      || (attackChainNodeTime.getRelativeDayNumber() == 0
                          && attackChainNodeTime.getRelativeHourNumber() == 0
                          && attackChainNodeTime.getRelativeMinuteNumber() < 0)) {
                    // We are in the past, so we need to explore the future to find the next
                    // absolute date
                    Optional<Map.Entry<Integer, AttackChainNodeTime>> firstFutureWithAbsolute =
                        mapInstantByRowIndex.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .filter(
                                entry ->
                                    entry.getKey() > integerAttackChainNodeTimeEntry.getKey()
                                        && entry.getValue().getDate() != null)
                            .findFirst();
                    if (firstFutureWithAbsolute.isPresent()) {
                      attackChainNodeTime.setDate(
                          firstFutureWithAbsolute
                              .get()
                              .getValue()
                              .getDate()
                              .plus(attackChainNodeTime.getRelativeDayNumber(), ChronoUnit.DAYS)
                              .plus(attackChainNodeTime.getRelativeHourNumber(), ChronoUnit.HOURS)
                              .plus(
                                  attackChainNodeTime.getRelativeMinuteNumber(),
                                  ChronoUnit.MINUTES));
                    } else {
                      importMessages.add(
                          new ImportMessage(
                              ImportMessage.MessageLevel.ERROR,
                              ImportMessage.ErrorCode.DATE_SET_IN_PAST,
                              Map.of(
                                  "row_num",
                                  String.valueOf(integerAttackChainNodeTimeEntry.getKey()))));
                    }
                  } else {
                    // We are in the future, so we need to explore the past to find an absolute date
                    Optional<Map.Entry<Integer, AttackChainNodeTime>> firstPastWithAbsolute =
                        mapInstantByRowIndex.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .filter(
                                entry ->
                                    entry.getKey() < integerAttackChainNodeTimeEntry.getKey()
                                        && entry.getValue().getDate() != null)
                            .min(Map.Entry.comparingByKey(Comparator.reverseOrder()));
                    if (firstPastWithAbsolute.isPresent()) {
                      attackChainNodeTime.setDate(
                          firstPastWithAbsolute
                              .get()
                              .getValue()
                              .getDate()
                              .plus(attackChainNodeTime.getRelativeDayNumber(), ChronoUnit.DAYS)
                              .plus(attackChainNodeTime.getRelativeHourNumber(), ChronoUnit.HOURS)
                              .plus(
                                  attackChainNodeTime.getRelativeMinuteNumber(),
                                  ChronoUnit.MINUTES));
                    } else {
                      importMessages.add(
                          new ImportMessage(
                              ImportMessage.MessageLevel.ERROR,
                              ImportMessage.ErrorCode.DATE_SET_IN_FUTURE,
                              Map.of(
                                  "row_num",
                                  String.valueOf(integerAttackChainNodeTimeEntry.getKey()))));
                    }
                  }
                }
              });

      processDateToAbsolute(mapInstantByRowIndex);
    }

    return importMessages;
  }

  private void processDateToAbsolute(Map<Integer, AttackChainNodeTime> mapInstantByRowIndex) {
    Optional<Instant> earliestAbsoluteDate =
        mapInstantByRowIndex.values().stream()
            .map(AttackChainNodeTime::getDate)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder());

    // If we have an earliest date, we calculate the dates depending on the earliest one
    earliestAbsoluteDate.ifPresent(
        earliestInstant ->
            mapInstantByRowIndex.values().stream()
                .filter(attackChainNodeTime -> attackChainNodeTime.getDate() != null)
                .forEach(
                    attackChainNodeTime ->
                        attackChainNodeTime
                            .getLinkedAttackChainNode()
                            .setDependsDuration(
                                attackChainNodeTime.getDate().getEpochSecond()
                                    - earliestInstant.getEpochSecond())));
  }

  public void importAttackChainNodesForAttackChain(MultipartFile file, String attackChainId)
      throws Exception {
    AttackChain targetAttackChain =
        attackChainRepository.findById(attackChainId).orElseThrow(ElementNotFoundException::new);

    this.importService.handleFileImport(file, null, targetAttackChain);
  }

  public void importAttackChainNodesForSimulation(MultipartFile file, String simulationId)
      throws Exception {
    AttackChainRun targetSimulation =
        attackChainRunRepository.findById(simulationId).orElseThrow(ElementNotFoundException::new);

    this.importService.handleFileImport(file, targetSimulation, null);
  }

  public void importAttackChainNodesForAtomicTestings(MultipartFile file) throws Exception {
    this.importService.handleFileImport(file, null, null);
  }
}
