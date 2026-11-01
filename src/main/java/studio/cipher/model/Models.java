package studio.cipher.model;

import javafx.beans.property.*;
import javafx.collections.*;
import java.time.LocalDateTime;
import java.util.List;

/** Presentation models contain no sockets, crypto primitives or persistence handles. */
public final class Models {
    private Models() {}
    public static final class Person {
        public final String id, role, color;
        public final StringProperty name=new SimpleStringProperty();
        public final BooleanProperty online=new SimpleBooleanProperty();
        public final StringProperty activity=new SimpleStringProperty();
        public final StringProperty preview=new SimpleStringProperty();
        public final IntegerProperty unread=new SimpleIntegerProperty();
        public Person(String id,String name,String role,String color,boolean online,String activity,String preview,int unread) {
            this.id=id;this.name.set(name);this.role=role;this.color=color;this.online.set(online);this.activity.set(activity);this.preview.set(preview);this.unread.set(unread);
        }
        public String initials(){String[] words=name.get().strip().split("\\s+");return (""+words[0].charAt(0)+(words.length>1?words[words.length-1].charAt(0):"")).toUpperCase();}
    }
    public enum Delivery { SENDING, DELIVERED, READ, FAILED }
    public enum TransferStatus { QUEUED, TRANSFERRING, COMPLETE, CANCELLED, FAILED }
    public static final class Message {
        public final String id, peerId, sender, text;
        public final boolean outgoing;
        public final LocalDateTime time;
        public final ObjectProperty<Delivery> delivery=new SimpleObjectProperty<>();
        public final Transfer attachment;
        public Message(String id,String peerId,String sender,String text,boolean outgoing,LocalDateTime time,Delivery delivery,Transfer attachment){
            this.id=id;this.peerId=peerId;this.sender=sender;this.text=text;this.outgoing=outgoing;this.time=time;this.delivery.set(delivery);this.attachment=attachment;
        }
    }
    public static final class Transfer {
        public final String id,peerId,name,sender,recipient,kind;
        public final long size;
        public final boolean outgoing;
        public final DoubleProperty progress=new SimpleDoubleProperty();
        public final ObjectProperty<TransferStatus> status=new SimpleObjectProperty<>(TransferStatus.QUEUED);
        public Transfer(String id,String peerId,String name,String sender,String recipient,long size,String kind,boolean outgoing){this.id=id;this.peerId=peerId;this.name=name;this.sender=sender;this.recipient=recipient;this.size=size;this.kind=kind;this.outgoing=outgoing;}
    }
    public static final class Session {
        public final String id,address;
        public final Person person;
        public final ObjectProperty<LocalDateTime> connected=new SimpleObjectProperty<>();
        public final LongProperty sent=new SimpleLongProperty(),received=new SimpleLongProperty();
        public Session(String id,String address,Person person,LocalDateTime connected){this.id=id;this.address=address;this.person=person;this.connected.set(connected);}
    }
    public record LogEntry(LocalDateTime time,String level,String category,String message) {}
    public record TrafficPoint(String time,long sent,long received,int messages,int connections) {}
    public record Frequency(String letter,int count) {}
    public record LabResult(String plaintext,String ciphertext,String nonce,String fingerprint,List<Frequency> frequencies,int letters,int bytes) {}
    public static final class Metrics {
        public final IntegerProperty connections=new SimpleIntegerProperty(),messages=new SimpleIntegerProperty(),activeTransfers=new SimpleIntegerProperty(),errors=new SimpleIntegerProperty();
        public final LongProperty sent=new SimpleLongProperty(),received=new SimpleLongProperty();
    }
    public static String bytes(long bytes){if(bytes<1024)return bytes+" B";if(bytes<1024*1024)return String.format("%.1f KB",bytes/1024.0);if(bytes<1024L*1024*1024)return String.format("%.1f MB",bytes/(1024.0*1024));return String.format("%.2f GB",bytes/(1024.0*1024*1024));}
}
