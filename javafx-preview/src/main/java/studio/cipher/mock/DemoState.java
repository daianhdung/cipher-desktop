package studio.cipher.mock;

import studio.cipher.model.Models.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.util.Duration;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** Shared event ledger. All observable mutations happen on the JavaFX thread. */
final class DemoState implements AutoCloseable {
    final ObservableList<Person> people=FXCollections.observableArrayList(p->new javafx.beans.Observable[]{p.name,p.online,p.preview,p.unread});
    final ObservableList<Session> sessions=FXCollections.observableArrayList();
    final ObservableList<Transfer> transfers=FXCollections.observableArrayList(t->new javafx.beans.Observable[]{t.progress,t.status});
    final ObservableList<LogEntry> logs=FXCollections.observableArrayList();
    final ObservableList<TrafficPoint> traffic=FXCollections.observableArrayList();
    final Map<String,ObservableList<Message>> conversations=new HashMap<>();
    final Person me=new Person("me",DemoFixtures.DEFAULT_NAME,"Student workspace","indigo",false,"Demo account","",0);
    final Metrics metrics=new Metrics();
    final List<Animation> jobs=new ArrayList<>();
    final double speed;
    boolean closed;
    int sequence,replyIndex;
    DemoState(double speed){
        this.speed=speed;people.addAll(DemoFixtures.people());
        int index=0;for(Person p:people){sessions.add(new Session("DEMO-"+(101+index),"192.0.2."+(21+index)+":5"+(4200+index),p,LocalDateTime.now().minusMinutes(44-index*5)));conversations.put(p.id,FXCollections.observableArrayList());index++;}
        sessions.add(new Session("DEMO-100","192.0.2.20:54100",me,LocalDateTime.now().minusMinutes(6)));
        refresh();DemoFixtures.seed(this);
    }
    static void fx(){if(!Platform.isFxApplicationThread())throw new IllegalStateException("Demo state must be changed on the JavaFX thread");}
    Person person(String id){return people.stream().filter(p->p.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Conversation not found"));}
    void log(String level,String category,String text){fx();logs.add(0,new LogEntry(LocalDateTime.now(),level,category,text));if(logs.size()>250)logs.remove(250,logs.size());}
    void bytes(String from,String to,long count){
        for(Session s:sessions){if(s.person.name.get().equals(from))s.received.set(s.received.get()+count);if(s.person.name.get().equals(to))s.sent.set(s.sent.get()+count);}
        refresh();
    }
    Message addMessage(String peer,String text,boolean out,LocalDateTime time,Delivery delivery,Transfer attachment,boolean notify){
        fx();Person other=person(peer);String sender=out?me.name.get():other.name.get();
        Message message=new Message("msg-"+(++sequence),peer,sender,text,out,time,delivery,attachment);
        conversations.get(peer).add(message);other.preview.set(attachment==null?text:"Attachment · "+attachment.name);
        if(!out&&notify)other.unread.set(other.unread.get()+1);
        metrics.messages.set(metrics.messages.get()+1);
        bytes(sender,out?other.name.get():me.name.get(),Math.max(48,text.getBytes(StandardCharsets.UTF_8).length+48));
        return message;
    }
    void advance(Transfer t,double progress){
        fx();long before=Math.round(t.progress.get()*t.size),after=Math.round(progress*t.size);t.progress.set(progress);bytes(t.sender,t.recipient,Math.max(0,after-before));
    }
    void refresh(){
        metrics.connections.set((int)sessions.stream().filter(s->s.person.online.get()).count());
        metrics.activeTransfers.set((int)transfers.stream().filter(t->t.status.get()==TransferStatus.TRANSFERRING||t.status.get()==TransferStatus.QUEUED).count());
        metrics.sent.set(sessions.stream().mapToLong(s->s.sent.get()).sum());metrics.received.set(sessions.stream().mapToLong(s->s.received.get()).sum());
    }
    void later(double seconds,Runnable action){
        Timeline t=new Timeline(new KeyFrame(Duration.seconds(seconds*speed),e->{if(!closed)action.run();}));
        jobs.add(t);t.setOnFinished(e->jobs.remove(t));t.play();
    }
    @Override public void close(){fx();closed=true;for(Animation a:List.copyOf(jobs))a.stop();jobs.clear();}
}
