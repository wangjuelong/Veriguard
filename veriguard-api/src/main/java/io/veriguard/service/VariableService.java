package io.veriguard.service;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static java.time.Instant.now;

import io.veriguard.database.model.Variable;
import io.veriguard.database.repository.VariableRepository;
import io.veriguard.database.specification.VariableSpecification;
import io.veriguard.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class VariableService {

  private final VariableRepository variableRepository;

  public Variable createVariable(@NotNull final Variable variable) {
    return this.variableRepository.save(variable);
  }

  public List<Variable> createVariables(@NotNull final List<Variable> variables) {
    return fromIterable(this.variableRepository.saveAll(variables));
  }

  public Variable variable(@NotBlank final String variableId) {
    return this.variableRepository
        .findById(variableId)
        .orElseThrow(
            () -> new ElementNotFoundException("Variable not found with id: " + variableId));
  }

  public List<Variable> variablesFromAttackChainRun(@NotBlank final String attackChainRunId) {
    return this.variableRepository.findAll(
        VariableSpecification.fromAttackChainRun(attackChainRunId));
  }

  public List<Variable> variablesFromAttackChain(@NotBlank final String attackChainId) {
    return this.variableRepository.findAll(VariableSpecification.fromAttackChain(attackChainId));
  }

  public Variable updateVariable(@NotNull final Variable variable) {
    variable.setUpdatedAt(now());
    return this.variableRepository.save(variable);
  }

  public void deleteVariable(@NotBlank final String variableId) {
    this.variableRepository.deleteById(variableId);
  }

  public void copyVariableFromAttackChainForSimulation(
      @NotBlank final String attackChainId, @NotBlank final String attackChainRunId) {
    this.variableRepository.copyVariableFromAttackChainForSimulation(
        attackChainId, attackChainRunId);
  }
}
