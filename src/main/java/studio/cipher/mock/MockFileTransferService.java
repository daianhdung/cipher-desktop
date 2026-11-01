package studio.cipher.mock;

import java.time.LocalDateTime;
import java.util.*;
import javafx.collections.*;
import studio.cipher.model.Models.*;
import studio.cipher.service.*;

final class MockFileTransferService implements FileTransferService {
  private final DemoState state;
  private final AuthenticationService auth;
  private final Map<String, Integer> runs = new HashMap<>();

  MockFileTransferService(DemoState state, AuthenticationService auth) {
    this.state = state;
    this.auth = auth;
  }

  public ObservableList<Transfer> transfers() {
    return FXCollections.unmodifiableObservableList(state.transfers);
  }

  public Transfer attach(String peer, String name, long size) {
    DemoState.fx();
    if (auth.currentUserProperty().get() == null) throw new IllegalStateException("Sign in first.");
    if (size < 0 || size > 1024L * 1024 * 1024)
      throw new IllegalArgumentException("Choose a file up to 1 GB for this demo.");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Choose a file name.");
    Person other = state.person(peer);
    String kind =
        name.contains(".")
            ? name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT)
            : "FILE";
    Transfer t =
        new Transfer(
            UUID.randomUUID().toString(),
            peer,
            name,
            state.me.name.get(),
            other.name.get(),
            size,
            kind,
            true);
    state.transfers.add(t);
    state.addMessage(peer, "", true, LocalDateTime.now(), Delivery.DELIVERED, t, false);
    start(t);
    return t;
  }

  public Transfer sampleAttachment(String peer) {
    return attach(peer, "interface-preview.pdf", 2_400_000);
  }

  private void start(Transfer t) {
    int run = runs.merge(t.id, 1, Integer::sum);
    t.status.set(TransferStatus.TRANSFERRING);
    state.refresh();
    state.log(
        "INFO", "FILE", "Demo transfer started: " + t.name + ". File content is not read or sent.");
    tick(t, run);
  }

  private void tick(Transfer t, int run) {
    state.later(
        .22,
        () -> {
          if (!Objects.equals(runs.get(t.id), run) || t.status.get() != TransferStatus.TRANSFERRING)
            return;
          if (auth.currentUserProperty().get() == null || !state.person(t.peerId).online.get()) {
            t.status.set(TransferStatus.FAILED);
            state.metrics.errors.set(state.metrics.errors.get() + 1);
            state.refresh();
            state.log("WARN", "FILE", "Demo transfer failed: recipient offline.");
            return;
          }
          state.advance(t, Math.min(1, t.progress.get() + .055));
          if (t.progress.get() >= 1) {
            t.status.set(TransferStatus.COMPLETE);
            state.refresh();
            state.log(
                "INFO",
                "FILE",
                "Demo transfer complete: "
                    + t.name
                    + ". Integrity was not cryptographically verified.");
          } else tick(t, run);
        });
  }

  public void cancel(Transfer t) {
    DemoState.fx();
    if (t.status.get() == TransferStatus.TRANSFERRING || t.status.get() == TransferStatus.QUEUED) {
      runs.merge(t.id, 1, Integer::sum);
      t.status.set(TransferStatus.CANCELLED);
      state.refresh();
      state.log("WARN", "FILE", "Demo transfer cancelled: " + t.name);
    }
  }

  public void retry(Transfer t) {
    DemoState.fx();
    if (t.status.get() == TransferStatus.CANCELLED || t.status.get() == TransferStatus.FAILED)
      start(t);
  }
}
