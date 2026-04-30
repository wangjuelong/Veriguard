package io.veriguard.rest.exercise.response;

import io.veriguard.database.model.Exercise;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicExercise {

  private String id;
  private String name;
  private String description;

  public PublicExercise(Exercise exercise) {
    this.id = exercise.getId();
    this.name = exercise.getName();
    this.description = exercise.getDescription();
  }
}
