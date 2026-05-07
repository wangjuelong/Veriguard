package io.veriguard.database.repository;

import io.veriguard.database.model.Pause;
import io.veriguard.database.raw.RawPause;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PauseRepository
    extends CrudRepository<Pause, String>, JpaSpecificationExecutor<Pause> {

  @NotNull
  Optional<Pause> findById(@NotNull String id);

  @Query(value = "select p from Pause p where p.attackChainRun.id = :attackChainRunId")
  List<Pause> findAllForAttackChainRun(@Param("attackChainRunId") String attackChainRunId);

  @Query(
      value =
          "SELECT p.pause_id, p.pause_date, p.pause_duration, p.pause_exercise "
              + "FROM pauses p "
              + "WHERE p.pause_exercise = :attackChainRunId",
      nativeQuery = true)
  List<RawPause> rawAllForAttackChainRun(@Param("attackChainRunId") String attackChainRunId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM Pause p WHERE p.attackChainRun.id = :attackChainRunId")
  void deleteAllPauseByAttackChainRunId(String attackChainRunId);
}
