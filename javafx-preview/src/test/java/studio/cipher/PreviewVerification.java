package studio.cipher;

import studio.cipher.mock.MockRuntime;
import studio.cipher.service.Services;
import studio.cipher.model.Models.*;
import studio.cipher.ui.controllers.ShellController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

/** Actual JavaFX toolkit/FXML/CSS and UI interaction checks. No backend tests or services run. */
public final class PreviewVerification {
    static Stage stage;static MockRuntime runtime;static ShellController shell;static Parent root;static Services services;
    static final AtomicReference<Throwable> asynchronousFailure=new AtomicReference<>();
    static <T>T fx(Callable<T> task)throws Exception{FutureTask<T> future=new FutureTask<>(task);Platform.runLater(future);return future.get(12,TimeUnit.SECONDS);}
    static void run(Runnable task)throws Exception{fx(()->{task.run();return null;});}
    static void check(boolean condition,String description){if(!condition)throw new AssertionError(description);System.out.println("PASS "+description);}
    static void waitFor(Callable<Boolean> condition)throws Exception{long end=System.nanoTime()+10_000_000_000L;while(!fx(condition)){if(asynchronousFailure.get()!=null)throw new AssertionError(asynchronousFailure.get());if(System.nanoTime()>end)throw new AssertionError("JavaFX condition timed out");Thread.sleep(15);}}
    static <T extends Node>T node(String id,Class<T> type){Node value=root.lookup("#"+id);if(value==null)throw new AssertionError("Missing #"+id);return type.cast(value);}
    static void button(String id)throws Exception{run(()->node(id,Button.class).fire());}
    static void screenshot(Path file,Node target)throws Exception{
        fx(()->{root.applyCss();root.layout();WritableImage image=target.snapshot(null,null);int width=(int)image.getWidth(),height=(int)image.getHeight();int[] pixels=new int[width*height];image.getPixelReader().getPixels(0,0,width,height,PixelFormat.getIntArgbInstance(),pixels,0,width);BufferedImage png=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,width,height,pixels,0,width);ImageIO.write(png,"png",file.toFile());return null;});
    }
    public static void main(String[]args)throws Exception{
        Path out=Path.of(args.length>0?args[0]:"target/screenshots").toAbsolutePath();Files.createDirectories(out);
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{e.printStackTrace();asynchronousFailure.set(e);});
        CountDownLatch started=new CountDownLatch(1);Platform.startup(()->{Platform.setImplicitExit(false);started.countDown();});started.await(10,TimeUnit.SECONDS);
        try{
            run(()->{try{
                runtime=new MockRuntime(false,.09);services=runtime.services();stage=new Stage();shell=new ShellController(services,stage);
                FXMLLoader loader=new FXMLLoader(PreviewVerification.class.getResource("/studio/cipher/view/shell.fxml"));loader.setControllerFactory(c->shell);root=loader.load();
                Scene scene=new Scene(root,1280,800);scene.getStylesheets().add(PreviewVerification.class.getResource("/studio/cipher/css/theme.css").toExternalForm());stage.setScene(scene);stage.show();shell.start("chat",false);
            }catch(Exception e){throw new RuntimeException(e);}});
            check(Objects.equals(fx(()->services.monitoring().metrics().sent.get()),fx(()->services.monitoring().traffic().stream().mapToLong(TrafficPoint::sent).sum())),"seed traffic reconciles with cumulative demo bytes");
            Thread.sleep(250);screenshot(out.resolve("00-login.png"),root);
            button("login-submit");waitFor(()->services.authentication().currentUserProperty().get()!=null);Thread.sleep(100);
            check(fx(()->!node("send-message",Button.class).isDisabled())==false,"empty composer disables send");
            screenshot(out.resolve("01-chat.png"),root);
            String peer=fx(()->services.chat().people().getFirst().id);
            int before=fx(()->services.chat().messages(peer).size());
            run(()->{
                TextArea text=node("message-composer",TextArea.class);text.setText("Hello from JavaFX — xin chào!");text.positionCaret(text.getLength());
                text.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.ENTER,true,false,false,false));
                check(text.getText().endsWith("\n"),"Shift+Enter inserts a newline");
                text.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.ENTER,false,false,false,false));
            });
            check(fx(()->services.chat().messages(peer).size())==before+1,"outgoing message appears immediately");
            waitFor(()->services.chat().messages(peer).size()>=before+2);
            check(fx(()->services.chat().messages(peer).get(before).delivery.get())==Delivery.READ,"delivery states and simulated incoming reply");
            check(fx(()->services.chat().people().getFirst().unread.get())==0,"active conversation does not accumulate unread replies");
            run(()->node("conversation-search",TextField.class).setText("no-such-person"));check(fx(()->node("conversation-list",ListView.class).getItems().isEmpty()),"conversation search empty state");
            run(()->{node("conversation-search",TextField.class).clear();node("conversation-list",ListView.class).getSelectionModel().select(0);});
            Person offline=fx(()->services.chat().people().stream().filter(p->!p.online.get()).findFirst().orElseThrow());
            run(()->{node("conversation-list",ListView.class).getSelectionModel().select(offline);node("message-composer",TextArea.class).setText("Offline delivery test");});button("send-message");
            waitFor(()->services.chat().messages(offline.id).getLast().delivery.get()==Delivery.FAILED);
            check(fx(()->services.monitoring().metrics().errors.get())==1,"offline delivery failure is visible and counted");
            run(()->{services.monitoring().simulatePresence();services.chat().retry(services.chat().messages(offline.id).getLast());});
            waitFor(()->services.chat().messages(offline.id).stream().anyMatch(m->m.text.equals("Offline delivery test")&&m.delivery.get()==Delivery.READ));
            run(()->node("conversation-list",ListView.class).getSelectionModel().select(0));
            Transfer transfer=fx(()->services.files().sampleAttachment(peer));waitFor(()->transfer.progress.get()>.05);
            run(()->services.files().cancel(transfer));check(fx(()->transfer.status.get())==TransferStatus.CANCELLED,"attachment cancellation state");
            run(()->services.files().retry(transfer));waitFor(()->transfer.status.get()==TransferStatus.COMPLETE);check(fx(()->transfer.progress.get())==1.0,"attachment retry and complete progress");
            screenshot(out.resolve("02-chat-interactions.png"),root);
            button("nav-lab");check(fx(()->shell.currentScreen()).equals("lab"),"Encryption Lab navigation and FXML");
            check(fx(()->node("lab-encrypt",Button.class).isDisabled()),"lab action disabled before key exchange");button("lab-exchange");waitFor(()->services.lab().readyProperty().get());button("lab-encrypt");waitFor(()->services.lab().resultProperty().get()!=null);
            LabResult result=fx(()->services.lab().resultProperty().get());check(result.ciphertext().startsWith("DEMO:")&&result.nonce().startsWith("DEMO-"),"illustrative cryptographic results explicitly labeled");
            check(result.letters()==result.plaintext().codePoints().filter(Character::isLetter).count(),"local Unicode letter frequency totals");button("lab-decrypt");Thread.sleep(3600);
            screenshot(out.resolve("03-encryption-lab.png"),root);screenshot(out.resolve("04-encryption-lab-full.png"),fx(()->node("labRoot",Node.class)));
            button("nav-monitor");check(fx(()->shell.currentScreen()).equals("monitor"),"Monitoring navigation and FXML");
            run(()->services.monitoring().simulateTraffic());
            button("monitor-pause");check(fx(()->services.monitoring().playingProperty().get()),"monitor resume control");button("monitor-pause");check(!fx(()->services.monitoring().playingProperty().get()),"monitor pause control");
            check(fx(()->services.monitoring().metrics().connections.get())==fx(()->(int)services.monitoring().sessions().stream().filter(s->s.person.online.get()).count()),"connection metric matches online session rows");
            check(Objects.equals(fx(()->services.monitoring().metrics().sent.get()),fx(()->services.monitoring().sessions().stream().mapToLong(s->s.sent.get()).sum())),"sent traffic reconciles with session counters");
            check(Objects.equals(fx(()->services.monitoring().metrics().received.get()),fx(()->services.monitoring().sessions().stream().mapToLong(s->s.received.get()).sum())),"received traffic reconciles with session counters");
            check(fx(()->services.monitoring().metrics().activeTransfers.get())==fx(()->(int)services.files().transfers().stream().filter(t->t.status.get()==TransferStatus.TRANSFERRING||t.status.get()==TransferStatus.QUEUED).count()),"active transfer metric reconciles with transfer states");
            run(()->node("chart-mode",ComboBox.class).getSelectionModel().select("Connections"));check(fx(()->node("traffic-chart",javafx.scene.chart.AreaChart.class).getData().size())==1,"interactive chart mode");run(()->node("chart-mode",ComboBox.class).getSelectionModel().select("Traffic (KB)"));
            run(()->node("online-only",CheckBox.class).setSelected(true));check(fx(()->node("session-table",TableView.class).getItems().size())==fx(()->services.monitoring().metrics().connections.get()),"session filtering");
            run(()->node("log-search",TextField.class).setText("no-such-log-entry"));check(fx(()->node("event-log",ListView.class).getItems().isEmpty()),"log search empty state");run(()->node("log-search",TextField.class).clear());
            run(()->node("log-level",ComboBox.class).getSelectionModel().select("WARN"));check(fx(()->node("event-log",ListView.class).getItems().stream().allMatch(e->((LogEntry)e).level().equals("WARN"))),"log severity filtering");run(()->node("log-level",ComboBox.class).getSelectionModel().select("All levels"));
            screenshot(out.resolve("05-server-monitoring.png"),root);screenshot(out.resolve("06-server-monitoring-full.png"),fx(()->node("monitorRoot",Node.class)));
            run(()->{stage.setWidth(1024);stage.setHeight(700);shell.navigate("chat");});Thread.sleep(150);screenshot(out.resolve("07-chat-minimum.png"),root);
            check(fx(()->!node("details",Node.class).isManaged()),"details collapse at minimum window width");
            run(()->shell.navigate("lab"));Thread.sleep(100);screenshot(out.resolve("08-lab-minimum.png"),root);
            run(()->shell.navigate("monitor"));Thread.sleep(100);screenshot(out.resolve("09-monitor-minimum.png"),root);
            run(()->services.monitoring().clearLogs());check(fx(()->services.monitoring().logs().size())==1,"bounded log clear state");
            for(int i=0;i<260;i++)run(()->services.monitoring().simulatePresence());check(fx(()->services.monitoring().logs().size())==250,"log retention bounded to 250 entries");
            run(()->services.authentication().logout());check(fx(()->node("loginOverlay",Node.class).isVisible()),"sign out returns to username login");
            run(()->{shell.close();runtime.close();});int total=fx(()->services.monitoring().metrics().messages.get());Thread.sleep(250);check(fx(()->services.monitoring().metrics().messages.get())==total,"demo jobs stop on application close");
            if(asynchronousFailure.get()!=null)throw new AssertionError(asynchronousFailure.get());
            MockRuntime live=fx(()->new MockRuntime(true,1));
            int samples=fx(()->live.services().monitoring().traffic().size());
            waitFor(()->live.services().monitoring().traffic().size()>samples);
            run(()->live.services().monitoring().playingProperty().set(false));
            int paused=fx(()->live.services().monitoring().traffic().size());Thread.sleep(2200);
            check(fx(()->live.services().monitoring().traffic().size())==paused,"automatic traffic timer pauses without blocking UI");
            run(live::close);
            System.out.println("JAVAFX PREVIEW VERIFICATION OK · screenshots: "+out);
        }finally{run(()->{if(shell!=null)shell.close();if(runtime!=null)runtime.close();if(stage!=null)stage.close();});Platform.exit();}
    }
}
