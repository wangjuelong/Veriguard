package io.veriguard.opencti.connectors.service;

import static io.veriguard.opencti.connectors.Constants.*;

import io.veriguard.database.model.Group;
import io.veriguard.database.model.Role;
import io.veriguard.database.model.User;
import io.veriguard.opencti.connectors.ConnectorBase;
import io.veriguard.rest.group.form.GroupCreateInput;
import io.veriguard.rest.user.form.user.CreateUserInput;
import io.veriguard.rest.user.form.user.UpdateUserInput;
import io.veriguard.service.GroupService;
import io.veriguard.service.RoleService;
import io.veriguard.service.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrivilegeService {
  private final RoleService roleService;
  private final GroupService groupService;
  private final UserService userService;

  @Transactional
  public void ensurePrivilegedUserExistsForConnector(ConnectorBase connector) {
    Group group = createWellKnownGroupWithRole(createWellKnownRole());

    Optional<User> connectorUser = userService.findByToken(connector.getToken());
    Optional<User> existingEmailUser =
        userService.findByEmailIgnoreCase(
            "connector-%s@veriguard.invalid".formatted(connector.getId()));

    if (connectorUser.isEmpty()) {

      if (existingEmailUser.isPresent()) {
        log.warn(
            "User with email {} already exists, but no token found. Reusing existing user.",
            existingEmailUser.get().getEmail());
        existingEmailUser
            .get()
            .setTokens(
                new ArrayList<>(
                    List.of(
                        userService.createUserToken(
                            existingEmailUser.get(), connector.getToken()))));
        existingEmailUser.get().setGroups(new ArrayList<>(List.of(group)));
        userService.updateUser(existingEmailUser.get());
        return;
      }

      CreateUserInput input = new CreateUserInput();
      input.setAdmin(false);
      input.setFirstname(connector.getName());
      input.setLastname("OpenCTI Connector");
      input.setToken(connector.getToken());
      input.setEmail("connector-%s@veriguard.invalid".formatted(connector.getId()));
      User u = userService.createUser(input, 1); // magic number; Active
      u.setGroups(new ArrayList<>(List.of(group)));
      userService.updateUser(u);
    } else {
      UpdateUserInput input = new UpdateUserInput();
      input.setAdmin(false);
      input.setFirstname(connector.getName());
      input.setLastname("OpenCTI Connector");
      input.setEmail("connector-%s@veriguard.invalid".formatted(connector.getId()));
      connectorUser.get().setGroups(new ArrayList<>(List.of(group)));
      userService.updateUser(connectorUser.get(), input);
    }
  }

  private Role createWellKnownRole() {
    Optional<Role> processStixRole = roleService.findById(PROCESS_STIX_ROLE_ID);
    if (processStixRole.isEmpty()) {
      processStixRole =
          Optional.of(
              roleService.createRole(
                  PROCESS_STIX_ROLE_ID,
                  PROCESS_STIX_ROLE_NAME,
                  PROCESS_STIX_ROLE_DESCRIPTION,
                  PROCESS_STIX_ROLE_CAPABILITIES));
    } else {
      processStixRole =
          Optional.of(
              roleService.updateRole(
                  PROCESS_STIX_ROLE_ID,
                  PROCESS_STIX_ROLE_NAME,
                  PROCESS_STIX_ROLE_DESCRIPTION,
                  PROCESS_STIX_ROLE_CAPABILITIES));
    }
    return processStixRole.get();
  }

  private Group createWellKnownGroupWithRole(Role role) {
    Optional<Group> processStixGroup = groupService.findById(PROCESS_STIX_GROUP_ID);

    GroupCreateInput input = new GroupCreateInput();
    input.setName(PROCESS_STIX_GROUP_NAME);
    input.setDescription(PROCESS_STIX_GROUP_DESCRIPTION);
    input.setDefaultUserAssignation(false);

    processStixGroup =
        processStixGroup
            .map(
                group ->
                    groupService.updateGroupInfoWithRoles(
                        group, input, new ArrayList<>(List.of(role))))
            .or(
                () ->
                    Optional.of(
                        groupService.createGroupWithRole(
                            PROCESS_STIX_GROUP_ID, input, new ArrayList<>(List.of(role)))));
    return processStixGroup.get();
  }
}
