package watson;

import org.controlsfx.control.table.TableFilter;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;

public class OwnerSecureController extends MasterController {

  @FXML
  private ComboBox<String> selection;

  private ObservableList<String> selections = FXCollections.observableArrayList();

  //----------------------------------------------------------------------------
  //  initialize() is called when OwnerSecureFXML.fxml is loaded
  //----------------------------------------------------------------------------

  @FXML
  public void initialize() {

    data.clear(); // clear table data
    table.setItems(data); // clear table

    // add all USERS to list of available USERS
    selections.clear();
    for (String USER : db.users().get())
      selections.add(USER);
    selection.setItems(selections);
    selection.getSelectionModel().selectFirst();

    // load user's SECURE table
    displayTable(selection.getValue() + ".SECURE", 379, true);

  } // end initialize()

  @FXML
  private void changeSelection() {
    data.clear(); // clear table data
    table.setItems(data); // clear table
    displayTable(selection.getValue() + ".SECURE", 379, false);
  }

}

