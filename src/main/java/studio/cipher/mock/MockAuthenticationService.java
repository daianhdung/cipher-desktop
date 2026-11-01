package studio.cipher.mock;

import javafx.beans.property.*;
import studio.cipher.model.Models.Person;
import studio.cipher.service.AuthenticationService;

final class MockAuthenticationService implements AuthenticationService {
  private final DemoState state;
  private final ObjectProperty<Person> current = new SimpleObjectProperty<>();
  private final BooleanProperty connecting = new SimpleBooleanProperty();
  private int generation;

  MockAuthenticationService(DemoState state) {
    this.state = state;
  }

  public ReadOnlyObjectProperty<Person> currentUserProperty() {
    return current;
  }

  public ReadOnlyBooleanProperty connectingProperty() {
    return connecting;
  }

  public String suggestedUsername() {
    return DemoFixtures.DEFAULT_NAME;
  }

  public void login(String username) {
    DemoState.fx();
    String value = username.strip();
    if (!value.matches("[\\p{L}\\p{N} _-]{2,24}"))
      throw new IllegalArgumentException("Use 2–24 letters, numbers, spaces, _ or -.");
    if (state.people.stream().anyMatch(p -> p.name.get().equalsIgnoreCase(value)))
      throw new IllegalArgumentException("That demo username is already in use.");
    if (connecting.get()) return;
    int token = ++generation;
    connecting.set(true);
    state.later(
        .65,
        () -> {
          if (token != generation) return;
          state.me.name.set(value);
          state.me.online.set(true);
          state.sessions.stream()
              .filter(s -> s.person == state.me)
              .forEach(s -> s.connected.set(java.time.LocalDateTime.now()));
          current.set(state.me);
          connecting.set(false);
          state.refresh();
          state.log(
              "INFO",
              "SESSION",
              value + " joined the demo workspace. No network connection was opened.");
        });
  }

  public void logout() {
    DemoState.fx();
    generation++;
    connecting.set(false);
    state.me.online.set(false);
    current.set(null);
    state.refresh();
    state.log("INFO", "SESSION", "Demo user signed out.");
  }
}
