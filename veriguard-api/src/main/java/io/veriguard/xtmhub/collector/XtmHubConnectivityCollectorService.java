package io.veriguard.xtmhub.collector;

import io.veriguard.xtmhub.XtmHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class XtmHubConnectivityCollectorService implements Runnable {
  private final XtmHubService xtmHubService;

  @Override
  public void run() {
    xtmHubService.refreshConnectivity();
  }
}
