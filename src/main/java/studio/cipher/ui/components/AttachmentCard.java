package studio.cipher.ui.components;

import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import studio.cipher.model.Models.*;
import studio.cipher.service.FileTransferService;

public final class AttachmentCard extends VBox {
  public AttachmentCard(Transfer transfer, FileTransferService service, boolean compact) {
    super(8);
    getStyleClass().add("attachment-card");
    Label fileType = Ui.label(transfer.kind.length() > 4 ? "FILE" : transfer.kind, "file-type");
    Label name = Ui.label(transfer.name, "file-name");
    name.setMaxWidth(compact ? 155 : 250);
    name.setTooltip(new Tooltip(transfer.name));
    Label detail = Ui.label("", "tiny", "muted");
    detail
        .textProperty()
        .bind(
            Bindings.createStringBinding(
                () -> ModelsText(transfer), transfer.status, transfer.progress));
    VBox words = new VBox(4, name, detail);
    HBox.setHgrow(words, Priority.ALWAYS);
    words.setMinWidth(0);
    Button action = Ui.iconButton("dots", "Transfer actions");
    Ui.actionMenu(
        action,
        new String[] {"View demo attachment", "Cancel transfer", "Retry transfer"},
        i -> {
          switch (i) {
            case 0 ->
                Ui.info(
                    this,
                    "Demo attachment",
                    transfer.name
                        + "\n"
                        + studio.cipher.model.Models.bytes(transfer.size)
                        + "\n\nOnly file metadata is modeled. No bytes are transferred, saved, opened or integrity-verified in Demo Mode.");
            case 1 -> service.cancel(transfer);
            case 2 -> service.retry(transfer);
          }
        });
    getChildren().add(Ui.row(10, fileType, words, action));
    ProgressBar progress = new ProgressBar();
    progress.setMaxWidth(Double.MAX_VALUE);
    progress.progressProperty().bind(transfer.progress);
    progress.visibleProperty().bind(transfer.status.isEqualTo(TransferStatus.TRANSFERRING));
    progress.managedProperty().bind(progress.visibleProperty());
    getChildren().add(progress);
  }

  private String ModelsText(Transfer t) {
    return studio.cipher.model.Models.bytes(t.size)
        + " · "
        + switch (t.status.get()) {
          case QUEUED -> "Queued";
          case TRANSFERRING -> Math.round(t.progress.get() * 100) + "% · simulated";
          case COMPLETE -> "Complete · demo";
          case CANCELLED -> "Cancelled";
          case FAILED -> "Failed · demo";
        };
  }
}
