package io.veriguard.service;

import io.veriguard.database.model.Exercise;
import io.veriguard.database.model.InjectExpectation;
import io.veriguard.database.repository.ExerciseRepository;
import io.veriguard.database.repository.InjectExpectationRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ExerciseExpectationService {

  private final InjectExpectationRepository injectExpectationRepository;
  private final ExerciseRepository exerciseRepository;

  public List<InjectExpectation> injectExpectations(@NotBlank final String exerciseId) {
    Exercise exercise =
        this.exerciseRepository
            .findById(exerciseId)
            .orElseThrow(
                () -> new ElementNotFoundException("Exercise not found with id: " + exerciseId));
    return this.injectExpectationRepository.findAllForExercise(exercise.getId());
  }
}
