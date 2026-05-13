package io.veriguard.coverage.soc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SocAdapterRouterTest {

  private final SocAdapter stub = new StubSocAdapter(0.5, 0.0);
  private final SocAdapter nxsoc = new NxSocAdapter();

  @Test
  void selects_stub_by_default() {
    SocAdapterRouter router = new SocAdapterRouter(List.of(stub), "stub");
    assertThat(router.select()).isSameAs(stub);
    assertThat(router.preferredAdapter()).isEqualTo("stub");
  }

  @Test
  void switches_to_nxsoc_when_preferred() {
    SocAdapterRouter router = new SocAdapterRouter(List.of(stub, nxsoc), "nxsoc");
    assertThat(router.select()).isSameAs(nxsoc);
  }

  @Test
  void fail_fast_when_preferred_missing() {
    SocAdapterRouter router = new SocAdapterRouter(List.of(stub), "nxsoc");
    assertThatThrownBy(router::select)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nxsoc")
        .hasMessageContaining("not registered");
  }

  @Test
  void list_available_returns_all_names() {
    SocAdapterRouter router = new SocAdapterRouter(List.of(stub, nxsoc), "stub");
    assertThat(router.listAvailable()).containsExactly("stub", "nxsoc");
  }
}
