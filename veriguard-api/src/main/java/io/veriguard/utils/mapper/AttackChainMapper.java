package io.veriguard.utils.mapper;

import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawAttackChain;
import io.veriguard.rest.attack_chain.form.AttackChainSimple;
import io.veriguard.rest.attack_chain.response.AttackChainOutput;
import io.veriguard.rest.attack_chain.response.AttackChainTeamUserOutput;
import io.veriguard.rest.document.form.RelatedEntityOutput;
import io.veriguard.rest.kill_chain_phase.response.KillChainPhaseOutput;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting AttackChain entities to output DTOs.
 *
 * <p>Provides methods for transforming attackChain domain objects and raw database results into API
 * response objects, including simple representations and full output formats.
 *
 * @see io.veriguard.database.model.AttackChain
 * @see io.veriguard.rest.attack_chain.form.AttackChainSimple
 * @see io.veriguard.rest.attack_chain.response.AttackChainOutput
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class AttackChainMapper {

  /**
   * Converts a attackChain entity to a simplified DTO.
   *
   * @param attackChain the attackChain to convert (must not be null)
   * @return the simplified attackChain DTO
   */
  public AttackChainSimple toAttackChainSimple(@NotNull final AttackChain attackChain) {
    AttackChainSimple simple = new AttackChainSimple();
    BeanUtils.copyProperties(attackChain, simple);
    return simple;
  }

  /**
   * Converts raw attackChain data to a full output DTO.
   *
   * <p>Assembles a comprehensive attackChain output from raw database results and pre-resolved
   * related entities.
   *
   * @param rawAttackChain the raw attackChain data
   * @param killChainPhases the resolved kill chain phases
   * @param attackChainTeamUsers the resolved team users
   * @return the full attackChain output DTO
   */
  public AttackChainOutput toAttackChainOutput(
      RawAttackChain rawAttackChain,
      Set<KillChainPhaseOutput> killChainPhases,
      Set<AttackChainTeamUserOutput> attackChainTeamUsers) {

    return AttackChainOutput.builder()
        .id(rawAttackChain.getAttack_chain_id())
        .name(rawAttackChain.getAttack_chain_name())
        .category(rawAttackChain.getAttack_chain_category())
        .createdAt(rawAttackChain.getAttack_chain_created_at())
        .updatedAt(rawAttackChain.getAttack_chain_updated_at())
        .customDashboard(rawAttackChain.getAttack_chain_custom_dashboard())
        .description(rawAttackChain.getAttack_chain_description())
        .externalUrl(rawAttackChain.getAttack_chain_external_url())
        .lessonsAnonymized(rawAttackChain.getAttack_chain_lessons_anonymized())
        .from(rawAttackChain.getAttack_chain_mail_from())
        .mainFocus(rawAttackChain.getAttack_chain_main_focus())
        .footer(rawAttackChain.getAttack_chain_message_footer())
        .header(rawAttackChain.getAttack_chain_message_header())
        .recurrence(rawAttackChain.getAttack_chain_recurrence())
        .recurrenceStart(rawAttackChain.getAttack_chain_recurrence_start())
        .recurrenceEnd(rawAttackChain.getAttack_chain_recurrence_end())
        .subtitle(rawAttackChain.getAttack_chain_subtitle())
        .dependencies(rawAttackChain.getAttack_chain_dependencies())
        .severity(rawAttackChain.getAttack_chain_severity())
        .typeAffinity(rawAttackChain.getAttack_chain_type_affinity())
        .attackChainRuns(rawAttackChain.getAttack_chain_attackChainRuns())
        .killChainPhases(killChainPhases)
        .platforms(rawAttackChain.getAttack_chain_platforms())
        .tags(rawAttackChain.getAttack_chain_tags())
        .teamUsers(attackChainTeamUsers)
        .attackChainUsersNumber(
            rawAttackChain.getAttack_chain_users_number() != null
                ? rawAttackChain.getAttack_chain_users_number()
                : 0)
        .attackChainAllUsersNumber(
            rawAttackChain.getAttack_chain_all_users_number() != null
                ? rawAttackChain.getAttack_chain_all_users_number()
                : 0)
        .build();
  }

  /**
   * Converts a set of attackChainNodes to related entity outputs with attackChain context.
   *
   * @param attackChainNodes the attackChainNodes to convert
   * @return set of related entity output DTOs including attackChain context
   */
  public static Set<RelatedEntityOutput> toRelatedEntityOutputsForChain(
      Set<AttackChainNode> attackChainNodes) {
    return attackChainNodes.stream()
        .map(attackChainNode -> toRelatedEntityOutputForChain(attackChainNode))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutputForChain(AttackChainNode attackChainNode) {
    return RelatedEntityOutput.builder()
        .id(attackChainNode.getId())
        .name(attackChainNode.getTitle())
        .context(attackChainNode.getAttackChain().getId())
        .build();
  }
}
