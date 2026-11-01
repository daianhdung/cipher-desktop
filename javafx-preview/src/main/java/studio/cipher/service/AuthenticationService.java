package studio.cipher.service;
import javafx.beans.property.*;
import studio.cipher.model.Models.Person;
public interface AuthenticationService {
    ReadOnlyObjectProperty<Person> currentUserProperty();
    ReadOnlyBooleanProperty connectingProperty();
    String suggestedUsername();
    void login(String username);
    void logout();
}
