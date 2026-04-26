package io.veriguard.rest.organization;

import static io.veriguard.database.specification.OrganizationSpecification.byName;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static java.time.Instant.now;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawOrganization;
import io.veriguard.database.repository.OrganizationRepository;
import io.veriguard.database.repository.TagRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.organization.form.OrganizationCreateInput;
import io.veriguard.rest.organization.form.OrganizationUpdateInput;
import io.veriguard.service.organization.OrganizationService;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrganizationApi extends RestBehavior {

  public static final String ORGANIZATION_URI = "/api/organizations";

  private final OrganizationRepository organizationRepository;
  private final TagRepository tagRepository;
  private final OrganizationService organizationService;

  @GetMapping(ORGANIZATION_URI)
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ORGANIZATION)
  public Iterable<RawOrganization> organizations() {
    List<RawOrganization> organizations;
    organizations = fromIterable(organizationRepository.rawAll());
    return organizations;
  }

  @PostMapping(ORGANIZATION_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ORGANIZATION)
  public Page<Organization> organizations(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.organizationService.organizationPagination(searchPaginationInput);
  }

  @PostMapping(ORGANIZATION_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ORGANIZATION)
  @Transactional(rollbackOn = Exception.class)
  public Organization createOrganization(@Valid @RequestBody OrganizationCreateInput input) {
    Organization organization = new Organization();
    organization.setUpdateAttributes(input);
    organization.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return organizationRepository.save(organization);
  }

  @PutMapping(ORGANIZATION_URI + "/{organizationId}")
  @RBAC(
      resourceId = "#organizationId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ORGANIZATION)
  public Organization updateOrganization(
      @PathVariable String organizationId, @Valid @RequestBody OrganizationUpdateInput input) {
    Organization organization =
        organizationRepository.findById(organizationId).orElseThrow(ElementNotFoundException::new);
    organization.setUpdateAttributes(input);
    organization.setUpdatedAt(now());
    organization.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return organizationRepository.save(organization);
  }

  @DeleteMapping(ORGANIZATION_URI + "/{organizationId}")
  @RBAC(
      resourceId = "#organizationId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ORGANIZATION)
  public void deleteOrganization(@PathVariable String organizationId) {
    organizationRepository.deleteById(organizationId);
  }

  // -- OPTION --

  @GetMapping(ORGANIZATION_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ORGANIZATION)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return fromIterable(
            this.organizationRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping(ORGANIZATION_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ORGANIZATION)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.organizationRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
