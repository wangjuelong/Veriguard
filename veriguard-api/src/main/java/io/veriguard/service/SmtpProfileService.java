package io.veriguard.service;

import io.veriguard.database.model.SmtpProfile;
import io.veriguard.database.repository.SmtpProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SmtpProfileService {

  private final SmtpProfileRepository repository;

  @Transactional
  public SmtpProfile create(SmtpProfile profile) {
    Optional<SmtpProfile> existing = repository.findByName(profile.getName());
    if (existing.isPresent()) {
      throw new IllegalArgumentException("SMTP profile name already exists: " + profile.getName());
    }
    return repository.save(profile);
  }

  public Optional<SmtpProfile> findById(String id) {
    return repository.findById(id);
  }

  public List<SmtpProfile> findAll() {
    List<SmtpProfile> result = new ArrayList<>();
    repository.findAll().forEach(result::add);
    return result;
  }

  @Transactional
  public SmtpProfile update(String id, SmtpProfile updates) {
    SmtpProfile existing =
        repository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SMTP profile not found: " + id));
    existing.setName(updates.getName());
    existing.setHost(updates.getHost());
    existing.setPort(updates.getPort());
    existing.setAuthType(updates.getAuthType());
    existing.setUsername(updates.getUsername());
    existing.setPassword(updates.getPassword());
    existing.setTlsMode(updates.getTlsMode());
    existing.setDefaultFrom(updates.getDefaultFrom());
    existing.setDefaultReplyTo(updates.getDefaultReplyTo());
    return repository.save(existing);
  }

  @Transactional
  public void delete(String id) {
    repository.deleteById(id);
  }
}
