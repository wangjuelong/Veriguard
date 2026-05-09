package io.veriguard.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.helper.MonoIdSerializer;
import io.veriguard.helper.MultiIdListSerializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "lessons_questions")
@EntityListeners(ModelBaseListener.class)
public class LessonsQuestion implements Base {

  @Id
  @Column(name = "lessons_question_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("lessonsquestion_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lessons_question_category")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("lessons_question_category")
  @NotNull
  @Schema(type = "string")
  private LessonsCategory category;

  @Column(name = "lessons_question_created_at")
  @JsonProperty("lessons_question_created_at")
  @NotNull
  private Instant created = now();

  @Column(name = "lessons_question_updated_at")
  @JsonProperty("lessons_question_updated_at")
  @NotNull
  private Instant updated = now();

  @Column(name = "lessons_question_content")
  @JsonProperty("lessons_question_content")
  @NotBlank
  private String content;

  @Column(name = "lessons_question_explanation")
  @JsonProperty("lessons_question_explanation")
  private String explanation;

  @Column(name = "lessons_question_order")
  @JsonProperty("lessons_question_order")
  private int order;

  @OneToMany(mappedBy = "question", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonProperty("lessons_question_answers")
  @JsonSerialize(using = MultiIdListSerializer.class)
  @ArraySchema(schema = @Schema(type = "string"))
  private List<LessonsAnswer> answers = new ArrayList<>();

  // region transient
  @JsonProperty("lessons_question_attack_chain_run")
  public String getAttackChainRun() {
    if (getCategory() == null) {
      return null;
    }
    return Optional.ofNullable(getCategory().getAttackChainRun())
        .map(AttackChainRun::getId)
        .orElse(null);
  }

  @JsonProperty("lessons_question_attack_chain")
  public String getAttackChain() {
    if (getCategory() == null) {
      return null;
    }
    return Optional.ofNullable(getCategory().getAttackChain()).map(AttackChain::getId).orElse(null);
  }

  // endregion

  @Override
  public boolean isUserHasAccess(User user) {
    if (getCategory() == null) {
      return user.isAdmin();
    }
    return getCategory().isUserHasAccess(user);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
