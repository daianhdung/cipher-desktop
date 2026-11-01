package studio.cipher.service;

import javafx.beans.property.*;

public interface EncryptionLabService {
  ReadOnlyBooleanProperty readyProperty();

  ReadOnlyBooleanProperty busyProperty();

  ReadOnlyIntegerProperty stepProperty();

  ReadOnlyObjectProperty<studio.cipher.model.Models.LabResult> resultProperty();

  ReadOnlyStringProperty statusProperty();

  String samplePlaintext();

  String sampleKey();

  String fingerprint();

  String endpointLabel();

  void exchange();

  void encrypt(String plaintext, String demoKey);

  void decrypt();

  void reset();
}
