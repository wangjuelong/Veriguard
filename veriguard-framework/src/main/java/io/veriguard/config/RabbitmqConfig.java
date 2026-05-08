package io.veriguard.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for RabbitMQ connection.
 *
 * <p>This component holds all configuration needed to connect to a RabbitMQ instance, including:
 *
 * <ul>
 *   <li>Connection details (hostname, port, virtual host)
 *   <li>Authentication (username, password)
 *   <li>SSL/TLS settings (enabled, trust store)
 *   <li>Management API settings
 * </ul>
 *
 * <p>Configuration is provided via properties with the {@code veriguard.rabbitmq.*} prefix.
 */
@Component
@Data
public class RabbitmqConfig {

  /** Prefix for queue and exchange names to namespace the platform's queues. */
  @JsonProperty("rabbitmq_prefix")
  @Value("${veriguard.rabbitmq.prefix:#{null}}")
  private String prefix;

  /** The hostname of the RabbitMQ server. */
  @JsonProperty("rabbitmq_hostname")
  @Value("${veriguard.rabbitmq.hostname:#{null}}")
  private String hostname;

  /** The virtual host to connect to. */
  @JsonProperty("rabbitmq_vhost")
  @Value("${veriguard.rabbitmq.vhost:#{null}}")
  private String vhost;

  /** Whether SSL/TLS should be used for the connection. */
  @JsonProperty("rabbitmq_ssl")
  @Value("${veriguard.rabbitmq.ssl:false}")
  private boolean ssl;

  /** The AMQP port of the RabbitMQ server (default: 5672). */
  @JsonProperty("rabbitmq_port")
  @Value("${veriguard.rabbitmq.port:5672}")
  private int port;

  /** The management API port of the RabbitMQ server (default: 15672). */
  @JsonProperty("rabbitmq_management_port")
  @Value("${veriguard.rabbitmq.management-port:15672}")
  private int managementPort;

  /** The username for RabbitMQ authentication. */
  @JsonProperty("rabbitmq_user")
  @Value("${veriguard.rabbitmq.user:#{null}}")
  private String user;

  /** The password for RabbitMQ authentication. */
  @JsonIgnore
  @Value("${veriguard.rabbitmq.pass:#{null}}")
  private String pass;

  /** The queue type (e.g., "classic", "quorum"). */
  @JsonProperty("rabbitmq_queue_type")
  @Value("${veriguard.rabbitmq.queue-type:#{null}}")
  private String queueType;

  /** Whether to allow insecure connections to the management API. */
  @JsonProperty("rabbitmq_management_insecure")
  @Value("${veriguard.rabbitmq.management-insecure:false}")
  private boolean managementInsecure;

  /** The password for the SSL trust store. */
  @JsonIgnore
  @Value("${veriguard.rabbitmq.trust-store-password:#{null}}")
  private String trustStorePassword;

  /** The SSL trust store resource containing trusted certificates. */
  @JsonIgnore
  @Value("${veriguard.rabbitmq.trust-store:#{null}}")
  private Resource trustStore;
}
