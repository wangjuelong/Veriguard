package io.veriguard.rest.exercise;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Exercise;
import io.veriguard.database.repository.ExerciseRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exercise.response.PublicExercise;
import io.veriguard.rest.helper.RestBehavior;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExercisePlayerApi extends RestBehavior {

  public static final String EXERCISE_URI = "/api/player/exercises";

  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;

  @GetMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(skipRBAC = true)
  public PublicExercise playerExercise(
      @PathVariable String exerciseId, @RequestParam Optional<String> userId) {
    impersonateUser(this.userRepository, userId);
    Exercise exercise =
        this.exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    return new PublicExercise(exercise);
  }
}
