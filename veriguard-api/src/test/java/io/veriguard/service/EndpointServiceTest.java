package io.veriguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.AssetAgentJob;
import io.veriguard.database.repository.AssetAgentJobRepository;
import io.veriguard.rest.asset.endpoint.form.EndpointRegisterInput;
import io.veriguard.utils.fixtures.AgentFixture;
import io.veriguard.utils.fixtures.AssetAgentJobFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class EndpointServiceTest {

  @Mock private AssetAgentJobRepository assetAgentJobRepository;

  @InjectMocks private EndpointService endpointService;

  @Nested
  @DisplayName("getEndpointJobs")
  class GetEndpointJobs {

    @Test
    void given_serviceModeInput_should_returnMatchingJobs() {
      // Arrange
      Agent agent = AgentFixture.createDefaultAgentService();
      AssetAgentJob job = AssetAgentJobFixture.createDefaultAssetAgentJob(agent);

      EndpointRegisterInput input = new EndpointRegisterInput();
      input.setExternalReference("ref-001");
      input.setService(true);
      input.setElevated(true);
      input.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);

      when(assetAgentJobRepository.findAll(any(Specification.class))).thenReturn(List.of(job));

      // Act
      List<AssetAgentJob> result = endpointService.getEndpointJobs(input);

      // Assert
      assertThat(result).containsExactly(job);
    }

    @Test
    void given_noMatchingJobs_should_returnEmptyList() {
      // Arrange
      EndpointRegisterInput input = new EndpointRegisterInput();
      input.setExternalReference("ref-unknown");
      input.setService(true);
      input.setElevated(true);
      input.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);

      when(assetAgentJobRepository.findAll(any(Specification.class))).thenReturn(List.of());

      // Act
      List<AssetAgentJob> result = endpointService.getEndpointJobs(input);

      // Assert
      assertThat(result).isEmpty();
    }
  }
}
