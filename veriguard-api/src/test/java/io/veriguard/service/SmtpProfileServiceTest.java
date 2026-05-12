package io.veriguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.SmtpProfile;
import io.veriguard.database.repository.SmtpProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmtpProfileServiceTest {

  @Mock private SmtpProfileRepository repository;

  @InjectMocks private SmtpProfileService service;

  private SmtpProfile profile(String name) {
    SmtpProfile p = new SmtpProfile();
    p.setName(name);
    p.setHost("smtp.example.com");
    p.setPort(587);
    p.setDefaultFrom("noreply@example.com");
    return p;
  }

  @Test
  @DisplayName("create 正常 → 保存并返回")
  void create_savesAndReturns() {
    SmtpProfile input = profile("prod-smtp");
    when(repository.findByName("prod-smtp")).thenReturn(Optional.empty());
    when(repository.save(input)).thenReturn(input);

    SmtpProfile result = service.create(input);

    assertThat(result).isSameAs(input);
    verify(repository).save(input);
  }

  @Test
  @DisplayName("create 重名 → 抛 IllegalArgumentException，不调 save")
  void create_duplicateName_throwsAndSkipsSave() {
    SmtpProfile input = profile("dup-smtp");
    when(repository.findByName("dup-smtp")).thenReturn(Optional.of(profile("dup-smtp")));

    assertThrows(IllegalArgumentException.class, () -> service.create(input));
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("findById 命中 → 返回 profile")
  void findById_hit_returnsProfile() {
    SmtpProfile p = profile("abc");
    p.setId("abc-id");
    when(repository.findById("abc-id")).thenReturn(Optional.of(p));

    Optional<SmtpProfile> result = service.findById("abc-id");

    assertThat(result).contains(p);
  }

  @Test
  @DisplayName("findById 不存在 → 返回 empty")
  void findById_miss_returnsEmpty() {
    when(repository.findById("nope")).thenReturn(Optional.empty());

    Optional<SmtpProfile> result = service.findById("nope");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findAll 返回 repository 全部")
  void findAll_returnsAll() {
    SmtpProfile p1 = profile("a");
    SmtpProfile p2 = profile("b");
    when(repository.findAll()).thenReturn(List.of(p1, p2));

    List<SmtpProfile> result = service.findAll();

    assertThat(result).containsExactly(p1, p2);
  }

  @Test
  @DisplayName("update 不存在的 id → 抛 IllegalArgumentException")
  void update_notFound_throws() {
    SmtpProfile input = profile("x");
    input.setId("ghost-id");
    when(repository.findById("ghost-id")).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.update("ghost-id", input));
  }

  @Test
  @DisplayName("delete 调 repository.deleteById")
  void delete_delegates() {
    service.delete("some-id");
    verify(repository).deleteById("some-id");
  }
}
