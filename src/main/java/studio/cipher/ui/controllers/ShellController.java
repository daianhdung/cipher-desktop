package studio.cipher.ui.controllers;

import java.util.*;
import javafx.animation.PauseTransition;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import studio.cipher.service.*;
import studio.cipher.ui.*;
import studio.cipher.ui.components.*;

public final class ShellController implements ScreenHost {
  private final Services services;
  private final Stage stage;
  private final Map<String, Parent> screens = new HashMap<>();
  private final PauseTransition toastTimer = new PauseTransition(Duration.seconds(3.5));
  private String screen = "chat";
  @FXML private StackPane root, screenContainer, loginOverlay, profileAvatar;
  @FXML private VBox toastBox;
  @FXML private Label pageTitle, toastText, loginError;
  @FXML private Button chatNav, labNav, monitorNav, settings, signOut, help, loginButton;
  @FXML private TextField username;

  public ShellController(Services services, Stage stage) {
    this.services = services;
    this.stage = stage;
  }

  @FXML
  private void initialize() {
    chatNav.setGraphic(new Icon("chat"));
    labNav.setGraphic(new Icon("lab"));
    monitorNav.setGraphic(new Icon("monitor"));
    settings.setGraphic(new Icon("settings"));
    signOut.setGraphic(new Icon("logout"));
    help.setGraphic(new Icon("info"));
    username.setText(services.authentication().suggestedUsername());
    loginButton.disableProperty().bind(services.authentication().connectingProperty());
    services
        .authentication()
        .connectingProperty()
        .addListener(
            (o, a, b) ->
                loginButton.setText(b ? "Opening demo workspace…" : "Enter demo workspace    →"));
    services
        .authentication()
        .currentUserProperty()
        .addListener(
            (o, a, b) -> {
              if (b != null) {
                profileAvatar.getChildren().setAll(new Avatar(b, 36, false));
                loginOverlay.setVisible(false);
                loginOverlay.setManaged(false);
              } else showLogin();
            });
    toastBox.setMaxHeight(Region.USE_PREF_SIZE);
    toastTimer.setOnFinished(
        e -> {
          toastBox.setVisible(false);
          toastBox.setManaged(false);
        });
    navigate("chat");
  }

  public void start(String route, boolean skipLogin) {
    navigate(route);
    if (skipLogin) {
      loginOverlay.setVisible(false);
      loginOverlay.setManaged(false);
    } else showLogin();
  }

  public void showLogin() {
    loginOverlay.setVisible(true);
    loginOverlay.setManaged(true);
    username.requestFocus();
  }

  @FXML
  private void login() {
    try {
      services.authentication().login(username.getText());
      loginError.setText("Opening your in-memory workspace…");
      loginError.getStyleClass().remove("error-text");
    } catch (RuntimeException e) {
      loginError.setText(e.getMessage());
      if (!loginError.getStyleClass().contains("error-text"))
        loginError.getStyleClass().add("error-text");
    }
  }

  @FXML
  private void logout() {
    services.authentication().logout();
    showLogin();
  }

  @FXML
  private void showChat() {
    navigate("chat");
    if (services.authentication().currentUserProperty().get() == null) showLogin();
  }

  @FXML
  private void showLab() {
    navigate("lab");
  }

  @FXML
  private void showMonitor() {
    navigate("monitor");
  }

  @FXML
  private void showSettings() {
    Ui.info(
        root,
        "About this preview",
        "Cipher Desktop · Java 21 + JavaFX 21\n\nA frontend-only prototype with replaceable service interfaces.\n\nChat, file progress, cryptographic workflows and monitoring are simulated in memory. Nothing connects to the existing TCP Server, reads file contents, encrypts data, or uses a database.\n\nThe existing Swing application remains separate.");
  }

  @FXML
  private void showHelp() {
    showSettings();
  }

  public void navigate(String route) {
    toastTimer.stop();
    toastBox.setVisible(false);
    toastBox.setManaged(false);
    if (!Set.of("chat", "lab", "monitor").contains(route))
      throw new IllegalArgumentException("Unknown screen: " + route);
    try {
      Parent view = screens.get(route);
      if (view == null) {
        FXMLLoader loader =
            new FXMLLoader(getClass().getResource("/studio/cipher/view/" + route + ".fxml"));
        loader.setControllerFactory(
            type -> {
              if (type == ChatController.class) return new ChatController(services, this);
              if (type == LabController.class) return new LabController(services, this);
              if (type == MonitorController.class) return new MonitorController(services, this);
              throw new IllegalArgumentException("Unknown controller: " + type);
            });
        view = loader.load();
        screens.put(route, view);
      }
      screenContainer.getChildren().setAll(view);
      screen = route;
      pageTitle.setText(
          switch (route) {
            case "lab" -> "Encryption Lab";
            case "monitor" -> "Server Monitoring";
            default -> "Messages";
          });
      for (Button b : List.of(chatNav, labNav, monitorNav))
        b.getStyleClass().remove("nav-selected");
      (route.equals("chat") ? chatNav : route.equals("lab") ? labNav : monitorNav)
          .getStyleClass()
          .add("nav-selected");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot load " + route + " screen", e);
    }
  }

  public void toast(String text) {
    toastText.setText(text);
    toastBox.setVisible(true);
    toastBox.setManaged(true);
    toastTimer.playFromStart();
  }

  public String currentScreen() {
    return screen;
  }

  public Parent view() {
    return root;
  }

  public void close() {
    toastTimer.stop();
  }
}
