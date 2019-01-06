package watson;

import org.controlsfx.control.table.TableFilter;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;

public class OwnerContactsController extends MasterController {

  @FXML
  private ComboBox<String> selection;

  private ObservableList<String> selections = FXCollections.observableArrayList();

  //----------------------------------------------------------------------------
  //  initialize() is called when OwnerContactsFXML.fxml is loaded
  //----------------------------------------------------------------------------

  @FXML
  public void initialize() {

    data.clear(); // clear table data
    table.setItems(data); // clear table

    // add all non-DB USERS to list of available USERS
    selections.clear();
    for (String USER : db.users().get())
      if (!USER.equals(OWNER)) selections.add(USER);
    selection.setItems(selections);
    selection.getSelectionModel().selectFirst();

    // load user's CONTACTS table
    displayTable(selection.getValue() + ".CONTACTS", 190, true);

  } // end initialize()

  @FXML
  private void changeSelection() {
    displayTable(selection.getValue() + ".CONTACTS", 190, false);
  }

}

