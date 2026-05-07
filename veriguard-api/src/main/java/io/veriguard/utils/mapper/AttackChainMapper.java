package io.veriguard.utils.mapper;

import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawAttackChain;
import io.veriguard.rest.document.form.RelatedEntityOutput;
import io.veriguard.rest.kill_chain_phase.response.KillChainPhaseOutput;
import io.veriguard.rest.scenario.form.AttackChainSimple;
import io.veriguard.rest.scenario.response.AttackChainOutput;
import io.veriguard.rest.scenario.response.AttackChainTeamUserOutput;
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
 * @see io.veriguard.rest.scenario.form.AttackChainSimple
 * @see io.veriguard.rest.scenario.response.AttackChainOutput
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
   * <p>Assembles a comprehensive attackChain output from raw database results and pre-resolved related
   * entities.
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
        .id(rawAttackChain.getScenario_id())
        .name(rawAttackChain.getScenario_name())
        .category(rawAttackChain.getScenario_category())
        .createdAt(rawAttackChain.getScenario_created_at())
        .updatedAt(rawAttackChain.getScenario_updated_at())
        .customDashboard(rawAttackChain.getScenario_custom_dashboard())
        .description(rawAttackChain.getScenario_description())
        .externalUrl(rawAttackChain.getScenario_external_url())
        .lessonsAnonymized(rawAttackChain.getScenario_lessons_anonymized())
        .from(rawAttackChain.getScenario_mail_from())
        .mainFocus(rawAttackChain.getScenario_main_focus())
        .footer(rawAttackChain.getScenario_message_footer())
        .header(rawAttackChain.getScenario_message_header())
        .recurrence(rawAttackChain.getScenario_recurrence())
        .recurrenceStart(rawAttackChain.getScenario_recurrence_start())
        .recurrenceEnd(rawAttackChain.getScenario_recurrence_end())
        .subtitle(rawAttackChain.getScenario_subtitle())
        .dependencies(rawAttackChain.getScenario_dependencies())
        .severity(rawAttackChain.getScenario_severity())
        .typeAffinity(rawAttackChain.getScenario_type_affinity())
        .attackChainRuns(rawAttackChain.getScenario_attackChainRuns())
        .killChainPhases(killChainPhases)
        .platforms(rawAttackChain.getScenario_platforms())
        .tags(rawAttackChain.getScenario_tags())
        .teamUsers(attackChainTeamUsers)
        .attackChainUsersNumber(
            rawAttackChain.getScenario_users_number() != null
                ? rawAttackChain.getScenario_users_number()
                : 0)
        .attackChainAllUsersNumber(
            rawAttackChain.getScenario_all_users_number() != null
                ? rawAttackChain.getScenario_all_users_number()
                : 0)
        .build();
  }

  /**
   * Converts a set of attackChainNodes to related entity outputs with attackChain context.
   *
   * @param attackChainNodes the attackChainNodes to convert
   * @return set of related entity output DTOs including attackChain context
   */
  public static Set<RelatedEntityOutput> toAttackChainAttackChainNodes(Set<AttackChainNode> attackChainNodes) {
    return attackChainNodes.stream().map(attackChainNode -> toAttackChainAttackChainNode(attackChainNode)).collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toAttackChainAttackChainNode(AttackChainNode attackChainNode) {
    return RelatedEntityOutput.builder()
        .id(attackChainNode.getId())
        .name(attackChainNode.getTitle())
        .context(attackChainNode.getAttackChain().getId())
        .build();
  }
}
