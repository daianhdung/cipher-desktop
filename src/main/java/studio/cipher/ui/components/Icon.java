package studio.cipher.ui.components;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.*;

/** Small code-native line icons; no font, image download or icon framework required. */
public final class Icon extends StackPane {
  public Icon(String name) {
    SVGPath path = new SVGPath();
    path.setContent(
        switch (name) {
          case "chat" -> "M4 4 H20 V16 H9 L4 21 Z M8 8 H16 M8 12 H14";
          case "lab" -> "M9 3 H15 M10 3 V10 L4 20 Q3 22 6 22 H18 Q21 22 20 20 L14 10 V3 M8 15 H16";
          case "monitor" -> "M3 4 H21 V17 H3 Z M8 21 H16 M12 17 V21 M6 12 L10 8 L14 13 L18 7";
          case "shield" -> "M12 2 L21 6 V12 Q20 19 12 23 Q4 19 3 12 V6 Z M8 12 L11 15 L17 9";
          case "search" -> "M16 16 L22 22 M18 10 A8 8 0 1 1 2 10 A8 8 0 1 1 18 10";
          case "send" -> "M2 3 L23 12 L2 21 L6 12 Z M6 12 H23";
          case "clip" ->
              "M8 12 L15 5 Q20 0 23 5 Q25 8 20 13 L10 23 Q5 27 1 22 Q-2 19 3 14 L14 3 M5 16 L15 6";
          case "file" -> "M5 2 H14 L20 8 V23 H5 Z M14 2 V8 H20 M9 13 H16 M9 17 H16";
          case "check" -> "M4 12 L9 17 L21 5";
          case "doublecheck" -> "M1 12 L6 17 L17 6 M10 14 L13 17 L24 6";
          case "users" ->
              "M9 11 A4 4 0 1 1 9 3 A4 4 0 1 1 9 11 M2 22 V19 Q2 14 9 14 Q16 14 16 19 V22 M17 3 Q23 3 23 7 Q23 11 18 11 M19 15 Q24 15 24 20";
          case "plus" -> "M12 4 V20 M4 12 H20";
          case "x" -> "M5 5 L19 19 M19 5 L5 19";
          case "play" -> "M7 3 L22 12 L7 21 Z";
          case "pause" -> "M7 4 V20 M17 4 V20";
          case "rotate" -> "M20 7 V2 M20 7 H15 M20 7 A9 9 0 1 0 21 16";
          case "copy" -> "M8 8 H22 V22 H8 Z M3 16 V3 H16";
          case "logout" -> "M10 3 H3 V21 H10 M9 12 H23 M18 7 L23 12 L18 17";
          case "settings" ->
              "M9 3 H15 L16 6 L20 7 L22 12 L20 17 L16 18 L15 21 H9 L8 18 L4 17 L2 12 L4 7 L8 6 Z M16 12 A4 4 0 1 1 8 12 A4 4 0 1 1 16 12";
          case "info" -> "M22 12 A10 10 0 1 1 2 12 A10 10 0 1 1 22 12 M12 10 V17 M12 6 V7";
          case "up" -> "M12 21 V3 M5 10 L12 3 L19 10";
          case "down" -> "M12 3 V21 M5 14 L12 21 L19 14";
          case "chevron" -> "M9 5 L16 12 L9 19";
          case "folder" -> "M2 5 H10 L13 8 H23 V21 H2 Z";
          case "alert" -> "M12 2 L24 22 H0 Z M12 8 V14 M12 17 V18";
          case "dots" -> "M4 11 V13 M12 11 V13 M20 11 V13";
          case "wifi" -> "M2 8 Q12 -1 22 8 M6 12 Q12 6 18 12 M10 16 Q12 14 14 16 M12 20 V21";
          default -> "M3 3 H9 V9 H3 Z M15 3 H21 V9 H15 Z M3 15 H9 V21 H3 Z M15 15 H21 V21 H15 Z";
        });
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    path.getStyleClass().add("icon-path");
    path.setScaleX(.77);
    path.setScaleY(.77);
    getChildren().add(path);
    setMinSize(20, 20);
    setPrefSize(20, 20);
    setMaxSize(20, 20);
    setMouseTransparent(true);
    getStyleClass().add("icon");
  }
}
