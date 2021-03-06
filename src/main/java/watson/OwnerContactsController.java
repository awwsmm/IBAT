package watson;

import org.controlsfx.control.table.TableFilter;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;

public class OwnerContactsController extends MasterController {

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
    displayTable(selection.getValue() + ".CONTACTS", 189, true);

  } // end initialize()

  @FXML
  private void changeSelection() {

    data.clear(); // clear table data
    table.setItems(data); // clear table

    displayTable(selection.getValue() + ".CONTACTS", 189, false);
  }

}

