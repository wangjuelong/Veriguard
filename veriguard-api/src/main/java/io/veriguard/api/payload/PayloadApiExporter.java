package io.veriguard.api.payload;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.Payload;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.repository.PayloadRepository;
import io.veriguard.jsonapi.IncludeOptions;
import io.veriguard.jsonapi.ZipJsonApi;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.payload.PayloadApi;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PayloadApi.PAYLOAD_URI)
@RequiredArgsConstructor
public class PayloadApiExporter extends RestBehavior {

  private final PayloadRepository payloadRepository;
  private final ZipJsonApi<Payload> zipJsonApi;

  @Operation(
      description = "Exports a payload in JSON:API format, optionally including related entities.")
  @GetMapping(value = "/{payloadId}/export", produces = "application/zip")
  @Transactional(readOnly = true)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<byte[]> export(@PathVariable @NotBlank final String payloadId)
      throws IOException {
    Map<String, Boolean> opts = new HashMap<>();
    opts.put("exclude from payload export", false);
    IncludeOptions includeOptions = IncludeOptions.of(opts);

    Payload payload =
        payloadRepository.findById(payloadId).orElseThrow(ElementNotFoundException::new);
    return zipJsonApi.handleExport(payload, null, includeOptions);
  }
}
