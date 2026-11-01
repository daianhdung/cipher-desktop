package studio.cipher.mock;
import studio.cipher.service.*;
import studio.cipher.model.Models.*;
import javafx.beans.property.*;
import javafx.collections.*;
import java.time.LocalDateTime;

final class MockChatService implements ChatService {
    private final DemoState state;
    private final AuthenticationService auth;
    private final StringProperty typing=new SimpleStringProperty("");
    private boolean failNext;
    private String activePeer="";
    private int generation;
    MockChatService(DemoState state,AuthenticationService auth){this.state=state;this.auth=auth;auth.currentUserProperty().addListener((o,a,b)->{generation++;typing.set("");});}
    public ObservableList<Person> people(){return FXCollections.unmodifiableObservableList(state.people);}
    public ObservableList<Message> messages(String peerId){state.person(peerId);return FXCollections.unmodifiableObservableList(state.conversations.get(peerId));}
    public ReadOnlyStringProperty typingPeerProperty(){return typing;}
    public void markRead(String id){state.person(id).unread.set(0);}
    public void activateConversation(String id){activePeer=id==null?"":id;if(!activePeer.isEmpty())markRead(activePeer);}
    public void send(String peer,String text){
        DemoState.fx();if(auth.currentUserProperty().get()==null)throw new IllegalStateException("Sign in to the demo workspace first.");
        String value=text.strip();if(value.isEmpty())return;if(value.length()>4000)throw new IllegalArgumentException("Keep demo messages under 4,000 characters.");
        Message message=state.addMessage(peer,value,true,LocalDateTime.now(),Delivery.SENDING,null,false);deliver(message);
    }
    private void deliver(Message m){
        boolean fail=failNext||!state.person(m.peerId).online.get();failNext=false;int token=generation;
        state.later(.65,()->{
            if(fail||auth.currentUserProperty().get()==null||token!=generation){m.delivery.set(Delivery.FAILED);state.metrics.errors.set(state.metrics.errors.get()+1);state.log("WARN","CHAT","Simulated delivery failed. Retry when the recipient is online.");return;}
            m.delivery.set(Delivery.DELIVERED);state.log("INFO","CHAT","Demo message delivered to "+state.person(m.peerId).name.get()+".");
            state.later(.75,()->{if(auth.currentUserProperty().get()!=null&&token==generation)m.delivery.set(Delivery.READ);});
            typing.set(m.peerId);
            state.later(1.8,()->{typing.set("");if(auth.currentUserProperty().get()!=null&&token==generation)simulateIncoming(m.peerId);});
        });
    }
    public void retry(Message message){if(message.delivery.get()!=Delivery.FAILED)return;message.delivery.set(Delivery.SENDING);deliver(message);}
    public void simulateIncoming(String peer){
        DemoState.fx();if(!state.person(peer).online.get())return;
        state.addMessage(peer,DemoFixtures.reply(peer,state.replyIndex++),false,LocalDateTime.now(),Delivery.DELIVERED,null,!peer.equals(activePeer));
        state.log("INFO","CHAT","Simulated incoming message from "+state.person(peer).name.get()+".");
    }
    public void failNextSend(){failNext=true;}
}
