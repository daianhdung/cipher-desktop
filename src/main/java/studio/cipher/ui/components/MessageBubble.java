package studio.cipher.ui.components;

import java.time.format.DateTimeFormatter;
import javafx.beans.binding.Bindings;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import studio.cipher.model.Models.*;
import studio.cipher.service.FileTransferService;

public final class MessageBubble extends HBox {
  public MessageBubble(Message message, Person peer, FileTransferService files, Runnable retry) {
    setSpacing(10);
    getStyleClass().add("message-row");
    setAlignment(message.outgoing ? Pos.TOP_RIGHT : Pos.TOP_LEFT);
    VBox content = new VBox(6);
    content.setMaxWidth(430);
    content.getStyleClass().add("message-content");
    if (!message.text.isBlank()) {
      Label text = Ui.label(message.text, "message-text");
      text.setWrapText(true);
      text.setMaxWidth(410);
      VBox bubble = new VBox(text);
      bubble.getStyleClass().addAll("bubble", message.outgoing ? "bubble-out" : "bubble-in");
      content.getChildren().add(bubble);
    }
    if (message.attachment != null)
      content.getChildren().add(new AttachmentCard(message.attachment, files, false));
    Label delivery = Ui.label("", "message-time");
    javafx.beans.Observable[] dependencies =
        message.attachment == null
            ? new javafx.beans.Observable[] {message.delivery}
            : new javafx.beans.Observable[] {message.delivery, message.attachment.status};
    delivery
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () ->
                    message.time.format(DateTimeFormatter.ofPattern("HH:mm"))
                        + (message.outgoing ? "   " + deliveryText(message) : ""),
                dependencies));
    Button retryButton = new Button("Retry");
    retryButton.getStyleClass().add("link-button");
    retryButton.visibleProperty().bind(message.delivery.isEqualTo(Delivery.FAILED));
    retryButton.managedProperty().bind(retryButton.visibleProperty());
    retryButton.setOnAction(e -> retry.run());
    HBox meta = Ui.row(7, delivery, retryButton);
    meta.setAlignment(message.outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    content.getChildren().add(meta);
    if (message.outgoing) getChildren().add(content);
    else getChildren().addAll(new Avatar(peer, 30, false), content);
  }

  private String deliveryText(Message m) {
    if (m.attachment != null)
      return switch (m.attachment.status.get()) {
        case QUEUED -> "Queued";
        case TRANSFERRING -> "Transferring · demo";
        case COMPLETE -> "✓ Complete · demo";
        case CANCELLED -> "Cancelled · demo";
        case FAILED -> "Failed · demo";
      };
    return switch (m.delivery.get()) {
      case SENDING -> "Sending…";
      case DELIVERED -> "✓ Delivered";
      case READ -> "✓✓ Read";
      case FAILED -> "Failed · demo";
    };
  }
}
