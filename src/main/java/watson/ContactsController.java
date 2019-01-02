package watson;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import javafx.fxml.FXML;

public class ContactsController {

  @FXML
  protected static TableView<ObservableList<String>> contactsTable;

  protected static ObservableList<ObservableList<String>> data;

//  ObservableList<ObservableList> CONTACTS = FXCollections.observableArrayList();


}
