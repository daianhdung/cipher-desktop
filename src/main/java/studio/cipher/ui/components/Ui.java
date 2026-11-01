package studio.cipher.ui.components;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.*;
import java.util.function.Consumer;

public final class Ui {
    private Ui(){}
    public static Label label(String text,String...styles){Label l=new Label(text);l.getStyleClass().addAll(styles);return l;}
    public static Label badge(String text,String style){return label(text,"badge",style);}
    public static Button iconButton(String icon,String tooltip){Button b=new Button();b.setGraphic(new Icon(icon));b.getStyleClass().add("icon-button");b.setTooltip(new Tooltip(tooltip));b.setAccessibleText(tooltip);return b;}
    public static Region spacer(){Region r=new Region();HBox.setHgrow(r,Priority.ALWAYS);return r;}
    public static HBox row(double spacing,Node...nodes){HBox box=new HBox(spacing,nodes);box.setAlignment(Pos.CENTER_LEFT);return box;}
    public static VBox empty(String title,String body){VBox v=new VBox(10,new Icon("chat"),label(title,"section-title"),label(body,"muted"));v.setAlignment(Pos.CENTER);v.getStyleClass().add("empty-state");return v;}
    public static void info(Node owner,String title,String body){Alert a=new Alert(Alert.AlertType.INFORMATION);a.initOwner(owner.getScene().getWindow());a.setTitle(title);a.setHeaderText(title);a.setContentText(body);a.getDialogPane().getStylesheets().add(Ui.class.getResource("/studio/cipher/css/theme.css").toExternalForm());a.showAndWait();}
    public static void actionMenu(Button button,String[] labels,Consumer<Integer> action){ContextMenu menu=new ContextMenu();for(int i=0;i<labels.length;i++){final int n=i;MenuItem item=new MenuItem(labels[i]);item.setOnAction(e->action.accept(n));menu.getItems().add(item);}button.setOnAction(e->menu.show(button,Side.BOTTOM,0,4));}
}
