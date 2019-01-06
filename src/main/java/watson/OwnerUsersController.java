package watson;

import org.controlsfx.control.table.TableFilter;

import java.util.List;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;

public class OwnerUsersController extends MasterController {

  //----------------------------------------------------------------------------
  //  initialize() is called when OwnerUsersFXML.fxml is loaded
  //----------------------------------------------------------------------------

  @FXML
  public void initialize() {

    data.clear(); // clear table data
    table.setItems(data); // clear table

    // loop over all users, get each username, hash, salt
    boolean firstUser = true;
    USERS = db.users().get();
    for (String USER : USERS) {

      // convert table to FX-formatted table
      List<List<String>> TABLE = db.table(USER + ".SECURE");

      // if DBO, these are "N/A"
      String nContacts, nGroups;

      if (!OWNER.equals(USER)) {
        nContacts = ((Integer) (db.table(USER + ".CONTACTS").size() - 1)).toString();
        nGroups = ((Integer) (db.table(USER + ".GROUPS").size() - 1)).toString();
      } else {
        nContacts = "N/A";
        nGroups = "N/A";
      }

      // loop over table rows
      TableColumn<ObservableList<String>, String> column;
      for (int rr = 0; rr < TABLE.size(); ++rr) {
        List<String> row = TABLE.get(rr);

        // add column headers to table, only for first user encountered
        if (rr == 0) { if (firstUser) { firstUser = false;
            row.add(0, "Username");
            row.add(1, "# Contacts");
            row.add(2, "# Groups");
            for (int cc = 0; cc < row.size(); ++cc) {
              final int ff = cc;
              column = new TableColumn<>(row.get(cc));
              column.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().get(ff)));
              column.setPrefWidth(151);
              table.getColumns().add(column);

        } } } else { // add all other rows of data
          ObservableList<String> tableRow = FXCollections.observableArrayList();
          tableRow.clear();
          tableRow.add(USER);
          tableRow.add(nContacts);
          tableRow.add(nGroups);
          for (String cell : row) tableRow.add(cell);
          data.add(tableRow);
    } } }

    table.setItems(data);
    table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    TableFilter tf = TableFilter.forTableView(table).apply();

  } // end initialize()

}

