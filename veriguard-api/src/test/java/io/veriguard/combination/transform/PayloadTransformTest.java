package io.veriguard.combination.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 单元测试：每个 PayloadTransform 的核心行为. */
class PayloadTransformTest {

  @Nested
  class IdentityTransformTests {
    private final IdentityTransform transform = new IdentityTransform();

    @Test
    void type_returns_identity() {
      assertThat(transform.type()).isEqualTo("identity");
    }

    @Test
    void apply_returns_input_unchanged() {
      assertThat(transform.apply("OR 1=1", Map.of())).isEqualTo("OR 1=1");
    }

    @Test
    void apply_null_payload_throws() {
      assertThatThrownBy(() -> transform.apply(null, Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class Base64EncodeTransformTests {
    private final Base64EncodeTransform transform = new Base64EncodeTransform();

    @Test
    void apply_default_encodes_ascii() {
      // "OR 1=1" → standard base64
      assertThat(transform.apply("OR 1=1", Map.of())).isEqualTo("T1IgMT0x");
    }

    @Test
    void apply_url_safe_uses_dash_underscore() {
      // bytes likely to produce + or / in standard base64
      String input = "?>?>?>?>";
      String standard = transform.apply(input, Map.of());
      String urlSafe = transform.apply(input, Map.of("url_safe", true));
      // url-safe replaces + → - and / → _
      assertThat(urlSafe).doesNotContain("+").doesNotContain("/");
      assertThat(standard.length()).isEqualTo(urlSafe.length());
    }

    @Test
    void apply_no_padding_omits_equals() {
      // "OR" (2 bytes) → standard "T1I=" (4 chars w/ padding)
      String padded = transform.apply("OR", Map.of());
      String noPad = transform.apply("OR", Map.of("padding", false));
      assertThat(padded).endsWith("=");
      assertThat(noPad).doesNotContain("=");
    }

    @Test
    void apply_with_invalid_config_type_throws() {
      assertThatThrownBy(() -> transform.apply("x", Map.of("url_safe", "yes")))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class UrlEncodeTransformTests {
    private final UrlEncodeTransform transform = new UrlEncodeTransform();

    @Test
    void apply_default_encodes_special_chars() {
      assertThat(transform.apply("OR 1=1", Map.of())).isEqualTo("OR+1%3D1");
    }

    @Test
    void apply_double_encode_re_encodes_percent() {
      String single = transform.apply("a b", Map.of()); // "a+b"
      String dbl = transform.apply("a b", Map.of("double_encode", true));
      assertThat(dbl).isNotEqualTo(single);
      // "a+b" → "a%2Bb" (the + sign gets re-encoded)
      assertThat(dbl).isEqualTo("a%2Bb");
    }
  }

  @Nested
  class MixedCaseTransformTests {
    private final MixedCaseTransform transform = new MixedCaseTransform();

    @Test
    void apply_alternate_alternates_case() {
      assertThat(transform.apply("select", Map.of("strategy", "alternate"))).isEqualTo("SeLeCt");
    }

    @Test
    void apply_upper_uppercases() {
      assertThat(transform.apply("select", Map.of("strategy", "upper"))).isEqualTo("SELECT");
    }

    @Test
    void apply_lower_lowercases() {
      assertThat(transform.apply("SELECT", Map.of("strategy", "lower"))).isEqualTo("select");
    }

    @Test
    void apply_invert_flips_case() {
      assertThat(transform.apply("SeLeCt", Map.of("strategy", "invert"))).isEqualTo("sElEcT");
    }

    @Test
    void apply_missing_strategy_throws() {
      assertThatThrownBy(() -> transform.apply("x", Map.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("strategy");
    }

    @Test
    void apply_unknown_strategy_throws() {
      assertThatThrownBy(() -> transform.apply("x", Map.of("strategy", "wat")))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class HttpChunkSplitTransformTests {
    private final HttpChunkSplitTransform transform = new HttpChunkSplitTransform();

    @Test
    void apply_splits_payload_into_hex_length_chunks() {
      // "ABCDE" with chunk_size=2 → "2\r\nAB\r\n2\r\nCD\r\n1\r\nE\r\n0\r\n\r\n"
      String result = transform.apply("ABCDE", Map.of("chunk_size", 2));
      assertThat(result).isEqualTo("2\r\nAB\r\n2\r\nCD\r\n1\r\nE\r\n0\r\n\r\n");
    }

    @Test
    void apply_with_chunk_size_one_splits_each_byte() {
      String result = transform.apply("AB", Map.of("chunk_size", 1));
      assertThat(result).isEqualTo("1\r\nA\r\n1\r\nB\r\n0\r\n\r\n");
    }

    @Test
    void apply_with_zero_chunk_size_throws() {
      assertThatThrownBy(() -> transform.apply("X", Map.of("chunk_size", 0)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class NoiseSurroundTransformTests {
    private final NoiseSurroundTransform transform = new NoiseSurroundTransform();

    @Test
    void apply_default_returns_payload_unchanged() {
      // no prefix / no suffix
      assertThat(transform.apply("X", Map.of())).isEqualTo("X");
    }

    @Test
    void apply_with_prefix_and_suffix_wraps_payload() {
      assertThat(transform.apply("X", Map.of("prefix", "<<", "suffix", ">>")))
          .isEqualTo("<<X>>");
    }

    @Test
    void apply_with_repeat_applies_repeat_times() {
      assertThat(transform.apply("X", Map.of("prefix", "a", "suffix", "b", "repeat", 3)))
          .isEqualTo("aaaXbbb");
    }

    @Test
    void apply_with_zero_repeat_throws() {
      assertThatThrownBy(
              () -> transform.apply("X", Map.of("prefix", "a", "suffix", "b", "repeat", 0)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class UnicodeFullWidthTransformTests {
    private final UnicodeFullWidthTransform transform = new UnicodeFullWidthTransform();

    @Test
    void apply_maps_ascii_to_fullwidth() {
      // 'A' (0x41) → 0xFF21
      String r = transform.apply("A", Map.of());
      assertThat(r).hasSize(1);
      assertThat((int) r.charAt(0)).isEqualTo(0xFF21);
    }

    @Test
    void apply_maps_space_to_ideographic_space() {
      String r = transform.apply(" ", Map.of());
      assertThat((int) r.charAt(0)).isEqualTo(0x3000);
    }

    @Test
    void apply_preserves_non_ascii() {
      String r = transform.apply("中", Map.of());
      assertThat(r).isEqualTo("中");
    }
  }

  @Nested
  class HashCommentInjectTransformTests {
    private final HashCommentInjectTransform transform = new HashCommentInjectTransform();

    @Test
    void apply_sql_block_replaces_spaces() {
      assertThat(
              transform.apply(
                  "SELECT * FROM users",
                  Map.of("style", "sql_block", "position", "between_tokens")))
          .isEqualTo("SELECT/**/*/**/FROM/**/users");
    }

    @Test
    void apply_html_comment_prefix() {
      assertThat(transform.apply("<script>x</script>", Map.of("style", "html_comment", "position", "prefix")))
          .isEqualTo("<!----><script>x</script>");
    }

    @Test
    void apply_sql_line_suffix() {
      assertThat(transform.apply("DROP TABLE", Map.of("style", "sql_line", "position", "suffix")))
          .isEqualTo("DROP TABLE-- ");
    }

    @Test
    void apply_unknown_style_throws() {
      assertThatThrownBy(
              () ->
                  transform.apply(
                      "x", Map.of("style", "foo", "position", "between_tokens")))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class ParamOrderShuffleTransformTests {
    private final ParamOrderShuffleTransform transform = new ParamOrderShuffleTransform();

    @Test
    void apply_reverse_reverses_param_order() {
      assertThat(transform.apply("a=1&b=2&c=3", Map.of("strategy", "reverse")))
          .isEqualTo("c=3&b=2&a=1");
    }

    @Test
    void apply_sort_asc_orders_by_key() {
      assertThat(transform.apply("c=3&a=1&b=2", Map.of("strategy", "sort_asc")))
          .isEqualTo("a=1&b=2&c=3");
    }

    @Test
    void apply_sort_desc_orders_by_key_desc() {
      assertThat(transform.apply("a=1&c=3&b=2", Map.of("strategy", "sort_desc")))
          .isEqualTo("c=3&b=2&a=1");
    }

    @Test
    void apply_with_custom_separator_uses_it() {
      assertThat(
              transform.apply(
                  "a=1;c=3;b=2", Map.of("strategy", "sort_asc", "separator", ";")))
          .isEqualTo("a=1;b=2;c=3");
    }
  }
}
