package studio.cipher.ui.components;

import javafx.geometry.*;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import studio.cipher.model.Models.Person;

public final class Avatar extends StackPane {
  public Avatar(Person person, double size, boolean showStatus) {
    getStyleClass().addAll("avatar", "avatar-" + person.color);
    setMinSize(size, size);
    setMaxSize(size, size);
    setPrefSize(size, size);
    Label initials = new Label(person.initials());
    initials.getStyleClass().add(size >= 60 ? "avatar-initials-large" : "avatar-initials");
    getChildren().add(initials);
    initials
        .textProperty()
        .bind(javafx.beans.binding.Bindings.createStringBinding(person::initials, person.name));
    if (showStatus) {
      Circle online = new Circle(size >= 60 ? 5 : 4), offline = new Circle(size >= 60 ? 5 : 4);
      online.getStyleClass().addAll("presence-dot", "presence-online");
      offline.getStyleClass().addAll("presence-dot", "presence-offline");
      online.visibleProperty().bind(person.online);
      offline.visibleProperty().bind(person.online.not());
      for (Circle dot : new Circle[] {online, offline}) {
        getChildren().add(dot);
        setAlignment(dot, Pos.BOTTOM_RIGHT);
        setMargin(dot, new Insets(0, 1, 1, 0));
      }
    }
  }
}
