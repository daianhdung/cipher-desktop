package studio.cipher.service;

/** Constructor injection boundary. Real adapters can implement these contracts later. */
public record Services(
    AuthenticationService authentication,
    ChatService chat,
    EncryptionLabService lab,
    FileTransferService files,
    MonitoringService monitoring) {}
