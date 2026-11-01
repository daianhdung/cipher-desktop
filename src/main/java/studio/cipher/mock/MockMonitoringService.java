package studio.cipher.mock;
import studio.cipher.service.*;
import studio.cipher.model.Models.*;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.util.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class MockMonitoringService implements MonitoringService {
    private final DemoState state;private final ChatService chat;
    private final BooleanProperty playing=new SimpleBooleanProperty(true);
    private long lastSent,lastReceived;private int lastMessages,ticks,presence;
    MockMonitoringService(DemoState state,ChatService chat,boolean automatic){
        this.state=state;this.chat=chat;lastSent=state.metrics.sent.get();lastReceived=state.metrics.received.get();lastMessages=state.metrics.messages.get();
        Timeline clock=new Timeline(new KeyFrame(Duration.seconds(2),e->tick()));clock.setCycleCount(Animation.INDEFINITE);state.jobs.add(clock);
        if(automatic)clock.play();else playing.set(false);
    }
    private void tick(){
        if(!playing.get())return;ticks++;
        if(ticks%4==0)simulateTraffic();if(ticks%15==0)simulatePresence();
        for(Transfer t:state.transfers)if(t.peerId.equals("world")&&t.status.get()==TransferStatus.TRANSFERRING){state.advance(t,Math.min(1,t.progress.get()+.035));if(t.progress.get()>=1){t.status.set(TransferStatus.COMPLETE);state.refresh();state.log("INFO","FILE","Background demo recording finished.");}}
        long sent=state.metrics.sent.get(),received=state.metrics.received.get();int messages=state.metrics.messages.get();
        state.traffic.add(new TrafficPoint(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),sent-lastSent,received-lastReceived,messages-lastMessages,state.metrics.connections.get()));
        if(state.traffic.size()>24)state.traffic.remove(0);lastSent=sent;lastReceived=received;lastMessages=messages;
    }
    public Metrics metrics(){return state.metrics;}
    public ObservableList<Session> sessions(){return FXCollections.unmodifiableObservableList(state.sessions);}
    public ObservableList<LogEntry> logs(){return FXCollections.unmodifiableObservableList(state.logs);}
    public ObservableList<TrafficPoint> traffic(){return FXCollections.unmodifiableObservableList(state.traffic);}
    public BooleanProperty playingProperty(){return playing;}
    public void simulateTraffic(){DemoState.fx();chat.simulateIncoming(state.people.get(ticks%state.people.size()).online.get()?state.people.get(ticks%state.people.size()).id:"bob");}
    public void simulatePresence(){DemoState.fx();Person p=state.person(presence++%2==0?"dana":"ethan");p.online.set(!p.online.get());if(p.online.get())state.sessions.stream().filter(s->s.person==p).forEach(s->s.connected.set(LocalDateTime.now()));p.activity.set(p.online.get()?"Available in demo workspace":"Last seen just now");state.refresh();state.log("INFO","SESSION",p.name.get()+(p.online.get()?" came online":" went offline")+" (simulated).");}
    public void simulateTransfer(){
        DemoState.fx();var t=new Transfer(java.util.UUID.randomUUID().toString(),"world","demo-session.zip","Bob Tran","Charlie Le",4_800_000,"ZIP",false);
        state.transfers.add(t);t.status.set(TransferStatus.TRANSFERRING);state.refresh();state.log("INFO","FILE","Simulated background transfer started: "+t.name);
        progress(t);
    }
    private void progress(Transfer t){state.later(.3,()->{if(t.status.get()!=TransferStatus.TRANSFERRING)return;state.advance(t,Math.min(1,t.progress.get()+.06));if(t.progress.get()>=1){t.status.set(TransferStatus.COMPLETE);state.refresh();state.log("INFO","FILE","Simulated background transfer complete.");}else progress(t);});}
    public void clearLogs(){state.logs.clear();state.log("INFO","SYSTEM","Demo log cleared. Retention is limited to 250 entries.");}
}
