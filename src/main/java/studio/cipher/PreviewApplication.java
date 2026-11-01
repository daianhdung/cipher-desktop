package studio.cipher;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import studio.cipher.mock.MockRuntime;
import studio.cipher.ui.controllers.ShellController;

/** Separate, mock-only entry point. The original Swing/TCP entry points are untouched. */
public final class PreviewApplication extends Application {
  private MockRuntime runtime;
  private ShellController shell;

  @Override
  public void start(Stage stage) throws Exception {
    runtime = new MockRuntime();
    shell = new ShellController(runtime.services(), stage);
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/studio/cipher/view/shell.fxml"));
    loader.setControllerFactory(
        type -> {
          if (type == ShellController.class) return shell;
          throw new IllegalArgumentException("Unknown root controller");
        });
    var root = loader.<javafx.scene.Parent>load();
    Scene scene = new Scene(root, 1280, 800);
    scene
        .getStylesheets()
        .add(getClass().getResource("/studio/cipher/css/theme.css").toExternalForm());
    stage.setTitle("Cipher Desktop — Demo Mode");
    stage.setMinWidth(1024);
    stage.setMinHeight(700);
    stage.setScene(scene);
    boolean monitor = getParameters().getRaw().contains("--monitor");
    shell.start(monitor ? "monitor" : "chat", monitor);
    stage.show();
    stage.setOnCloseRequest(
        e -> {
          shell.close();
          runtime.close();
        });
    String name = getParameters().getNamed().get("username");
    if (name != null) runtime.services().authentication().login(name);
  }

  @Override
  public void stop() {
    if (shell != null) shell.close();
    if (runtime != null) runtime.close();
  }
}
