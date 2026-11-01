package studio.cipher.ui.controllers;

import studio.cipher.service.*;
import studio.cipher.model.Models.*;
import studio.cipher.model.Models;
import studio.cipher.ui.*;
import studio.cipher.ui.components.*;
import javafx.beans.binding.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MonitorController {
    private final Services services;private final ScreenHost host;
    @FXML private VBox monitorRoot,transferList,logsContainer;
    @FXML private HBox metrics;
    @FXML private Button pause,scenario;
    @FXML private ComboBox<String> chartMode;
    @FXML private CheckBox sentVisible,receivedVisible,onlineOnly;
    @FXML private AreaChart<Number,Number> trafficChart;
    @FXML private Label activeTransferCount,sessionCount;
    @FXML private TableView<Session> sessionTable;
    public MonitorController(Services services,ScreenHost host){this.services=services;this.host=host;}
    @FXML private void initialize(){
        var monitor=services.monitoring();var m=monitor.metrics();
        metrics.getChildren().addAll(
            new MetricCard("users","Connections",m.connections.asString(),"Active demo sessions"),
            new MetricCard("chat","Messages",m.messages.asString(),"Simulated chat events"),
            new MetricCard("up","Sent",Bindings.createStringBinding(()->Models.bytes(m.sent.get()),m.sent),"Server → clients · demo"),
            new MetricCard("down","Received",Bindings.createStringBinding(()->Models.bytes(m.received.get()),m.received),"Clients → Server · demo"),
            new MetricCard("file","Transfers",m.activeTransfers.asString(),"Currently in progress"),
            new MetricCard("alert","Failures",m.errors.asString(),"Simulated failures"));
        activeTransferCount.textProperty().bind(m.activeTransfers.asString().concat(" active"));sessionCount.textProperty().bind(m.connections.asString().concat(" online"));
        monitor.playingProperty().addListener((o,a,b)->updatePlayback());updatePlayback();
        Ui.actionMenu(scenario,new String[]{"Generate incoming message","Simulate file transfer","Toggle a user’s presence"},i->{switch(i){case 0->monitor.simulateTraffic();case 1->monitor.simulateTransfer();case 2->monitor.simulatePresence();}host.toast("Demo scenario started. Metrics update from the same in-memory events.");});
        chartMode.getItems().addAll("Traffic (KB)","Messages","Connections");chartMode.getSelectionModel().selectFirst();
        chartMode.valueProperty().addListener((o,a,b)->renderChart());sentVisible.selectedProperty().addListener((o,a,b)->renderChart());receivedVisible.selectedProperty().addListener((o,a,b)->renderChart());
        monitor.traffic().addListener((ListChangeListener<TrafficPoint>)c->renderChart());renderChart();
        configureTable();renderTransfers();services.files().transfers().addListener((ListChangeListener<Transfer>)c->renderTransfers());logsContainer.getChildren().add(new LogViewer(monitor));
    }
    private void updatePlayback(){boolean playing=services.monitoring().playingProperty().get();pause.setText(playing?"Pause traffic":"Resume traffic");pause.setGraphic(new Icon(playing?"pause":"play"));}
    private void configureTable(){
        FilteredList<Session> filtered=new FilteredList<>(services.monitoring().sessions());sessionTable.setItems(filtered);
        Runnable filter=()->filtered.setPredicate(s->!onlineOnly.isSelected()||s.person.online.get());onlineOnly.selectedProperty().addListener((o,a,b)->filter.run());services.monitoring().metrics().connections.addListener((o,a,b)->filter.run());
        TableColumn<Session,String> user=new TableColumn<>("CLIENT");user.setCellValueFactory(c->c.getValue().person.name);user.setPrefWidth(160);
        TableColumn<Session,String> session=new TableColumn<>("SESSION ID");session.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().id));session.setPrefWidth(118);
        TableColumn<Session,String> address=new TableColumn<>("REMOTE ADDRESS");address.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().address));address.setPrefWidth(158);
        TableColumn<Session,String> time=new TableColumn<>("CONNECTED");time.setCellValueFactory(c->Bindings.createStringBinding(()->c.getValue().connected.get().format(DateTimeFormatter.ofPattern("HH:mm:ss")),c.getValue().connected));time.setPrefWidth(100);
        TableColumn<Session,String> state=new TableColumn<>("STATUS");state.setCellValueFactory(c->Bindings.when(c.getValue().person.online).then("Online").otherwise("Offline"));state.setPrefWidth(90);
        state.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String v,boolean empty){super.updateItem(v,empty);setText(null);setGraphic(empty||v==null?null:Ui.badge("●  "+v,v.equals("Online")?"badge-green":"badge-neutral"));}});
        TableColumn<Session,String> sent=new TableColumn<>("SENT");sent.setCellValueFactory(c->Bindings.createStringBinding(()->Models.bytes(c.getValue().sent.get()),c.getValue().sent));sent.setPrefWidth(90);
        TableColumn<Session,String> received=new TableColumn<>("RECEIVED");received.setCellValueFactory(c->Bindings.createStringBinding(()->Models.bytes(c.getValue().received.get()),c.getValue().received));received.setPrefWidth(90);
        sessionTable.getColumns().setAll(user,session,address,time,state,sent,received);sessionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);sessionTable.setPlaceholder(Ui.label("No matching demo sessions","muted"));
    }
    private void renderChart(){
        trafficChart.getData().clear();String mode=chartMode.getValue();boolean traffic="Traffic (KB)".equals(mode);sentVisible.setVisible(traffic);sentVisible.setManaged(traffic);receivedVisible.setVisible(traffic);receivedVisible.setManaged(traffic);
        NumberAxis x=(NumberAxis)trafficChart.getXAxis();double oldest=services.monitoring().traffic().stream().mapToDouble(this::relativeSeconds).min().orElse(-24);x.setLowerBound(Math.min(-4,oldest));x.setUpperBound(0);x.setTickUnit(Math.max(4,Math.ceil(Math.abs(oldest)/20)*4));
        x.setTickLabelFormatter(new javafx.util.StringConverter<Number>(){public String toString(Number value){return value.intValue()==0?"latest":Math.abs(value.intValue())+"s";}public Number fromString(String value){return 0;}});
        ((NumberAxis)trafficChart.getYAxis()).setLabel(traffic?"KB / interval":mode);
        if(traffic){if(sentVisible.isSelected())trafficChart.getData().add(series("Sent",p->p.sent()/1024.0));if(receivedVisible.isSelected())trafficChart.getData().add(series("Received",p->p.received()/1024.0));}
        else trafficChart.getData().add(series(mode,p->"Messages".equals(mode)?p.messages():p.connections()));
    }
    private XYChart.Series<Number,Number> series(String name,java.util.function.ToDoubleFunction<TrafficPoint> value){
        var series=new XYChart.Series<Number,Number>();series.setName(name);
        for(TrafficPoint point:services.monitoring().traffic()){
            var data=new XYChart.Data<Number,Number>(relativeSeconds(point),value.applyAsDouble(point));
            data.nodeProperty().addListener((o,a,b)->{if(b!=null)Tooltip.install(b,new Tooltip(name+" · "+point.time()+"\n"+String.format("%.1f",value.applyAsDouble(point))));});
            series.getData().add(data);
        }return series;
    }
    private double relativeSeconds(TrafficPoint point){
        var samples=services.monitoring().traffic();if(samples.isEmpty())return 0;
        long seconds=java.time.Duration.between(java.time.LocalTime.parse(samples.getLast().time()),java.time.LocalTime.parse(point.time())).getSeconds();
        return seconds>0?seconds-86400:seconds;
    }
    private void renderTransfers(){
        transferList.getChildren().clear();List<Transfer> all=new ArrayList<>(services.files().transfers());all.sort(Comparator.comparingInt(t->t.status.get()==TransferStatus.TRANSFERRING?0:1));
        for(Transfer t:all){VBox card=new VBox(6);card.getStyleClass().add("monitor-transfer");Label name=Ui.label(t.name,"file-name");name.setMaxWidth(210);Label status=Ui.label("","tiny","muted");status.textProperty().bind(Bindings.createStringBinding(()->Models.bytes(t.size)+" · "+(t.status.get()==TransferStatus.TRANSFERRING?Math.round(t.progress.get()*100)+"%":t.status.get().toString().toLowerCase(Locale.ROOT)),t.status,t.progress));
            card.getChildren().addAll(Ui.row(8,new Icon("file"),name,Ui.spacer()),Ui.label(t.sender+" → "+t.recipient,"micro","muted"),status);ProgressBar p=new ProgressBar();p.progressProperty().bind(t.progress);p.setMaxWidth(Double.MAX_VALUE);card.getChildren().add(p);transferList.getChildren().add(card);}
    }
    @FXML private void toggleTraffic(){var p=services.monitoring().playingProperty();p.set(!p.get());host.toast(p.get()?"Automatic demo traffic resumed.":"Automatic traffic paused. Manual demo actions still work.");}
    @FXML private void presence(){services.monitoring().simulatePresence();}
}
