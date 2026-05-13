package io.veriguard.combination.severity;

import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.Tag;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 单测 —— PR D4 AssetSensitivityScorer 标签匹配. */
class AssetSensitivityScorerTest {

  private final AssetSensitivityScorer scorer = new AssetSensitivityScorer();

  @Test
  void high_keyword_returns_100() {
    Asset asset = newAsset(tag("production"));
    assertThat(scorer.scoreFor(asset)).isEqualTo(AssetSensitivityScorer.HIGH_SENSITIVITY);

    Asset criticalAsset = newAsset(tag("critical-db"));
    assertThat(scorer.scoreFor(criticalAsset)).isEqualTo(AssetSensitivityScorer.HIGH_SENSITIVITY);
  }

  @Test
  void low_keyword_returns_25() {
    Asset asset = newAsset(tag("dev"));
    assertThat(scorer.scoreFor(asset)).isEqualTo(AssetSensitivityScorer.LOW_SENSITIVITY);

    Asset testAsset = newAsset(tag("test-env"));
    assertThat(scorer.scoreFor(testAsset)).isEqualTo(AssetSensitivityScorer.LOW_SENSITIVITY);
  }

  @Test
  void no_matching_tag_returns_default_50() {
    Asset asset = newAsset(tag("random-label"));
    assertThat(scorer.scoreFor(asset)).isEqualTo(AssetSensitivityScorer.DEFAULT_SENSITIVITY);

    Asset noTagsAsset = new Asset();
    noTagsAsset.setTags(new HashSet<>());
    assertThat(scorer.scoreFor(noTagsAsset))
        .isEqualTo(AssetSensitivityScorer.DEFAULT_SENSITIVITY);
  }

  @Test
  void null_asset_returns_default() {
    assertThat(scorer.scoreFor(null)).isEqualTo(AssetSensitivityScorer.DEFAULT_SENSITIVITY);
  }

  @Test
  void high_keyword_wins_over_low() {
    Asset asset = newAsset(tag("production"), tag("dev"));
    assertThat(scorer.scoreFor(asset)).isEqualTo(AssetSensitivityScorer.HIGH_SENSITIVITY);
  }

  // ============================================================
  // helpers
  // ============================================================

  private static Asset newAsset(Tag... tags) {
    Asset a = new Asset();
    Set<Tag> set = new HashSet<>();
    for (Tag t : tags) {
      set.add(t);
    }
    a.setTags(set);
    return a;
  }

  private static Tag tag(String name) {
    Tag t = new Tag();
    t.setId("tag-" + name);
    t.setName(name);
    return t;
  }
}
