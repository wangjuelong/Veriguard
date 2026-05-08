package io.veriguard.rest.user;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.specification.TokenSpecification.fromUser;
import static io.veriguard.helper.DatabaseHelper.updateRelation;

import io.veriguard.aop.RBAC;
import io.veriguard.config.SessionManager;
import io.veriguard.database.model.Token;
import io.veriguard.database.model.User;
import io.veriguard.database.repository.OrganizationRepository;
import io.veriguard.database.repository.TokenRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.InputValidationException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.user.form.me.UpdateMePasswordInput;
import io.veriguard.rest.user.form.me.UpdateProfileInput;
import io.veriguard.rest.user.form.user.RenewTokenInput;
import io.veriguard.rest.user.form.user.UpdateUserInfoInput;
import io.veriguard.service.UserService;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
public class MeApi extends RestBehavior {

  public static final String ME_URI = "/api/me";

  @Resource private SessionManager sessionManager;

  private OrganizationRepository organizationRepository;
  private TokenRepository tokenRepository;
  private UserRepository userRepository;
  private UserService userService;

  @Autowired
  public void setOrganizationRepository(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  @Autowired
  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  @Autowired
  public void setUserRepository(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Autowired
  public void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  @GetMapping("/api/logout")
  @RBAC(skipRBAC = true)
  public ResponseEntity<Object> logout() {
    return ResponseEntity.ok().build();
  }

  @GetMapping("/api/me")
  @RBAC(skipRBAC = true)
  public User me() {
    return userRepository
        .findById(currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
  }

  @PutMapping("/api/me/profile")
  @RBAC(skipRBAC = true)
  public User updateProfile(@Valid @RequestBody UpdateProfileInput input) {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    user.setUpdateAttributes(input);
    user.setOrganization(
        updateRelation(input.getOrganizationId(), user.getOrganization(), organizationRepository));
    User savedUser = userRepository.save(user);
    sessionManager.refreshUserSessions(savedUser);
    return savedUser;
  }

  @PutMapping("/api/me/information")
  @RBAC(skipRBAC = true)
  public User updateInformation(@Valid @RequestBody UpdateUserInfoInput input) {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    user.setUpdateAttributes(input);
    User savedUser = userRepository.save(user);
    sessionManager.refreshUserSessions(savedUser);
    return savedUser;
  }

  @PutMapping("/api/me/password")
  @RBAC(skipRBAC = true)
  public User updatePassword(@Valid @RequestBody UpdateMePasswordInput input)
      throws InputValidationException {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    if (userService.isUserPasswordValid(user, input.getCurrentPassword())) {
      user.setPassword(userService.encodeUserPassword(input.getPassword()));
      return userRepository.save(user);
    } else {
      throw new InputValidationException("user_current_password", "Bad current password");
    }
  }

  @PostMapping("/api/me/token/refresh")
  @RBAC(skipRBAC = true)
  @Transactional(rollbackOn = Exception.class)
  public Token renewToken(@Valid @RequestBody RenewTokenInput input)
      throws InputValidationException {
    User user =
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
    Token token =
        tokenRepository.findById(input.getTokenId()).orElseThrow(ElementNotFoundException::new);
    if (!user.equals(token.getUser())) {
      throw new AccessDeniedException("You are not allowed to renew this token");
    }
    token.setValue(UUID.randomUUID().toString());
    return tokenRepository.save(token);
  }

  @GetMapping("/api/me/tokens")
  @RBAC(skipRBAC = true)
  public List<Token> tokens() {
    return tokenRepository.findAll(fromUser(currentUser().getId()));
  }
}
