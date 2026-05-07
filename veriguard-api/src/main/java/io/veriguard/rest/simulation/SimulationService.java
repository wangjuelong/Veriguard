package io.veriguard.rest.simulation;

import static io.veriguard.database.specification.AttackChainRunSpecification.byName;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.utils.FilterUtilsJpa.PAGE_NUMBER_OPTION;
import static io.veriguard.utils.FilterUtilsJpa.PAGE_SIZE_OPTION;

import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.utils.FilterUtilsJpa.Option;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class SimulationService {

  private final AttackChainRunRepository attackChainRunRepository;

  /**
   * Retrieves all attackChainRuns whose names match the provided search text, and converts them into
   * {@link Option} DTOs for UI consumption.
   *
   * @param searchText partial or full name to filter attackChainRuns
   * @return list of {@link Option} objects containing attackChainRun IDs and names
   */
  public List<Option> findAllAsOptions(final String searchText) {
    Pageable pageable =
        PageRequest.of(
            PAGE_NUMBER_OPTION, PAGE_SIZE_OPTION, Sort.by(Sort.Direction.ASC, "name", "createdAt"));
    return fromIterable(attackChainRunRepository.findAll(byName(searchText), pageable)).stream()
        .map(i -> new Option(i.getId(), i.getName()))
        .toList();
  }

  /**
   * Retrieves all attackChainRuns with IDs matching the given list, and converts them into {@link Option}
   * DTOs for UI consumption.
   *
   * @param ids list of attackChainRun IDs to retrieve
   * @return list of {@link Option} objects containing attackChainRun IDs and names
   */
  public List<Option> findAllByIdsAsOptions(final List<String> ids) {
    return fromIterable(attackChainRunRepository.findAllById(ids)).stream()
        .map(i -> new Option(i.getId(), i.getName()))
        .toList();
  }
}
