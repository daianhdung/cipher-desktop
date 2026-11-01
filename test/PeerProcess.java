import chat.client.ChatClient;
import chat.common.Packet;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Subprocess harness; stdout is a small line protocol for scripts/process_test.py. */
public class PeerProcess {
    static String encode(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    static String decode(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception {
        try(ChatClient c=new ChatClient(Path.of(args[2]),p->System.out.println(p.type()+"\t"+p.id()+"\t"+encode(p.from())+"\t"+encode(p.to())+"\t"+encode(p.text())+"\t"+encode(p.extra())))) {
            c.connect("127.0.0.1",Integer.parseInt(args[0]),args[1],"");
            Scanner input=new Scanner(System.in,StandardCharsets.UTF_8);
            while(input.hasNextLine()) {
                String[] command=input.nextLine().split("\t",-1);
                if(command[0].equals("EXIT"))break;
                switch(command[0]) {
                    case "TEXT" -> c.text(command[1],decode(command[2]));
                    case "FILE" -> c.file(command[1],Path.of(decode(command[2])),new AtomicBoolean());
                    case "HISTORY" -> c.history(command[1]);
                    default -> throw new IllegalArgumentException(command[0]);
                }
            }
        }
    }
}
