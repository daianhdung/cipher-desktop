package studio.cipher.mock;

import studio.cipher.service.Services;

/** Composition root only. Controllers receive Services and never import this package. */
public final class MockRuntime implements AutoCloseable {
  private final DemoState state;
  private final Services services;

  public MockRuntime() {
    this(true, 1);
  }

  public MockRuntime(boolean automatic, double timeScale) {
    DemoState.fx();
    state = new DemoState(timeScale);
    var auth = new MockAuthenticationService(state);
    var chat = new MockChatService(state, auth);
    services =
        new Services(
            auth,
            chat,
            new MockEncryptionLabService(state),
            new MockFileTransferService(state, auth),
            new MockMonitoringService(state, chat, automatic));
  }

  public Services services() {
    return services;
  }

  public void close() {
    state.close();
  }
}
