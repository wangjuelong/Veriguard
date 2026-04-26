package io.veriguard.utils.fixtures;

import io.veriguard.database.model.Exercise;
import io.veriguard.database.model.ExerciseTeamUser;
import io.veriguard.database.model.Team;
import io.veriguard.database.model.User;

public class ExerciseTeamUserFixture {

  public static ExerciseTeamUser createExerciseTeamUser(Exercise exercise, Team team, User user) {
    ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
    exerciseTeamUser.setExercise(exercise);
    exerciseTeamUser.setTeam(team);
    exerciseTeamUser.setUser(user);
    return exerciseTeamUser;
  }
}
