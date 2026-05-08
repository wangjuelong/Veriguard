package io.veriguard.utils.fixtures;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.AssetAgentJob;

public class AssetAgentJobFixture {

  public static AssetAgentJob createDefaultAssetAgentJob(Agent agent) {
    AssetAgentJob assetAgentJob = new AssetAgentJob();
    assetAgentJob.setCommand("whoami");
    assetAgentJob.setAgent(agent);
    return assetAgentJob;
  }
}
