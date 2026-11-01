package studio.cipher.ui.controllers;

import java.util.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import studio.cipher.model.Models.*;
import studio.cipher.service.*;
import studio.cipher.ui.*;
import studio.cipher.ui.components.*;

public final class ChatController {
  private final Services services;
  private final ScreenHost host;
  private Person selected;
  private ObservableList<Message> shown;
  private final ListChangeListener<Message> messageListener = change -> renderMessages();
  private boolean detailsRequested = true;
  @FXML private BorderPane chatRoot;
  @FXML private VBox details, messageList, sharedFiles;
  @FXML private StackPane myAvatar, peerAvatar, detailAvatar, searchIcon;
  @FXML private HBox typingRow;
  @FXML
  private Label onlineCount,
      myName,
      peerName,
      peerStatus,
      detailName,
      detailRole,
      detailOnline,
      typingLabel,
      sharedCount;
  @FXML private ListView<Person> conversationList;
  @FXML private TextField search;
  @FXML private TextArea composer;
  @FXML private Button newChat, profileInfo, simulateReply, detailsToggle, more, attach, send;
  @FXML private ScrollPane messageScroll;

  public ChatController(Services services, ScreenHost host) {
    this.services = services;
    this.host = host;
  }

  @FXML
  private void initialize() {
    searchIcon.getChildren().add(new Icon("search"));
    newChat.setGraphic(new Icon("plus"));
    profileInfo.setGraphic(new Icon("dots"));
    simulateReply.setGraphic(new Icon("rotate"));
    simulateReply.setTooltip(new Tooltip("Simulate an incoming reply"));
    detailsToggle.setGraphic(new Icon("info"));
    more.setGraphic(new Icon("dots"));
    attach.setGraphic(new Icon("clip"));
    send.setGraphic(new Icon("send"));
    Ui.actionMenu(
        more,
        new String[] {"Add sample attachment", "Simulate a failed send", "About this conversation"},
        i -> {
          if (i == 0 && selected != null) services.files().sampleAttachment(selected.id);
          else if (i == 1) {
            services.chat().failNextSend();
            host.toast("The next outgoing message will demonstrate a delivery failure.");
          } else if (i == 2) aboutProfile();
        });
    FilteredList<Person> filtered = new FilteredList<>(services.chat().people());
    conversationList.setItems(filtered);
    conversationList.setCellFactory(l -> new PersonCell());
    conversationList.setPlaceholder(
        Ui.empty("No conversations", "Try a different name or message."));
    search
        .textProperty()
        .addListener(
            (o, a, b) -> {
              String q = b.strip().toLowerCase(Locale.ROOT);
              filtered.setPredicate(
                  p ->
                      p.name.get().toLowerCase(Locale.ROOT).contains(q)
                          || p.preview.get().toLowerCase(Locale.ROOT).contains(q));
            });
    conversationList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> select(b));
    services
        .chat()
        .people()
        .addListener(
            (ListChangeListener<Person>)
                c -> {
                  updateCount();
                  if (selected != null) updateHeader();
                });
    updateCount();
    services
        .authentication()
        .currentUserProperty()
        .addListener(
            (o, a, b) -> {
              updateMyProfile();
              if (b == null) composer.clear();
            });
    updateMyProfile();
    send.disableProperty()
        .bind(
            Bindings.createBooleanBinding(
                () ->
                    composer.getText().isBlank()
                        || selected == null
                        || services.authentication().currentUserProperty().get() == null,
                composer.textProperty(),
                services.authentication().currentUserProperty(),
                conversationList.getSelectionModel().selectedItemProperty()));
    attach
        .disableProperty()
        .bind(
            services
                .authentication()
                .currentUserProperty()
                .isNull()
                .or(conversationList.getSelectionModel().selectedItemProperty().isNull()));
    composer.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ENTER) {
            e.consume();
            if (e.isShiftDown()) composer.replaceSelection("\n");
            else sendMessage();
          }
        });
    services.chat().typingPeerProperty().addListener((o, a, b) -> updateTyping());
    services.files().transfers().addListener((ListChangeListener<Transfer>) c -> renderFiles());
    chatRoot.widthProperty().addListener((o, a, b) -> updateDetails());
    chatRoot
        .sceneProperty()
        .addListener(
            (o, a, b) ->
                services
                    .chat()
                    .activateConversation(b != null && selected != null ? selected.id : null));
    if (!filtered.isEmpty()) conversationList.getSelectionModel().select(0);
  }

  private void select(Person person) {
    if (shown != null) shown.removeListener(messageListener);
    selected = person;
    if (person == null) {
      services.chat().activateConversation(null);
      messageList
          .getChildren()
          .setAll(Ui.empty("Choose a conversation", "Your demo messages will appear here."));
      peerName.setText("No conversation selected");
      peerStatus.setText("Search for a teammate to begin");
      peerAvatar.getChildren().clear();
      sharedFiles.getChildren().clear();
      return;
    }
    shown = services.chat().messages(person.id);
    shown.addListener(messageListener);
    services.chat().markRead(person.id);
    if (chatRoot.getScene() != null) services.chat().activateConversation(person.id);
    peerAvatar.getChildren().setAll(new Avatar(person, 42, true));
    detailAvatar.getChildren().setAll(new Avatar(person, 74, true));
    updateHeader();
    renderMessages();
    renderFiles();
    updateTyping();
  }

  private void updateHeader() {
    if (selected == null) return;
    peerName.setText(selected.name.get());
    peerStatus.setText(
        selected.online.get()
            ? "Online · " + selected.activity.get()
            : "Offline · " + selected.activity.get());
    detailName.setText(selected.name.get());
    detailRole.setText(selected.role);
    detailOnline.setText(selected.online.get() ? "●  Available" : "●  Offline");
    detailOnline.getStyleClass().removeAll("badge-green", "badge-neutral");
    detailOnline.getStyleClass().add(selected.online.get() ? "badge-green" : "badge-neutral");
  }

  private void updateMyProfile() {
    Person p = services.authentication().currentUserProperty().get();
    myName.setText(p == null ? "Guest preview" : p.name.get());
    myAvatar.getChildren().clear();
    if (p != null) myAvatar.getChildren().add(new Avatar(p, 36, false));
  }

  private void updateCount() {
    onlineCount.setText(
        services.chat().people().stream().filter(p -> p.online.get()).count() + " online");
  }

  private void updateTyping() {
    boolean visible =
        selected != null && services.chat().typingPeerProperty().get().equals(selected.id);
    typingRow.setVisible(visible);
    typingRow.setManaged(visible);
    if (visible) typingLabel.setText(selected.name.get() + " is typing…");
  }

  private void renderMessages() {
    if (selected == null) return;
    messageList.getChildren().clear();
    HBox divider = Ui.row(10, separator(), Ui.label("TODAY", "date-label"), separator());
    divider.setAlignment(Pos.CENTER);
    messageList.getChildren().add(divider);
    for (Message m : shown)
      messageList
          .getChildren()
          .add(new MessageBubble(m, selected, services.files(), () -> services.chat().retry(m)));
    Platform.runLater(() -> messageScroll.setVvalue(1));
    // A visible conversation is read locally; no read receipt is sent anywhere.
    if (selected.unread.get() != 0) services.chat().markRead(selected.id);
  }

  private Region separator() {
    Region r = new Region();
    r.getStyleClass().add("date-line");
    HBox.setHgrow(r, Priority.ALWAYS);
    return r;
  }

  private void renderFiles() {
    if (selected == null) return;
    sharedFiles.getChildren().clear();
    List<Transfer> shared =
        services.files().transfers().stream().filter(t -> t.peerId.equals(selected.id)).toList();
    sharedCount.setText(String.valueOf(shared.size()));
    for (Transfer t : shared)
      sharedFiles.getChildren().add(new AttachmentCard(t, services.files(), true));
    if (shared.isEmpty())
      sharedFiles.getChildren().add(Ui.label("No shared files yet", "tiny", "muted"));
  }

  private void updateDetails() {
    boolean visible = detailsRequested && chatRoot.getWidth() >= 1110;
    details.setVisible(visible);
    details.setManaged(visible);
  }

  @FXML
  private void toggleDetails() {
    if (chatRoot.getWidth() < 1110) {
      if (selected != null)
        Ui.info(
            chatRoot,
            "Conversation details",
            selected.name.get()
                + "\n"
                + selected.role
                + "\n\n"
                + selected.activity.get()
                + "\n\nLocal simulation. No TCP connection or real security state.");
    } else {
      detailsRequested = !detailsRequested;
      updateDetails();
    }
  }

  @FXML
  private void sendMessage() {
    if (selected == null || composer.getText().isBlank()) return;
    try {
      services.chat().send(selected.id, composer.getText());
      composer.clear();
      composer.requestFocus();
    } catch (RuntimeException e) {
      host.toast(e.getMessage());
    }
  }

  @FXML
  private void chooseFile() {
    if (selected == null) return;
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose attachment · metadata-only demo");
    java.io.File f = chooser.showOpenDialog(chatRoot.getScene().getWindow());
    if (f != null) {
      try {
        services.files().attach(selected.id, f.getName(), f.length());
        host.toast(
            "Simulating progress for " + f.getName() + ". File contents are not read or sent.");
      } catch (RuntimeException e) {
        host.toast(e.getMessage());
      }
    }
  }

  @FXML
  private void incoming() {
    if (selected == null) return;
    if (!selected.online.get()) {
      host.toast("This teammate is offline in the demo. Try an online conversation.");
      return;
    }
    services.chat().simulateIncoming(selected.id);
  }

  @FXML
  private void focusSearch() {
    search.requestFocus();
  }

  @FXML
  private void openLab() {
    host.navigate("lab");
  }

  @FXML
  private void aboutProfile() {
    Ui.info(
        chatRoot,
        "Demo workspace",
        "Username-only simulated sign-in. All contacts, conversations, delivery indicators and session information are in-memory examples. No backend authentication is performed.");
  }
}
