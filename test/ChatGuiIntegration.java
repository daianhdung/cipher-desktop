import chat.client.ChatFrame;
import chat.server.ChatServer;
import javax.swing.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import javax.imageio.ImageIO;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.atomic.*;

/** Real Swing controls, real TCP; captures the rendered window without screen-recording access. */
public class ChatGuiIntegration {
    static Object field(ChatFrame f,String name)throws Exception{Field v=ChatFrame.class.getDeclaredField(name);v.setAccessible(true);return v.get(f);}
    static void edt(Runnable task)throws Exception{SwingUtilities.invokeAndWait(task);}
    static void text(ChatFrame f,String name,String value)throws Exception{((JTextField)field(f,name)).setText(value);}
    static void click(ChatFrame f,String name)throws Exception{((JButton)field(f,name)).doClick();}
    static void waitFor(java.util.function.BooleanSupplier predicate)throws Exception{long end=System.nanoTime()+15_000_000_000L;while(!predicate.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("GUI timeout");Thread.sleep(20);}}
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("chat-gui-");AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->failure.set(error));
        ChatServer server=new ChatServer("127.0.0.1",0,dir.resolve("server"));
        Thread.ofVirtual().start(()->{try{server.run();}catch(Exception e){failure.set(e);}});
        System.setProperty("chat.downloads",dir.resolve("inbox").toString());
        ChatFrame[] frames=new ChatFrame[2];AtomicInteger ticks=new AtomicInteger();Timer timer=new Timer(10,e->ticks.incrementAndGet());
        try {
            edt(()->{try{
                for(int i=0;i<2;i++){frames[i]=new ChatFrame();frames[i].setVisible(true);text(frames[i],"username",i==0?"GuiAlice":"GuiBob");text(frames[i],"port",String.valueOf(server.port()));click(frames[i],"connect");}
                timer.start();
            }catch(Exception e){throw new RuntimeException(e);}});
            waitFor(()->server.onlineCount()==2);
            // Wait until online list callbacks have reached Swing's event queue.
            waitFor(()->{AtomicBoolean ready=new AtomicBoolean();try{edt(()->{try{ready.set(((JList<?>)field(frames[0],"users")).getModel().getSize()>0);}catch(Exception e){throw new RuntimeException(e);}});}catch(Exception e){throw new RuntimeException(e);}return ready.get();});
            edt(()->{try{((JList<?>)field(frames[0],"users")).setSelectedIndex(0);((JList<?>)field(frames[1],"users")).setSelectedIndex(0);((JTextArea)field(frames[0],"input")).setText("Tin nhắn từ giao diện Swing");click(frames[0],"send");}catch(Exception e){throw new RuntimeException(e);}});
            waitFor(()->{AtomicBoolean shown=new AtomicBoolean();try{edt(()->{try{shown.set(((JTextArea)field(frames[1],"messages")).getText().contains("Tin nhắn từ giao diện Swing"));}catch(Exception e){throw new RuntimeException(e);}});}catch(Exception e){throw new RuntimeException(e);}return shown.get();});
            Path file=dir.resolve("gui-large.bin");try(var out=Files.newOutputStream(file)){byte[] block=new byte[65536];for(int i=0;i<4096;i++)out.write(block);}
            int before=ticks.get();
            edt(()->{try{Method m=ChatFrame.class.getDeclaredMethod("startUpload",String.class,Path.class);m.setAccessible(true);m.invoke(frames[0],"GuiBob",file);text(frames[0],"labKey","GUI rotation during upload");click(frames[0],"rotate");}catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(300);
            AtomicLong duration=new AtomicLong();
            edt(()->{try{long t=System.nanoTime();click(frames[0],"disconnect");duration.set(System.nanoTime()-t);}catch(Exception e){throw new RuntimeException(e);}});
            if(duration.get()>500_000_000L || ticks.get()-before<5)throw new AssertionError("EDT blocked: "+duration+" ticks="+(ticks.get()-before));
            waitFor(()->server.onlineCount()==1);
            Thread.sleep(200);
            Path screenshot=Path.of(args.length>0?args[0]:"build/chat-gui.png").toAbsolutePath();Files.createDirectories(screenshot.getParent());
            edt(()->{try{ChatFrame f=frames[1];BufferedImage image=new BufferedImage(f.getWidth(),f.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();f.printAll(g);g.dispose();ImageIO.write(image,"png",screenshot.toFile());}catch(Exception e){throw new RuntimeException(e);}});
            if(failure.get()!=null)throw new AssertionError(failure.get());
            System.out.println("PASS real Swing connect/send, timer ticks during 256 MiB upload + rotate, immediate disconnect; screenshot="+screenshot);
        } finally {
            edt(()->{timer.stop();for(ChatFrame f:frames)if(f!=null)f.dispatchEvent(new WindowEvent(f,WindowEvent.WINDOW_CLOSING));});server.close();
            try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
