package io.veriguard.executors.caldera.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.NodeExecutor;
import io.veriguard.executors.caldera.client.model.Ability;
import io.veriguard.executors.caldera.config.CalderaExecutorConfig;
import io.veriguard.executors.caldera.model.Agent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class CalderaExecutorClient {

  private static final String KEY_HEADER = "KEY";

  private final CalderaExecutorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClientFactory httpClientFactory;

  // -- AGENTS --

  private static final String AGENT_URI = "/agents";

  public List<Agent> agents() {
    try {
      String jsonResponse = this.get(AGENT_URI);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      log.error("Cannot retrieve agent list", e);
      throw new RuntimeException(e);
    }
  }

  public void deleteAgent(final String externalReference) {
    try {
      this.delete(this.config.getRestApiV2Url() + AGENT_URI + "/" + externalReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- ABILITIES --

  private static final String ABILITIES_URI = "/abilities";

  public List<Ability> abilities() {
    try {
      String jsonResponse = this.get(ABILITIES_URI);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Ability createSubprocessorAbility(NodeExecutor nodeExecutor) {
    try {
      List<Map<String, String>> executors = new ArrayList<>();
      Map<String, String> nodeExecutorExecutorCommands = nodeExecutor.getExecutorCommands();
      if (nodeExecutorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorWindows = new HashMap<>();
        executorWindows.put("platform", "windows");
        executorWindows.put("name", "psh");
        executorWindows.put(
            "command",
            nodeExecutorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorWindows);
      } else if (nodeExecutorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorWindows = new HashMap<>();
        executorWindows.put("platform", "windows");
        executorWindows.put("name", "psh");
        executorWindows.put(
            "command",
            nodeExecutorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorWindows);
      }
      if (nodeExecutorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorLinux = new HashMap<>();
        executorLinux.put("platform", "linux");
        executorLinux.put("name", "sh");
        executorLinux.put(
            "command",
            nodeExecutorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorLinux);
      } else if (nodeExecutorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorLinux = new HashMap<>();
        executorLinux.put("platform", "linux");
        executorLinux.put("name", "sh");
        executorLinux.put(
            "command",
            nodeExecutorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorLinux);
      }
      if (nodeExecutorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorMac = new HashMap<>();
        executorMac.put("platform", "darwin");
        executorMac.put("name", "sh");
        executorMac.put(
            "command",
            nodeExecutorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorMac);
      } else if (nodeExecutorExecutorCommands.containsKey(
          Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorMac = new HashMap<>();
        executorMac.put("platform", "darwin");
        executorMac.put("name", "sh");
        executorMac.put(
            "command",
            nodeExecutorExecutorCommands.get(
                Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorMac);
      }
      Map<String, Object> body = new HashMap<>();
      body.put("name", "caldera-subprocessor-" + nodeExecutor.getName());
      body.put("tactic", "veriguard");
      body.put("technique_id", "veriguard");
      body.put("technique_name", "veriguard");
      body.put("executors", executors);
      String jsonResponse = this.post(this.config.getRestApiV2Url() + ABILITIES_URI, body);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Ability createClearAbility(NodeExecutor nodeExecutor) {
    try {
      List<Map<String, String>> executors = new ArrayList<>();
      Map<String, String> nodeExecutorExecutorClearCommands = nodeExecutor.getExecutorClearCommands();
      if (nodeExecutorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorWindows = new HashMap<>();
        executorWindows.put("platform", "windows");
        executorWindows.put("name", "psh");
        executorWindows.put(
            "command",
            nodeExecutorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorWindows);
      } else if (nodeExecutorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorWindows = new HashMap<>();
        executorWindows.put("platform", "windows");
        executorWindows.put("name", "psh");
        executorWindows.put(
            "command",
            nodeExecutorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorWindows);
      }
      if (nodeExecutorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorLinux = new HashMap<>();
        executorLinux.put("platform", "linux");
        executorLinux.put("name", "sh");
        executorLinux.put(
            "command",
            nodeExecutorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorLinux);
      } else if (nodeExecutorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorLinux = new HashMap<>();
        executorLinux.put("platform", "linux");
        executorLinux.put("name", "sh");
        executorLinux.put(
            "command",
            nodeExecutorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorLinux);
      }
      if (nodeExecutorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64)) {
        Map<String, String> executorMac = new HashMap<>();
        executorMac.put("platform", "darwin");
        executorMac.put("name", "sh");
        executorMac.put(
            "command",
            nodeExecutorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64));
        executors.add(executorMac);
      } else if (nodeExecutorExecutorClearCommands.containsKey(
          Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64)) {
        Map<String, String> executorMac = new HashMap<>();
        executorMac.put("platform", "darwin");
        executorMac.put("name", "sh");
        executorMac.put(
            "command",
            nodeExecutorExecutorClearCommands.get(
                Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64));
        executors.add(executorMac);
      }
      Map<String, Object> body = new HashMap<>();
      body.put("name", "caldera-clear-" + nodeExecutor.getName());
      body.put("tactic", "veriguard");
      body.put("technique_id", "veriguard");
      body.put("technique_name", "veriguard");
      body.put("executors", executors);
      String jsonResponse = this.post(this.config.getRestApiV2Url() + ABILITIES_URI, body);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteAbility(Ability ability) {
    try {
      this.delete(this.config.getRestApiV2Url() + ABILITIES_URI + "/" + ability.getAbility_id());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- EXPLOITS --

  private static final String EXPLOIT_URI = "/exploit";

  public void exploit(
      @NotBlank final String obfuscator,
      @NotBlank final String paw,
      @NotBlank final String abilityId,
      final List<Map<String, String>> additionalFields) {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("obfuscator", obfuscator);
      body.put("paw", paw);
      body.put("ability_id", abilityId);
      body.put("facts", additionalFields);
      String result = this.post(this.config.getPluginAccessApiUrl() + EXPLOIT_URI, body);
      assert result.contains("complete"); // the exploit is well taken into account
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- PRIVATE --

  private String get(@NotBlank final String uri) throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpGet httpGet = new HttpGet(this.config.getRestApiV2Url() + uri);
      // Headers
      httpGet.addHeader(KEY_HEADER, this.config.getApiKey());

      return httpClient.execute(httpGet, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + uri, e);
    }
  }

  private String post(@NotBlank final String url, @NotNull final Map<String, Object> body)
      throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(url);
      // Headers
      httpPost.addHeader(KEY_HEADER, this.config.getApiKey());
      // Body
      StringEntity entity = new StringEntity(this.objectMapper.writeValueAsString(body));
      httpPost.setEntity(entity);

      return httpClient.execute(httpPost, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + url, e);
    }
  }

  private void patch(@NotBlank final String url, @NotNull final Map<String, Object> body)
      throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPatch httpPatch = new HttpPatch(url);
      // Headers
      httpPatch.addHeader(KEY_HEADER, this.config.getApiKey());
      // Body
      StringEntity entity = new StringEntity(this.objectMapper.writeValueAsString(body));
      httpPatch.setEntity(entity);
      httpClient.execute(httpPatch);
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + url, e);
    }
  }

  private void delete(@NotBlank final String url) throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpDelete httpdelete = new HttpDelete(url);
      // Headers
      httpdelete.addHeader(KEY_HEADER, this.config.getApiKey());
      httpClient.execute(httpdelete);
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + url, e);
    }
  }
}
