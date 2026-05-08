package io.veriguard.rest.attack_chain_node.output;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Asset;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public record AgentsAndAssetsAgentless(
    @NotNull Set<Agent> agents, @NotNull Set<Asset> assetsAgentless) {}
