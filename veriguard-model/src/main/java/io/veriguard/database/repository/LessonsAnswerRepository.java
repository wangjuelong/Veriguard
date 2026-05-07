package io.veriguard.database.repository;

import io.veriguard.database.model.LessonsAnswer;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonsAnswerRepository
    extends CrudRepository<LessonsAnswer, String>, JpaSpecificationExecutor<LessonsAnswer> {

  @NotNull
  Optional<LessonsAnswer> findById(@NotNull String id);

  Optional<LessonsAnswer> findByUserIdAndQuestionId(
      @NotNull String userId, @NotNull String questionId);

  @Modifying(clearAutomatically = true)
  @Query(
      """
    delete from LessonsAnswer la
    where la.question.category.attackChainRun.id = :attackChainRunId
""")
  void deleteAllLessonsAnswersQuestionsCategoriesByAttackChainRunId(String attackChainRunId);
}
