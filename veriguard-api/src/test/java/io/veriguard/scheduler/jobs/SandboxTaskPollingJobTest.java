package io.veriguard.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.rest.security_validation.SandboxTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SandboxTaskPollingJobTest {

  @Mock private SandboxTaskService taskService;

  @Test
  void execute_delegates_to_service_poll_all_active() {
    when(taskService.pollAllActive()).thenReturn(3);
    SandboxTaskPollingJob job = new SandboxTaskPollingJob(taskService);
    job.execute(null);
    verify(taskService, times(1)).pollAllActive();
  }

  @Test
  void execute_swallows_runtime_exception_so_quartz_keeps_scheduling() {
    when(taskService.pollAllActive()).thenThrow(new RuntimeException("DB down"));
    SandboxTaskPollingJob job = new SandboxTaskPollingJob(taskService);
    assertThatCode(() -> job.execute(null)).doesNotThrowAnyException();
    verify(taskService).pollAllActive();
  }
}
