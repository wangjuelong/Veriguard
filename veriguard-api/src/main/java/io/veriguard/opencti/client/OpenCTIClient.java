package io.veriguard.opencti.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.opencti.client.mutations.Mutation;
import io.veriguard.opencti.client.response.Response;
import io.veriguard.opencti.client.response.fields.Error;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenCTIClient {
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper mapper;

  public Response execute(String url, String authToken, Mutation mutation) throws IOException {
    return execute(url, authToken, mutation.getQueryText(), mutation.getVariables());
  }

  public Response execute(String url, String authToken, String mutationBody, JsonNode variables)
      throws IOException {
    HttpPost req = new HttpPost(url);
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(authToken));
    req.addHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
    req.addHeader(HttpHeaders.ACCEPT, "application/json");
    Map<String, JsonNode> payload = new HashMap<>();
    payload.put("query", mapper.valueToTree(mutationBody));
    if (variables != null) {
      payload.put("variables", variables);
    }
    req.setEntity(new StringEntity(mapper.writeValueAsString(payload)));

    return execute(req);
  }

  public record ExtractedData(int status, String body) {}

  private Response execute(ClassicHttpRequest request) throws IOException {
    try (CloseableHttpClient client = httpClientFactory.httpClientCustom()) {
      ExtractedData ed =
          client.execute(
              request,
              classicResponse ->
                  new ExtractedData(
                      classicResponse.getCode(),
                      EntityUtils.toString(classicResponse.getEntity())));
      try {
        JsonNode node = mapper.readTree(ed.body);
        if (!node.has("errors") && !node.has("data")) {
          throw new JsonMappingException(
              null, "Response body does not conform to a GraphQL response.");
        }
        Response response = mapper.treeToValue(node, Response.class);
        response.setStatus(ed.status);
        return response;
      } catch (JsonProcessingException e) {
        // if the response body cannot be deserialised as GraphQL response
        // then we need to cope a little bit and provide as much context as possible
        Response response = new Response();
        response.setStatus(ed.status);
        Error err = new Error();
        err.setMessage(e.getMessage());
        response.setErrors(List.of(err));
        // set the data field as the full response body as a string
        ObjectNode objNode = mapper.createObjectNode();
        objNode.set("response_body", mapper.convertValue(ed.body, JsonNode.class));
        response.setData(objNode);
        return response;
      }

    } catch (IOException e) {
      throw new ClientProtocolException(
          "Unexpected response for request on: " + request.getRequestUri(), e);
    }
  }
}
