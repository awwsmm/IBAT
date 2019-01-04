package watson;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Pair;

import static watson.MasterController.*;



public class UsersController {

  @FXML private TextField      newUserName;
  @FXML private PasswordField  newUserPassword;
  @FXML private PasswordField  ownerPassword;
  @FXML private Text           usersMessage;

  @FXML
  private TableView<ObservableList<String>> usersTable;

  private static ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

  @FXML
  public void initialize() {

    //--------------------------------------------------------------------------
    //  build the user information table
    //--------------------------------------------------------------------------

    TableColumn<ObservableList<String>, String> column;
    data.clear();

    // loop over all users, get each username, hash, salt
    boolean firstUser = true;
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
              column.setPrefWidth(800.0 / 5.0 - 5.0);
              usersTable.getColumns().add(column);

        } } } else { // add all other rows of data
          ObservableList<String> tableRow = FXCollections.observableArrayList();
          tableRow.clear();
          tableRow.add(USER);
          tableRow.add(nContacts);
          tableRow.add(nGroups);
          for (String cell : row) tableRow.add(cell);
          data.add(tableRow);
    } } }

    usersTable.setItems(data);
    usersTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    //--------------------------------------------------------------------------
    //  build the right-click dialog menu
    //--------------------------------------------------------------------------
/*
    ContextMenu cm = new ContextMenu();

    MenuItem mi1 = new MenuItem("Delete Selected Users");
    MenuItem mi2 = new MenuItem("Reset Selected Users' Passwords");

    cm.getItems().add(mi1);
    cm.getItems().add(mi2);

    // display the menu when the user right-clicks on the table
    usersTable.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
      @Override
      public void handle(MouseEvent t) {
        if(t.getButton() == MouseButton.SECONDARY) {
          cm.show(usersTable, t.getScreenX(), t.getScreenY());
    } } });

    // Delete Selected Users
    mi1.setOnAction(new EventHandler<ActionEvent>() {
      public void handle (ActionEvent t) { usersOpsHelper("DELETE"); }
    });

    // Reset Selected Users' Passwords
    mi2.setOnAction(new EventHandler<ActionEvent>() {
      public void handle (ActionEvent t) { usersOpsHelper("RESET"); }
    });
*/
    // add filtering capabilities to table

  } // end initialize()

  //--------------------------------------------------------------------------
  //  helper method to verify owner password, etc. before users operations
  //--------------------------------------------------------------------------

  private boolean usersOpsHelper (String FUNCTION) {

    String msg = null;
    String ownerWarning = null;

    switch (FUNCTION) {

      case "DELETE":
        msg = "Are you sure you want to delete the selected users?";
        ownerWarning = "Database owner cannot be deleted.";
        break;

      case "RESET":
        msg = "Are you sure you want to reset these users' passwords?";
        ownerWarning = "Database owner cannot reset their own password.";
        break;

      default:
        return false;
    }

    Alert alert = new Alert(AlertType.CONFIRMATION, msg,
      ButtonType.YES, ButtonType.CANCEL);
    alert.showAndWait();

    // have the user verify that they want to delete these users
    if (alert.getResult() == ButtonType.YES) {

      // have the user re-enter their password
      Dialog<Pair<String, String>> dialog = new Dialog<>();
      dialog.setHeaderText("Confirm Password:");
      dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);

      VBox contents = new VBox();
      PasswordField password = new PasswordField();
      contents.getChildren().add(password);
      dialog.getDialogPane().setContent(contents);

      // request focus on the password field by default
      Platform.runLater(() -> password.requestFocus());
      dialog.showAndWait();

      // loop over users and delete each selected one
      List<String> USERS = usersTable.getSelectionModel().getSelectedItems()
        .stream().map(e -> e.get(0)).collect(Collectors.toList());

      if (USERS.contains(OWNER)) {
        alert = new Alert(AlertType.ERROR, ownerWarning, ButtonType.OK);
        USERS.remove(OWNER);
        alert.showAndWait();
      }

      // delete all non-DBO users
      Boolean correctPassword = true;
      for (String USER : USERS) {
        switch (FUNCTION) {

          case "DELETE":
            correctPassword = correctPassword &&
              db.deleteUser(USER, get(password));
            break;

          case "RESET":
            correctPassword = correctPassword &&
              db.resetPassword(USER, (USER + "pass").toLowerCase(), get(password));
            break;

          default:
            return false;
        }

        if (!correctPassword) break;
      } // end loop over USERS

      // alert if DBO password is incorrect
      if (!correctPassword) {
        alert = new Alert(AlertType.ERROR, "Incorrect password");
        alert.showAndWait();
        return false;
      }

      // refresh table view and send message to user
      USERS = db.users().get();

//      try {
        refreshApp("UsersFXML.fxml");
//        Parent usersPage = FXMLLoader.load(getClass().getClassLoader().getResource("UsersFXML.fxml"));
//        App.scene = new Scene(usersPage, 800, 450);
//        App.stage.setScene(App.scene);
//        App.stage.show();
        return true;

//      } catch (IOException ex) {
//        IOUtils.printError("initialize()", "IOException when refreshing table view");
//        usersMessage.setText("Error refreshing table");
//        return false;
//      }
    }

    return false;
  } // end usersOpsHelper()




  @FXML
  public void addUserButton() throws IOException {

    boolean success = db.addUser(
      get(newUserName), get(newUserPassword), get(ownerPassword));

    if (success) {
      USERS = db.users().get();
      refreshApp("UsersFXML.fxml");

//      Parent usersPage = FXMLLoader.load(getClass().getClassLoader().getResource("UsersFXML.fxml"));
//      App.scene = new Scene(usersPage, 800, 450);
//      App.stage.setScene(App.scene);
//      App.stage.show();

    } else
      usersMessage.setText("User could not be added. See log for details.");
  }


  @FXML
  private boolean changePassword() {
return false;
//return false; //     return MasterController.changePassword();
  }

  @FXML
  private boolean logout() {
return false;
//return false; //     return MasterController.logout();
  }

  @FXML
  private boolean quit() {
return false; //     return MasterController.quit();
  }


}
