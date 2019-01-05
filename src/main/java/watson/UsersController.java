package watson;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import java.awt.Desktop;
import java.net.URISyntaxException;
import java.net.URL;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import static watson.MasterController.*;

public class UsersController {

  //----------------------------------------------------------------------------
  //  objects in FXML UI, local variables
  //----------------------------------------------------------------------------

  @FXML private TableView<ObservableList<String>>  usersTable;

  private static ObservableList<ObservableList<String>> data
    = FXCollections.observableArrayList();

  //----------------------------------------------------------------------------
  //  method called when UsersFXML.fxml is loaded
  //----------------------------------------------------------------------------

  @FXML
  public void initialize() {

    TableColumn<ObservableList<String>, String> column;
    data.clear();

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

  } // end initialize()

  //----------------------------------------------------------------------------
  //  'Users' menu items
  //----------------------------------------------------------------------------

  @FXML
  private boolean addUser() { return usersOpsHelper("ADD"); }

  @FXML
  private boolean deleteUsers() { return usersOpsHelper("DELETE"); }

  @FXML
  private boolean resetPasswords() { return usersOpsHelper("RESET"); }

  // helper method for 'Users' menu items, above
  private boolean usersOpsHelper (String FUNCTION) {

    boolean  conf        = false; // confirm action
    String   confMessage = null;  // confirmation prompt
    String  errorMessage = null;  // default error message

    switch (FUNCTION) {

      case "DELETE":
        conf = true;
        confMessage = "Are you sure you want to delete the selected users?";
        errorMessage = "The database owner, \"" + OWNER + "\", cannot be deleted.";
        break;

      case "RESET":
        conf = true;
        confMessage = "Are you sure you want to reset these users' passwords?";
        errorMessage = "The database owner cannot reset their own password. " +
          "Instead, change your password by selecting 'Account' > 'Change " +
          "Password' from the menu.";
        break;

      case "ADD":
        conf = false;
        errorMessage = "The user \"%s\" already exists. Try a different " +
          "username. (Remember that usernames are not case-sensitive: " +
          "\"Jeff\" is the same user as \"JEFF\".)";
        break;

      default:
        return false;
    }

    Alert alert;  // if confirmation is needed from the user, send an
    if (conf) {   // alert and wait for the user's response
      alert = new Alert(AlertType.CONFIRMATION, confMessage,
        ButtonType.YES, ButtonType.CANCEL);
      alert.showAndWait();
      conf = conf && (alert.getResult() != ButtonType.YES);
    }

    // if conf == false here, we don't need confirmation OR we got it
    if (!conf) { // alert.getResult() == ButtonType.YES) {

      // have the DBO re-enter their password
      Dialog<ButtonType> dialog = new Dialog<>();
      dialog.setHeaderText("Confirm Password:");
      dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

      VBox vb = new VBox();
      PasswordField password = new PasswordField();
      vb.getChildren().add(password);
      dialog.getDialogPane().setContent(vb);

      // request focus on the password field by default
      Platform.runLater(() -> password.requestFocus());
      dialog.showAndWait();

      // quietly quit if user closed window or clicked "CANCEL"
      if (dialog.getResult() != ButtonType.OK) return false;

      // alert if DBO password is incorrect
      if (!db.verifyPassword(OWNER, get(password))) {
        alert = new Alert(AlertType.ERROR, "Incorrect password");
        alert.showAndWait();
        return false;
      }

      // if DELETE or RESET, get list of users to act on:
      if (FUNCTION.equals("DELETE") || FUNCTION.equals("RESET")) {

        List<String> USERS = usersTable.getSelectionModel().getSelectedItems()
          .stream().map(e -> e.get(0)).collect(Collectors.toList());

        // to avoid confusion, cancel entirely if DBO is selected
        if (USERS.contains(OWNER)) {
          alert = new Alert(AlertType.ERROR, errorMessage, ButtonType.OK);
          alert.showAndWait();
          return false;
        }

        // act on all non-DBO users
        for (String USER : USERS) {
          switch (FUNCTION) {

            case "DELETE":
              db.deleteUser(USER, get(password));
              break;

            case "RESET":
              db.resetPassword(USER, (USER + "pass").toLowerCase(), get(password));
              break;

            default:
              return false;
          }
        }

      } else if (FUNCTION.equals("ADD")) {

        // have the user re-enter their password
        dialog = new Dialog<>();
        dialog.setHeaderText("Add New User:");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane gp = new GridPane();

        Label uLabel = new Label("New User Name:");
        Label pLabel = new Label("New User Password:");

        gp.add(uLabel, 0, 0);
        gp.add(pLabel, 0, 1);

        TextField username = new TextField();
        PasswordField userpass = new PasswordField();

        gp.add(username, 1, 0);
        gp.add(userpass, 1, 1);

        gp.setPadding(new Insets(10, 10, 10, 10));
        gp.setHgap(10);
        gp.setVgap(10);
        dialog.getDialogPane().setContent(gp);

        // request focus on the old password field by default
        Platform.runLater(() -> username.requestFocus());
        dialog.showAndWait();

        // quietly quit if user closed window or clicked "CANCEL"
        if (dialog.getResult() != ButtonType.OK) return false;

        // ...otherwise, create a new alert
        alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.OK);

        // attempt to add a new user
        boolean success = db.addUser(get(username), get(userpass), get(password));

        if (success) {
          alert.setContentText("New user successfully added.");
          alert.showAndWait();

        } else {
          alert.setContentText("New user could not be created. See log for details.");
          alert.showAndWait();
          return false;
        }

      } else return false;

      refreshApp("UsersFXML.fxml");
      return true;
    }

    return false;
  } // end usersOpsHelper()

  //----------------------------------------------------------------------------
  //  'About' menu items
  //----------------------------------------------------------------------------

  private void web (String url) {
    try {
      Desktop.getDesktop().browse(new URL(url).toURI());

    } catch (IOException ex) {
      IOUtils.printError("web()",
        "IOException encountered while trying to navigate to " + url);

    } catch (URISyntaxException ex) {
      IOUtils.printError("web()",
        "URISyntaxException encountered while trying to navigate to " + url);
  } }

  @FXML
  private void gotoGitHub() {
    web("https://github.com/awwsmm/IBAT");
  }

  @FXML
  private void gotoWebsite() {
    web("http://awwsmm.com/");
  }

  //----------------------------------------------------------------------------
  //  menu items common to all pages: defined in MasterController.java
  //----------------------------------------------------------------------------

  @FXML private boolean changePassword() { return MasterController.changePassword(); }
  @FXML private void logout() { MasterController.logout(); }
  @FXML private void quit() { MasterController.quit(); }

}

