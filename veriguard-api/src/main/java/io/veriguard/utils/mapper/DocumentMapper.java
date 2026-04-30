package io.veriguard.utils.mapper;

import static io.veriguard.utils.mapper.ExerciseMapper.toRelatedEntityOutputs;
import static io.veriguard.utils.mapper.ExerciseMapper.toSimulationInjects;
import static io.veriguard.utils.mapper.InjectMapper.toRelatedEntityOutputs;
import static io.veriguard.utils.mapper.PayloadMapper.toRelatedEntityOutputs;
import static io.veriguard.utils.mapper.ScenarioMapper.toScenarioInjects;
import static io.veriguard.utils.mapper.SecurityPlatformMapper.toRelatedEntityOutputs;

import io.veriguard.database.model.*;
import io.veriguard.rest.document.form.DocumentRelationsOutput;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class DocumentMapper {

  public static DocumentRelationsOutput toDocumentRelationsOutput(Document document) {
    Set<Inject> injects =
        document.getInjectDocuments().stream()
            .map(InjectDocument::getInject)
            .collect(Collectors.toSet());

    Set<Inject> atomics =
        injects.stream()
            .filter(inject -> inject.getScenario() == null && inject.getExercise() == null)
            .collect(Collectors.toSet());

    Set<Inject> scenarioInjects =
        injects.stream().filter(inject -> inject.getScenario() != null).collect(Collectors.toSet());

    Set<Inject> simulationInjects =
        injects.stream().filter(inject -> inject.getExercise() != null).collect(Collectors.toSet());

    Set<Exercise> simulations =
        Stream.concat(
                document.getSimulationsByLogoDark().stream(),
                document.getSimulationsByLogoLight().stream())
            .collect(Collectors.toSet());

    Set<SecurityPlatform> securityPlatforms =
        Stream.concat(
                document.getSecurityPlatformsByLogoDark().stream(),
                document.getSecurityPlatformsByLogoLight().stream())
            .collect(Collectors.toSet());

    Set<Payload> payloads =
        Stream.concat(
                document.getPayloadsByFileDrop().stream(),
                document.getPayloadsByExecutableFile().stream())
            .collect(Collectors.toSet());

    return DocumentRelationsOutput.builder()
        .simulations(toRelatedEntityOutputs(simulations))
        .securityPlatforms(toRelatedEntityOutputs(securityPlatforms))
        .payloads(toRelatedEntityOutputs(payloads))
        .atomicTestings(toRelatedEntityOutputs(atomics))
        .scenarioInjects(toScenarioInjects(scenarioInjects))
        .simulationInjects(toSimulationInjects(simulationInjects))
        .build();
  }
}
