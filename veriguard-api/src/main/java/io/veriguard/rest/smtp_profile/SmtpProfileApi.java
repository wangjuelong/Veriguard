package io.veriguard.rest.smtp_profile;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.SmtpProfile;
import io.veriguard.rest.smtp_profile.form.SmtpProfileInput;
import io.veriguard.service.SmtpProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SmtpProfileApi {

  public static final String SMTP_PROFILE_URI = "/api/smtp_profiles";

  private final SmtpProfileService service;

  @LogExecutionTime
  @GetMapping(SMTP_PROFILE_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SCENARIO)
  public List<SmtpProfile> findAll() {
    return service.findAll();
  }

  @LogExecutionTime
  @GetMapping(SMTP_PROFILE_URI + "/{id}")
  @RBAC(resourceId = "#id", actionPerformed = Action.READ, resourceType = ResourceType.SCENARIO)
  public SmtpProfile findById(@PathVariable @NotBlank final String id) {
    return service
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("SMTP profile not found: " + id));
  }

  @LogExecutionTime
  @PostMapping(SMTP_PROFILE_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
  public SmtpProfile create(@Valid @RequestBody final SmtpProfileInput input) {
    SmtpProfile profile = new SmtpProfile();
    input.toEntity(profile);
    return service.create(profile);
  }

  @LogExecutionTime
  @PutMapping(SMTP_PROFILE_URI + "/{id}")
  @RBAC(resourceId = "#id", actionPerformed = Action.WRITE, resourceType = ResourceType.SCENARIO)
  public SmtpProfile update(
      @PathVariable @NotBlank final String id, @Valid @RequestBody final SmtpProfileInput input) {
    SmtpProfile patch = new SmtpProfile();
    input.toEntity(patch);
    return service.update(id, patch);
  }

  @LogExecutionTime
  @DeleteMapping(SMTP_PROFILE_URI + "/{id}")
  @RBAC(resourceId = "#id", actionPerformed = Action.DELETE, resourceType = ResourceType.SCENARIO)
  public void delete(@PathVariable @NotBlank final String id) {
    service.delete(id);
  }
}
