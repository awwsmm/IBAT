package watson;

import javafx.fxml.FXML;

public class UserContactsController extends MasterController {

  //----------------------------------------------------------------------------
  //  initialize() is called when UserContactsFXML.fxml is loaded
  //----------------------------------------------------------------------------

  @FXML
  public void initialize() {

    data.clear(); // clear table data
    table.setItems(data); // clear table

    // load user's CONTACTS table
    displayTable(USER + ".CONTACTS", 189, true);

  } // end initialize()

}

