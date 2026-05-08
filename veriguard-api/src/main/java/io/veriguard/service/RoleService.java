package io.veriguard.service;

import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationJPA;

import com.google.common.annotations.VisibleForTesting;
import io.veriguard.database.model.Capability;
import io.veriguard.database.model.Group;
import io.veriguard.database.model.Role;
import io.veriguard.database.repository.GroupRepository;
import io.veriguard.database.repository.RoleRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RoleService {
  private final RoleRepository roleRepository;
  private final GroupRepository groupRepository;

  public Optional<Role> findById(String id) {
    return roleRepository.findById(id);
  }

  public List<Role> findAll() {
    return StreamSupport.stream(roleRepository.findAll().spliterator(), false)
        .collect(Collectors.toList());
  }

  public Role createRole(
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    return createRole(UUID.randomUUID().toString(), roleName, roleDescription, capabilities);
  }

  public Role createRole(
      @NotBlank final String id,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {
    Role role = new Role();
    role.setId(id);
    role.setName(roleName);
    role.setDescription(roleDescription);
    role.setCapabilities(getCapabilitiesWithParents(capabilities));
    return roleRepository.save(role);
  }

  public Role updateRole(
      @NotBlank final String roleId,
      @NotBlank final String roleName,
      @NotBlank final String roleDescription,
      @NotNull final Set<Capability> capabilities) {

    // verify that the role exists
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));

    role.setUpdatedAt(Instant.now());
    role.setName(roleName);
    role.setDescription(roleDescription);
    role.setCapabilities(getCapabilitiesWithParents(capabilities));

    return roleRepository.save(role);
  }

  public Page<Role> searchRole(SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(roleRepository::findAll, searchPaginationInput, Role.class);
  }

  public void deleteRole(@NotBlank final String roleId) {
    // verify that the role exists
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));

    List<Group> groups = groupRepository.findAllByRoles(role);
    for (Group g : groups) {
      g.getRoles().remove(role);
    }

    roleRepository.deleteById(roleId);
  }

  /**
   * Get a set of capabilities as input and return a set containing the input + their parent
   *
   * @param capabilitiesInput
   * @return
   */
  @VisibleForTesting
  protected Set<Capability> getCapabilitiesWithParents(
      @NotNull final Set<Capability> capabilitiesInput) {
    Set<Capability> result = new HashSet<>();

    for (Capability capability : capabilitiesInput) {
      Capability current = capability;
      while (current != null && result.add(current)) {
        current = current.getParent();
      }
    }
    return result;
  }
}
