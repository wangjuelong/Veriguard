package io.veriguard.authorisation;

import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.rest.security.SecurityExpression;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Getter
@RequiredArgsConstructor
@Service
public class AuthorisationService {
  private final AttackChainRunRepository attackChainRunRepository;
  private final UserRepository userRepository;

  public SecurityExpression getSecurityExpression() {
    return new SecurityExpression(
        SecurityContextHolder.getContext().getAuthentication(),
        userRepository,
        attackChainRunRepository);
  }
}
