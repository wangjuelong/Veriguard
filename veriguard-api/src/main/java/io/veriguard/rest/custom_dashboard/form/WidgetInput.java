package io.veriguard.rest.custom_dashboard.form;

import static io.veriguard.config.AppConfig.MANDATORY_MESSAGE;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.Widget;
import io.veriguard.database.model.WidgetLayout;
import io.veriguard.engine.api.WidgetConfiguration;
import io.veriguard.engine.api.WidgetType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WidgetInput {

  @JsonProperty("widget_type")
  @NotNull(message = MANDATORY_MESSAGE)
  private WidgetType type;

  @JsonProperty("widget_config")
  @NotNull(message = MANDATORY_MESSAGE)
  private WidgetConfiguration widgetConfiguration;

  @JsonProperty("widget_layout")
  @NotNull(message = MANDATORY_MESSAGE)
  private WidgetLayout widgetLayout;

  // -- METHOD --

  public Widget toWidget(@NotNull Widget widget) {
    requireNonNull(widget, "Widget must not be null.");

    widget.setType(this.getType());
    widget.setWidgetConfiguration(this.getWidgetConfiguration());
    widget.setLayout(this.getWidgetLayout());
    return widget;
  }
}
