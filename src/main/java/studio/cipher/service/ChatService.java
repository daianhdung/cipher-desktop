package studio.cipher.service;

import javafx.beans.property.*;
import javafx.collections.ObservableList;
import studio.cipher.model.Models.*;

public interface ChatService {
  ObservableList<Person> people();

  ObservableList<Message> messages(String peerId);

  ReadOnlyStringProperty typingPeerProperty();

  void markRead(String peerId);

  void activateConversation(String peerId);

  void send(String peerId, String text);

  void retry(Message message);

  void simulateIncoming(String peerId);

  void failNextSend();
}
