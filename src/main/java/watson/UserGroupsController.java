package watson;

import javafx.fxml.FXML;

public class UserGroupsController extends MasterController {

  //----------------------------------------------------------------------------
  //  initialize() is called when UserGroupsFXML.fxml is loaded
  //----------------------------------------------------------------------------

  @FXML
  public void initialize() {

    data.clear(); // clear table data
    table.setItems(data); // clear table

    // load user's CONTACTS table
    displayTable(USER + ".GROUPS", 253, true);

  } // end initialize()

}

