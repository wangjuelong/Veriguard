package io.veriguard.service.organization;

import static io.veriguard.database.specification.OrganizationSpecification.findGrantedFor;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.veriguard.database.model.Capability;
import io.veriguard.database.model.Organization;
import io.veriguard.database.model.User;
import io.veriguard.database.repository.OrganizationRepository;
import io.veriguard.service.UserService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationService {

  private final OrganizationRepository organizationRepository;

  private final UserService userService;

  public Page<Organization> organizationPagination(
      @NotNull SearchPaginationInput searchPaginationInput) {
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_PLATFORM_SETTINGS)) {
      return buildPaginationJPA(
          this.organizationRepository::findAll, searchPaginationInput, Organization.class);
    } else {
      return buildPaginationJPA(
          (Specification<Organization> specification, Pageable pageable) ->
              this.organizationRepository.findAll(
                  findGrantedFor(currentUser.getId()).and(specification), pageable),
          searchPaginationInput,
          Organization.class);
    }
  }
}
