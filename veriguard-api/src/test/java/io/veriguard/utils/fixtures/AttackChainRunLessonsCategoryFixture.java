package io.veriguard.utils.fixtures;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.LessonsCategory;
import io.veriguard.database.model.Team;
import java.util.List;

public class AttackChainRunLessonsCategoryFixture {

  public static final String LESSON_CATEGORY_NAME = "Category name";
  public static final String LESSON_CATEGORY_DESCRIPTION = "Category description";
  public static final int LESSON_CATEGORY_ORDER = 0;

  public static LessonsCategory getLessonsCategory(
      AttackChainRun attackChainRun, List<Team> categoryTeams) {
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setAttackChainRun(attackChainRun);
    lessonsCategory.setName(LESSON_CATEGORY_NAME);
    lessonsCategory.setDescription(LESSON_CATEGORY_DESCRIPTION);
    lessonsCategory.setOrder(LESSON_CATEGORY_ORDER);
    lessonsCategory.setTeams(categoryTeams);
    return lessonsCategory;
  }
}
