package studio.cipher.mock;
import studio.cipher.service.EncryptionLabService;
import studio.cipher.model.Models.*;
import javafx.beans.property.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Intentionally NOT cryptography. Base64 demo encoding is explicitly labeled in the UI. */
final class MockEncryptionLabService implements EncryptionLabService {
    private final DemoState state;
    private final BooleanProperty ready=new SimpleBooleanProperty(),busy=new SimpleBooleanProperty();
    private final IntegerProperty step=new SimpleIntegerProperty(-1);
    private final ObjectProperty<LabResult> result=new SimpleObjectProperty<>();
    private final StringProperty status=new SimpleStringProperty("Start with a simulated key exchange.");
    private int generation;
    MockEncryptionLabService(DemoState state){this.state=state;}
    public ReadOnlyBooleanProperty readyProperty(){return ready;}
    public ReadOnlyBooleanProperty busyProperty(){return busy;}
    public ReadOnlyIntegerProperty stepProperty(){return step;}
    public ReadOnlyObjectProperty<LabResult> resultProperty(){return result;}
    public ReadOnlyStringProperty statusProperty(){return status;}
    public String samplePlaintext(){return DemoFixtures.PLAINTEXT;}
    public String sampleKey(){return DemoFixtures.DEMO_KEY;}
    public String fingerprint(){return DemoFixtures.FINGERPRINT;}
    public String endpointLabel(){return "demo.local:5555 · virtual endpoint";}
    public void exchange(){
        DemoState.fx();if(busy.get())return;int token=++generation;busy.set(true);ready.set(false);step.set(0);status.set("Simulating public-key exchange…");
        state.later(.6,()->{if(token!=generation)return;step.set(1);});
        state.later(1.2,()->{if(token!=generation)return;step.set(2);ready.set(true);busy.set(false);status.set("Demo session ready · no RSA keys were generated.");state.log("INFO","CRYPTO","Illustrative RSA exchange completed; no cryptographic keys were created.");});
    }
    public void encrypt(String plaintext,String demoKey){
        DemoState.fx();if(busy.get())return;if(!ready.get())throw new IllegalStateException("Run the simulated key exchange first.");
        if(plaintext.isBlank()||plaintext.length()>4000)throw new IllegalArgumentException("Enter 1–4,000 characters of plaintext.");
        if(demoKey.isBlank())throw new IllegalArgumentException("Enter a demo key. It will not be used for real encryption.");
        int token=++generation;busy.set(true);step.set(3);status.set("Building an illustrative payload…");
        for(int i=4;i<=7;i++){int n=i;state.later((i-3)*.4,()->{if(token!=generation)return;step.set(n);});}
        state.later(1.8,()->{if(token!=generation)return;
            Map<String,Integer> counts=new TreeMap<>();plaintext.codePoints().filter(Character::isLetter).map(Character::toLowerCase).forEach(cp->counts.merge(new String(Character.toChars(cp)),1,Integer::sum));
            List<Frequency> frequencies=counts.entrySet().stream().map(e->new Frequency(e.getKey(),e.getValue())).sorted(Comparator.comparingInt(Frequency::count).reversed().thenComparing(Frequency::letter)).toList();
            String cipher="DEMO:"+Base64.getEncoder().encodeToString(("ILLUSTRATION ONLY | "+plaintext).getBytes(StandardCharsets.UTF_8));
            result.set(new LabResult(plaintext,cipher,"DEMO-NONCE-000000000001",fingerprint(),frequencies,frequencies.stream().mapToInt(Frequency::count).sum(),plaintext.getBytes(StandardCharsets.UTF_8).length));
            busy.set(false);status.set("Demo complete · local letter counts, illustrative Base64 payload.");state.log("INFO","CRYPTO","Lab walkthrough completed locally; no data was encrypted or transmitted.");
        });
    }
    public void decrypt(){if(result.get()==null)throw new IllegalStateException("Create a demo payload first.");status.set("Original input shown below. This is a demo reveal, not cryptographic decryption.");}
    public void reset(){generation++;ready.set(false);busy.set(false);step.set(-1);result.set(null);status.set("Start with a simulated key exchange.");}
}
