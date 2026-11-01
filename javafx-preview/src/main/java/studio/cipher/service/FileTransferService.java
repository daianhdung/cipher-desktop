package studio.cipher.service;
import javafx.collections.ObservableList;
import studio.cipher.model.Models.Transfer;
public interface FileTransferService {
    ObservableList<Transfer> transfers();
    Transfer attach(String peerId,String name,long size);
    Transfer sampleAttachment(String peerId);
    void cancel(Transfer transfer);
    void retry(Transfer transfer);
}
