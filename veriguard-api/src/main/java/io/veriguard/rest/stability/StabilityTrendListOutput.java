package io.veriguard.rest.stability;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 稳定性趋势分页响应（PR C5）.
 *
 * <p>使用统一的 meta 字段格式（total/page/limit）与项目其它分页响应对齐.
 */
public record StabilityTrendListOutput(
    @JsonProperty("items") List<StabilityTrendOutput> items,
    @JsonProperty("total") long total,
    @JsonProperty("page") int page,
    @JsonProperty("limit") int limit) {}
