package studio.cipher.ui.components;

import studio.cipher.model.Models.LogEntry;
import studio.cipher.service.MonitoringService;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class LogViewer extends VBox {
    public LogViewer(MonitoringService service){
        super(12);getStyleClass().add("card");
        ComboBox<String> level=new ComboBox<>();level.getItems().addAll("All levels","INFO","WARN","ERROR");level.getSelectionModel().selectFirst();level.setId("log-level");level.setAccessibleText("Filter logs by level");
        TextField search=new TextField();search.setPromptText("Filter events…");search.setId("log-search");search.setPrefWidth(220);
        Button clear=new Button("Clear");clear.getStyleClass().add("link-button");clear.setOnAction(e->service.clearLogs());
        getChildren().add(Ui.row(12,Ui.label("Event log","section-title"),Ui.label("SIMULATED","badge","badge-neutral"),Ui.spacer(),search,level,clear));
        FilteredList<LogEntry> filtered=new FilteredList<>(service.logs());
        Runnable update=()->{String query=search.getText().strip().toLowerCase(Locale.ROOT);filtered.setPredicate(entry->(level.getValue().equals("All levels")||entry.level().equals(level.getValue()))&&(entry.message()+entry.category()).toLowerCase(Locale.ROOT).contains(query));};
        search.textProperty().addListener((o,a,b)->update.run());level.valueProperty().addListener((o,a,b)->update.run());
        ListView<LogEntry> list=new ListView<>(filtered);list.setId("event-log");list.getStyleClass().add("log-list");list.setPrefHeight(176);list.setPlaceholder(Ui.label("No matching events","tiny","muted"));
        list.setCellFactory(v->new ListCell<>(){
            @Override protected void updateItem(LogEntry e,boolean empty){super.updateItem(e,empty);setText(null);setGraphic(null);if(empty||e==null)return;
                Label timestamp=Ui.label(e.time().format(DateTimeFormatter.ofPattern("HH:mm:ss")),"log-time");timestamp.setMinWidth(64);
                Label severity=Ui.label(e.level(),"log-level","log-"+e.level().toLowerCase(Locale.ROOT));severity.setMinWidth(44);
                Label category=Ui.label(e.category(),"log-category");category.setMinWidth(64);
                Label message=Ui.label(e.message(),"log-message");message.setWrapText(true);HBox.setHgrow(message,Priority.ALWAYS);
                setGraphic(Ui.row(12,timestamp,severity,category,message));
            }
        });getChildren().add(list);
        getChildren().add(Ui.label("Local demo events only · bounded to 250 entries · no keys or message contents logged","micro","muted"));
    }
}
