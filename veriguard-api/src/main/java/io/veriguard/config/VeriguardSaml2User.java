package io.veriguard.config;

import io.veriguard.database.model.User;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;

public class VeriguardSaml2User
    implements VeriguardPrincipal, Saml2AuthenticatedPrincipal, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private final User user;
  private final List<SimpleGrantedAuthority> roles;

  public VeriguardSaml2User(
      @NotNull final User user, @NotNull final List<SimpleGrantedAuthority> roles) {
    this.user = user;
    this.roles = roles;
  }

  @Override
  public String getName() {
    return user.getName();
  }

  @Override
  public String getId() {
    return user.getId();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles;
  }

  @Override
  public boolean isAdmin() {
    return user.isAdmin();
  }

  @Override
  public String getLang() {
    return user.getLang();
  }
}
