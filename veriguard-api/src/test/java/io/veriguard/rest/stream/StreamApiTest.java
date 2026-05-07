package io.veriguard.rest.stream;

import static io.veriguard.database.audit.ModelBaseListener.DATA_DELETE;
import static io.veriguard.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.config.VeriguardPrincipal;
import io.veriguard.database.audit.BaseEvent;
import io.veriguard.database.model.*;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.PermissionService;
import io.veriguard.service.UserService;
import io.veriguard.utils.fixtures.AttackChainFixture;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.FluxSink;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@MockitoSettings(strictness = Strictness.LENIENT) // class-wide
@ExtendWith(MockitoExtension.class)
public class StreamApiTest {

  private static final String RESOURCE_ID = "id";
  private static final String USER_ID = "userid";
  private static final String SESSION_ID = "sessionid";

  @Mock private User mockUser;

  @Mock private FluxSink<Object> mockSink;

  @Mock private PermissionService permissionService;

  @Mock private UserService userService;

  @Mock private ObjectMapper mapper;

  @InjectMocks private StreamApi streamApi;

  @BeforeEach
  public void setup() throws NoSuchFieldException, IllegalAccessException {
    // mock consumer
    VeriguardPrincipal mockPrincipal = mock(VeriguardPrincipal.class);
    when(mockPrincipal.getId()).thenReturn(USER_ID);
    when(userService.user(USER_ID)).thenReturn(mockUser);

    // mock objectmapper using reflection
    Field mapperField = RestBehavior.class.getDeclaredField("mapper");
    mapperField.setAccessible(true);
    mapperField.set(streamApi, mapper);

    // attackChainNode into consumers using reflection
    Field consumersField = StreamApi.class.getDeclaredField("consumers");
    consumersField.setAccessible(true);
    Map<String, Tuple2<VeriguardPrincipal, FluxSink<Object>>> consumers =
        (Map<String, Tuple2<VeriguardPrincipal, FluxSink<Object>>>) consumersField.get(streamApi);
    consumers.put(SESSION_ID, Tuples.of(mockPrincipal, mockSink));
  }

  @Test
  public void test_listenDatabaseUpdate_WHEN_user_has_permission() {

    // mock PermissionService method
    when(permissionService.hasPermission(
            mockUser, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.READ))
        .thenReturn(true);

    AttackChain attackChain = AttackChainFixture.getAttackChain();
    attackChain.setId(RESOURCE_ID);
    BaseEvent event = new BaseEvent(DATA_UPDATE, attackChain, mock(ObjectMapper.class));

    // call the method
    streamApi.listenDatabaseUpdate(event);

    // capture the event and verify data
    ArgumentCaptor<ServerSentEvent> captor = ArgumentCaptor.forClass(ServerSentEvent.class);
    verify(mockSink).next(captor.capture());

    ServerSentEvent<?> serverSentEvent = captor.getValue();
    BaseEvent baseEventCaptured = (BaseEvent) serverSentEvent.data();
    assertEquals(event.getType(), baseEventCaptured.getType());
    assertTrue(baseEventCaptured.getInstance() instanceof AttackChain);
    assertEquals(attackChain.getId(), ((AttackChain) baseEventCaptured.getInstance()).getId());
  }

  @Test
  public void test_listenDatabaseUpdate_WHEN_user_has_not_permission() {

    when(mapper.createObjectNode()).thenReturn(mock(ObjectNode.class));

    // mock PermissionService method
    when(permissionService.hasPermission(
            mockUser, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.READ))
        .thenReturn(false);

    AttackChain attackChain = AttackChainFixture.getAttackChain();
    attackChain.setId(RESOURCE_ID);
    BaseEvent event = new BaseEvent(DATA_UPDATE, attackChain, mock(ObjectMapper.class));

    // call the method
    streamApi.listenDatabaseUpdate(event);

    // capture the event and verify data
    ArgumentCaptor<ServerSentEvent> captor = ArgumentCaptor.forClass(ServerSentEvent.class);
    verify(mockSink).next(captor.capture());

    ServerSentEvent<?> serverSentEvent = captor.getValue();
    BaseEvent baseEventCaptured = (BaseEvent) serverSentEvent.data();
    assertEquals(DATA_DELETE, baseEventCaptured.getType());
    assertTrue(baseEventCaptured.getInstance() instanceof AttackChain);
    assertEquals(attackChain.getId(), ((AttackChain) baseEventCaptured.getInstance()).getId());
  }

  @Test
  public void test_given_databaseEvent_when_eventIsCVE_then_doNothing() {
    Vulnerability vulnerability = new Vulnerability();
    BaseEvent event = new BaseEvent(DATA_UPDATE, vulnerability, mock(ObjectMapper.class));

    streamApi.listenDatabaseUpdate(event);

    verify(mockSink, never()).next(any());
  }
}
