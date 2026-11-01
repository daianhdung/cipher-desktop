package chat.server;

import java.nio.file.Path;

public final class ServerMain {
    public static void main(String[] args) throws Exception {
        String host="0.0.0.0"; int port=5555; Path data=Path.of("server-data");
        for(int i=0;i<args.length;i+=2) {
            if(i+1>=args.length) throw new IllegalArgumentException("Dùng --host IP --port PORT --data DIR");
            switch(args[i]) {
                case "--host" -> host=args[i+1];
                case "--port" -> port=Integer.parseInt(args[i+1]);
                case "--data" -> data=Path.of(args[i+1]);
                default -> throw new IllegalArgumentException("Tham số lạ: "+args[i]);
            }
        }
        var server=new ChatServer(host,port,data);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("Console: status / sessions / quit");
        Thread.ofVirtual().name("server-console").start(()-> {
            var input=new java.util.Scanner(System.in);
            while(input.hasNextLine()) {
                switch(input.nextLine().trim()) {
                    case "status" -> System.out.println("RUNNING | connected="+server.onlineCount());
                    case "sessions" -> server.currentSessions().forEach(System.out::println);
                    case "quit" -> { server.close(); return; }
                    default -> System.out.println("Console: status / sessions / quit");
                }
            }
        });
        server.run();
    }
}
