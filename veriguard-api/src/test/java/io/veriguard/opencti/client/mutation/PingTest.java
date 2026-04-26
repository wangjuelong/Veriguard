package io.veriguard.opencti.client.mutation;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.opencti.client.mutations.Ping;
import io.veriguard.opencti.connectors.ConnectorBase;
import io.veriguard.utils.fixtures.opencti.ConnectorFixture;
import io.veriguard.utilstest.RabbitMQTestListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class PingTest {
  @Test
  @DisplayName("When Ping mutation is passed a connector, variables are correctly interpolated")
  public void whenPingMutationIsPassedAConnector_variablesAreCorrectlyLoaded()
      throws JsonProcessingException {
    ConnectorBase cb = ConnectorFixture.getDefaultConnector();

    Ping ping = new Ping(cb);

    // most data here is static anyway
    // but the connector ID must be interpolated
    assertThatJson(ping.getVariables())
        .isEqualTo(
            """
          {
            "id":"%s",
            "state":null,
            "connectorInfo": {
              "run_and_terminate":false,
              "buffering":false,
              "queue_threshold":0.0,
              "queue_messages_size":0.0,
              "next_run_datetime":null,
              "last_run_datetime":null
            }
          }
          """
                .formatted(cb.getId()));
  }
}
