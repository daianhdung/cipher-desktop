package studio.cipher.service;

import javafx.beans.property.*;
import javafx.collections.ObservableList;
import studio.cipher.model.Models.*;

public interface MonitoringService {
  Metrics metrics();

  ObservableList<Session> sessions();

  ObservableList<LogEntry> logs();

  ObservableList<TrafficPoint> traffic();

  BooleanProperty playingProperty();

  void simulateTraffic();

  void simulatePresence();

  void simulateTransfer();

  void clearLogs();
}
