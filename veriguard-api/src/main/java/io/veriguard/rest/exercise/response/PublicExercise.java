package io.veriguard.rest.exercise.response;

import io.veriguard.database.model.Exercise;
import io.veriguard.rest.challenge.output.PublicEntity;
import lombok.Getter;

@Getter
public class PublicExercise extends PublicEntity {

  public PublicExercise(Exercise exercise) {
    setId(exercise.getId());
    setName(exercise.getName());
    setDescription(exercise.getDescription());
  }
}
