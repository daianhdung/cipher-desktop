package studio.cipher.ui.components;
import javafx.beans.binding.StringBinding;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public final class MetricCard extends VBox {
    public MetricCard(String icon,String title,StringBinding value,String detail){
        super(9);getStyleClass().add("metric-card");HBox top=Ui.row(8,Ui.label(title,"metric-label"),Ui.spacer(),new Icon(icon));
        Label number=Ui.label("","metric-value");number.textProperty().bind(value);getChildren().addAll(top,number,Ui.label(detail,"tiny","muted"));setMinWidth(120);setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(this,Priority.ALWAYS);
    }
}
