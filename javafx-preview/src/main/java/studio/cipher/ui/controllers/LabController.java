package studio.cipher.ui.controllers;

import studio.cipher.service.*;
import studio.cipher.model.Models.*;
import studio.cipher.ui.*;
import studio.cipher.ui.components.*;
import javafx.beans.binding.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import java.util.*;

public final class LabController {
    private final Services services;private final ScreenHost host;
    private final List<HBox> steps=new ArrayList<>();
    @FXML private VBox labRoot;
    @FXML private StackPane noticeIcon,connectionIcon,keyIcon,sessionIcon;
    @FXML private Label demoHost,rsaState,fingerprint,keyState,inputCount,nonce,analysisBadge,letterSummary,revealed,labStatus;
    @FXML private TextArea plaintext,ciphertext;
    @FXML private PasswordField demoKey;
    @FXML private Button exchange,rotate,encrypt,copy,decrypt,reset;
    @FXML private ProgressIndicator busy;
    @FXML private FlowPane flow;
    @FXML private BarChart<String,Number> frequencyChart;
    @FXML private TableView<Frequency> frequencyTable;
    public LabController(Services services,ScreenHost host){this.services=services;this.host=host;}
    @FXML private void initialize(){
        var lab=services.lab();noticeIcon.getChildren().add(new Icon("info"));connectionIcon.getChildren().add(new Icon("wifi"));keyIcon.getChildren().add(new Icon("shield"));sessionIcon.getChildren().add(new Icon("lab"));
        demoHost.setText(lab.endpointLabel());fingerprint.setText(lab.fingerprint());example();
        inputCount.textProperty().bind(plaintext.textProperty().length().asString().concat(" characters"));
        labStatus.textProperty().bind(lab.statusProperty());busy.visibleProperty().bind(lab.busyProperty());busy.managedProperty().bind(busy.visibleProperty());
        exchange.disableProperty().bind(lab.busyProperty());rotate.disableProperty().bind(lab.busyProperty().or(lab.readyProperty().not()));
        encrypt.disableProperty().bind(lab.busyProperty().or(lab.readyProperty().not()).or(plaintext.textProperty().isEmpty()).or(demoKey.textProperty().isEmpty()));
        copy.disableProperty().bind(lab.resultProperty().isNull());decrypt.disableProperty().bind(lab.resultProperty().isNull().or(lab.busyProperty()));
        lab.readyProperty().addListener((o,a,b)->updateSession());updateSession();
        String[] labels={"TCP connection","RSA exchange","Session key","AES encryption","Transmission","Decryption","Letter count","Response"};
        for(int i=0;i<labels.length;i++){HBox step=Ui.row(7,Ui.label(String.format("%02d",i+1),"flow-index"),Ui.label(labels[i],"flow-label"));step.getStyleClass().add("flow-step");steps.add(step);flow.getChildren().add(step);}
        lab.stepProperty().addListener((o,a,b)->updateSteps(b.intValue()));updateSteps(lab.stepProperty().get());
        TableColumn<Frequency,String> letter=new TableColumn<>("LETTER");letter.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().letter()));
        TableColumn<Frequency,String> count=new TableColumn<>("OCCURRENCES");count.setCellValueFactory(c->new ReadOnlyStringWrapper(String.valueOf(c.getValue().count())));
        frequencyTable.getColumns().setAll(letter,count);frequencyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);frequencyTable.setPlaceholder(Ui.label("Run the walkthrough to see local counts","tiny","muted"));
        frequencyChart.setAnimated(false);lab.resultProperty().addListener((o,a,b)->showResult(b));
    }
    private void updateSession(){boolean ready=services.lab().readyProperty().get();rsaState.setText(ready?"Public-key exchange simulated":"Not exchanged");keyState.setText(ready?"Demo session ready":"Waiting for demo exchange");exchange.setText(ready?"Repeat key exchange   ↻":"Simulate key exchange   →");}
    private void updateSteps(int current){for(int i=0;i<steps.size();i++){steps.get(i).getStyleClass().removeAll("flow-active","flow-done");if(i<current)steps.get(i).getStyleClass().add("flow-done");if(i==current)steps.get(i).getStyleClass().add("flow-active");}}
    private void showResult(LabResult result){
        frequencyChart.getData().clear();frequencyTable.getItems().clear();
        if(result==null){ciphertext.setText("Run the walkthrough to generate an illustrative payload.");nonce.setText("—");revealed.setText("The original message will be shown here after the demo reveal.");letterSummary.setText("No results yet");analysisBadge.setText("AWAITING INPUT");return;}
        ciphertext.setText(result.ciphertext());nonce.setText(result.nonce());frequencyTable.getItems().setAll(result.frequencies());
        var series=new XYChart.Series<String,Number>();result.frequencies().stream().limit(10).forEach(f->series.getData().add(new XYChart.Data<>(f.letter(),f.count())));frequencyChart.getData().add(series);
        letterSummary.setText(result.letters()+" letters · "+result.frequencies().size()+" unique");analysisBadge.setText("LOCAL DEMO");revealed.setText("Demo payload ready. Select ‘Reveal original’ to display the input.");
    }
    @FXML private void example(){plaintext.setText(services.lab().samplePlaintext());demoKey.setText(services.lab().sampleKey());}
    @FXML private void exchange(){services.lab().exchange();}
    @FXML private void encrypt(){try{services.lab().encrypt(plaintext.getText(),demoKey.getText());}catch(RuntimeException e){host.toast(e.getMessage());}}
    @FXML private void decrypt(){try{services.lab().decrypt();revealed.setText(services.lab().resultProperty().get().plaintext());host.toast("Original demo input revealed. No decryption operation was performed.");}catch(RuntimeException e){host.toast(e.getMessage());}}
    @FXML private void copy(){LabResult r=services.lab().resultProperty().get();if(r!=null){ClipboardContent content=new ClipboardContent();content.putString(r.ciphertext());Clipboard.getSystemClipboard().setContent(content);host.toast("Illustrative demo payload copied.");}}
    @FXML private void reset(){services.lab().reset();example();}
}
