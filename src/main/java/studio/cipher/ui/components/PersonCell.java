package studio.cipher.ui.components;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.*;
import studio.cipher.model.Models.Person;

public final class PersonCell extends ListCell<Person> {
    @Override protected void updateItem(Person person,boolean empty){
        super.updateItem(person,empty);setText(null);setGraphic(null);if(empty||person==null)return;
        Label name=Ui.label(person.name.get(),"person-name");Label preview=Ui.label(person.preview.get(),"conversation-preview");preview.setMaxWidth(175);
        VBox words=new VBox(5,name,preview);HBox.setHgrow(words,Priority.ALWAYS);words.setMinWidth(0);
        Label unread=Ui.label(person.unread.get()>0?String.valueOf(person.unread.get()):"","unread-count");unread.setVisible(person.unread.get()>0);unread.setManaged(person.unread.get()>0);
        HBox row=Ui.row(11,new Avatar(person,42,true),words,unread);row.getStyleClass().add("person-row");row.setMinWidth(0);row.prefWidthProperty().bind(getListView().widthProperty().subtract(24));row.maxWidthProperty().bind(row.prefWidthProperty());setGraphic(row);setAccessibleText(person.name.get()+", "+(person.online.get()?"Online":"Offline")+", "+person.unread.get()+" unread");
    }
}
