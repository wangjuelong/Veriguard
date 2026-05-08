package io.veriguard.rest.injector.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeExecutorRegistration {

  @JsonProperty("connection")
  private NodeExecutorConnection connection;

  @JsonProperty("listen")
  private String listen;

  public NodeExecutorRegistration(NodeExecutorConnection connection, String listen) {
    this.connection = connection;
    this.listen = listen;
  }
}
