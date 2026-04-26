package io.veriguard.rest.scenario;

import static io.veriguard.rest.scenario.ScenarioApi.SCENARIO_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.Base;
import io.veriguard.database.model.Domain;
import io.veriguard.database.model.Scenario;
import io.veriguard.database.model.Tag;
import io.veriguard.export.Mixins;
import io.veriguard.utils.ZipUtils;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.*;
import io.veriguard.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.Set;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
public class ScenarioExportTest extends IntegrationTest {
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private EntityManager manager;

  @BeforeEach
  public void before() {
    scenarioComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    tagComposer.reset();
  }

  private String getJsonExportFromZip(byte[] zipBytes, String entryName) throws IOException {
    return ZipUtils.getZipEntry(zipBytes, "%s.json".formatted(entryName), ZipUtils::streamToString);
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("When payloads have tags, scenario export has these tags")
  public void WhenPayloadsHaveTags_ScenarioExportHasTheseTags() throws Exception {
    Set<Domain> domains =
        domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().getSet();

    ObjectMapper objectMapper = mapper.copy();
    Scenario scenario =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultCrisisScenario())
            .withTag(tagComposer.forTag(TagFixture.getTagWithText("scenario tag")))
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withTag(tagComposer.forTag(TagFixture.getTagWithText("inject tag")))
                    .withInjectorContract(
                        injectorContractComposer
                            .forInjectorContract(
                                InjectorContractFixture.createDefaultInjectorContract())
                            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                            .withPayload(
                                payloadComposer
                                    .forPayload(PayloadFixture.createDefaultCommand(domains))
                                    .withTag(
                                        tagComposer.forTag(
                                            TagFixture.getTagWithText("this is a payload tag"))))))
            .persist()
            .get();

    manager.flush();
    manager.clear();

    byte[] response =
        mvc.perform(
                get(SCENARIO_URI + "/" + scenario.getId() + "/export")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String actualJson = getJsonExportFromZip(response, scenario.getName());

    objectMapper.addMixIn(Base.class, Mixins.Base.class);
    objectMapper.addMixIn(Tag.class, Mixins.Tag.class);
    String tagsJson = objectMapper.writeValueAsString(tagComposer.generatedItems.stream().toList());

    assertThatJson(actualJson)
        .when(Option.IGNORING_ARRAY_ORDER)
        .node("scenario_tags")
        .isArray()
        .isEqualTo(tagsJson);
  }
}
