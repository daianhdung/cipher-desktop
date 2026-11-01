package studio.cipher;

/** Non-Application launcher makes classpath/JAR launching predictable outside an IDE. */
public final class PreviewLauncher {
  public static void main(String[] args) {
    javafx.application.Application.launch(PreviewApplication.class, args);
  }
}
