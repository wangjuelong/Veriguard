package io.veriguard.utils.fixtures;

import static io.veriguard.database.model.CustomDashboardParameters.CustomDashboardParameterType.*;

import io.veriguard.database.model.CustomDashboard;

public class CustomDashboardFixture {

  public static final String NAME = "Custom Dashboard";

  public static CustomDashboard createDefaultCustomDashboard() {
    CustomDashboard customDashboard = new CustomDashboard();
    customDashboard.setName(NAME);
    return customDashboard;
  }

  public static CustomDashboard createCustomDashboardWithDefaultParams() {
    CustomDashboard customDashboard = new CustomDashboard();
    customDashboard.setName(NAME);
    return customDashboard
        .addParameter("Time range", timeRange)
        .addParameter("Start date", startDate)
        .addParameter("End date", endDate);
  }
}
