package io.veriguard;

import io.veriguard.database.model.Grant;
import io.veriguard.database.model.Group;
import io.veriguard.database.model.User;
import io.veriguard.utils.fixtures.GrantFixture;
import io.veriguard.utils.fixtures.composers.GrantComposer;
import io.veriguard.utils.mockUser.TestUserHolder;
import io.veriguard.utils.mockUser.WithMockUserTestExecutionListener;
import io.veriguard.utilstest.RabbitMQTestListener;
import io.veriguard.utilstest.StartupSnapshotTestListener;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@AutoConfigureMockMvc(print = MockMvcPrint.SYSTEM_ERR)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestExecutionListeners(
    value = {
      StartupSnapshotTestListener.class,
      WithMockUserTestExecutionListener.class,
      RabbitMQTestListener.class
    },
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class IntegrationTest {

  @Autowired GrantComposer grantComposer;
  @Autowired protected TestUserHolder testUserHolder;
  @Autowired protected EntityManager entityManager;

  public void addGrantToCurrentUser(
      Grant.GRANT_RESOURCE_TYPE grantResourceType, Grant.GRANT_TYPE grantType, String resourceId) {
    User user = testUserHolder.get();
    Group group = user.getGroups().getFirst();

    Grant grant = GrantFixture.getGrant(resourceId, grantResourceType, grantType, group);
    grantComposer.forGrant(grant).persist();

    // ensure changes are flushed and a fresh entity is seen
    entityManager.flush();
    entityManager.clear();

    // Refresh SecurityContext to reflect new authority
    testUserHolder.refreshSecurityContext();
  }
}
