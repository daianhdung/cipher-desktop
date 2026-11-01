package studio.cipher.mock;

import studio.cipher.model.Models.*;
import java.time.LocalDateTime;
import java.util.*;

/** All fictional names, histories, scripted replies and security demo values live here. */
final class DemoFixtures {
    static final String DEFAULT_NAME="Alex Morgan";
    static final String PLAINTEXT="Xin chào Bob! Hẹn gặp bạn ở buổi demo AES chiều nay.";
    static final String DEMO_KEY="network-lab-demo";
    static final String FINGERPRINT="DEMO · 8F:2A:71:C4:9B:06:DE:23";
    static List<Person> people(){return List.of(
        new Person("bob","Bob Tran","Project teammate","blue",true,"Available for a quick review","The diagrams look great. Let's use these!",2),
        new Person("charlie","Charlie Le","Backend developer","purple",true,"Working on the protocol","I’ve shared the latest demo capture.",1),
        new Person("dana","Dana Phan","UI designer","rose",false,"Last seen 24 minutes ago","Love the new direction for the interface.",0),
        new Person("ethan","Ethan Vo","QA & testing","teal",true,"Testing the happy path","All three clients are ready for the demo.",0));}
    static void seed(DemoState s){
        history(s,"bob",true,"Morning Bob! How is the presentation coming along?",52);
        history(s,"bob",false,"Hey! Almost there. I’ve finished the architecture slides.",50);
        history(s,"bob",true,"Perfect. I’ve updated the chat interface and the encryption walkthrough.",47);
        history(s,"bob",false,"Could you send over the network diagram? I’ll add it to the deck.",44);
        Transfer diagram=complete(s,"bob","network-diagram.pdf",1_248_320,"PDF",true);
        s.addMessage("bob","",true,LocalDateTime.now().minusMinutes(41),Delivery.READ,diagram,false);
        history(s,"bob",false,"The diagrams look great. Let’s use these!",38);
        history(s,"bob",true,"Sounds good. We can run through the demo together after lunch.",35);
        Transfer notes=complete(s,"bob","project-notes.md",6_420,"MD",false);
        s.addMessage("bob","A few notes for our walkthrough.",false,LocalDateTime.now().minusMinutes(32),Delivery.READ,notes,false);
        history(s,"charlie",false,"The protocol spec is ready for review. We’ll integrate after the UI is approved.",42);
        history(s,"charlie",true,"Great — I’m focusing on the JavaFX frontend this week.",40);
        Transfer capture=complete(s,"charlie","demo-capture.png",228_400,"PNG",false);
        s.addMessage("charlie","I’ve shared the latest demo capture.",false,LocalDateTime.now().minusMinutes(29),Delivery.READ,capture,false);
        history(s,"dana",true,"The new spacing and colors are in place. Have a look when you’re back!",65);
        history(s,"dana",false,"Love the new direction for the interface.",60);
        history(s,"ethan",false,"All three clients are ready for the demo.",21);
        history(s,"ethan",true,"Thanks! Could you check the attachment and offline states too?",19);
        Transfer background=new Transfer("seed-world","world","test-recording.mp4","Charlie Le","Ethan Vo",8_600_000,"MP4",false);
        s.transfers.add(background);background.status.set(TransferStatus.TRANSFERRING);s.advance(background,.36);
        s.log("INFO","SESSION","Demo workspace initialized with fictional users and message history.");
        s.log("INFO","CRYPTO","Encryption Lab is illustrative only. No encryption or TCP services are running.");
        s.log("WARN","SESSION","Dana Phan is offline in the current scenario.");
        // Historical samples are accounting snapshots, not fabricated live measurements.
        long sent=s.metrics.sent.get(),received=s.metrics.received.get();
        int[] sendWeights={1,1,2,3,2,1,3,1,2,4,3,1},receiveWeights={2,3,1,1,4,2,1,3,2,1,2,2};
        long allocatedSent=0,allocatedReceived=0;
        for(int i=0;i<12;i++){
            long tx=i==11?sent-allocatedSent:sent*sendWeights[i]/24,rx=i==11?received-allocatedReceived:received*receiveWeights[i]/24;
            allocatedSent+=tx;allocatedReceived+=rx;
            int messages=s.metrics.messages.get()/12+(i<s.metrics.messages.get()%12?1:0);
            s.traffic.add(new TrafficPoint(LocalDateTime.now().minusSeconds((12-i)*2L).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),tx,rx,messages,s.metrics.connections.get()));
        }
        s.refresh();
    }
    private static void history(DemoState s,String id,boolean out,String text,int minutes){s.addMessage(id,text,out,LocalDateTime.now().minusMinutes(minutes),Delivery.READ,null,false);}
    private static Transfer complete(DemoState s,String peer,String name,long size,String kind,boolean out){
        Transfer f=new Transfer(UUID.randomUUID().toString(),peer,name,out?DEFAULT_NAME:s.person(peer).name.get(),out?s.person(peer).name.get():DEFAULT_NAME,size,kind,out);
        s.transfers.add(f);s.advance(f,1);f.status.set(TransferStatus.COMPLETE);return f;
    }
    static String reply(String peer,int index){
        List<String> replies=switch(peer){
            case "bob" -> List.of("Looks good to me! I’ll add that to the demo notes.","Thanks! Let’s walk through that together.","Got it — the interface feels much clearer now.");
            case "charlie" -> List.of("Nice! We can plug in the TCP adapter after the UI review.","The service boundaries look good. I’ll keep the protocol compatible.");
            case "ethan" -> List.of("I’ve noted that in the test plan. I’ll check the edge cases too.","Confirmed. The demo scenario is ready to run.");
            default -> List.of("Thanks for the update! I’ll take a look when I’m back.");
        };return replies.get(Math.floorMod(index,replies.size()));
    }
}
