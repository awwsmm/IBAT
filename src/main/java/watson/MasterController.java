package watson;

import org.controlsfx.control.table.TableFilter;

import java.awt.Desktop;
import java.net.URISyntaxException;
import java.net.URL;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
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

import static watson.App.*;

/**
  *
  * Master Controller class which provides global variables and methods for the
  * other Controllers to access and use.
  *
  **/
public class MasterController {

  // private constructor for utility class
//  private MasterController() { }

  /** Database object to use in GUI. **/
  protected static Database db = null;

  /** Name of database to use in GUI. **/
  protected static String DBNAME = null;

  /** Current user of GUI. **/
  protected static String USER = null;

  /** Owner of database being used in GUI. **/
  protected static String OWNER = null;

  /** Is current user of GUI the owner of the database? **/
  protected static boolean isOwner = false;

  /** List of users for the database in the GUI. **/
  protected static List<String> USERS = null;

  // main table on page
  @FXML protected TableView<ObservableList<String>> table;

  // data for table
  protected ObservableList<ObservableList<String>> data
    = FXCollections.observableArrayList();

  /**
    * Extracts the {@link String} value of a {@link TextField} or
    * {@link PasswordField}.
    *
    * @param field {@link TextField} or {@link PasswordField} which should be
    * converted to a {@link String}
    *
    * @return the {@link String} contents of the given {@link TextField} or
    * {@link PasswordField}, or {@code null} if {@code field} is {@code null}
    *
    **/
  protected static String get (TextField field) {
    if (field == null) return null;
    return field.getCharacters().toString();
  }

  /**
    * Attempts to refresh the GUI, loading the FXML page specified.
    *
    * <p>If {@code title} is {@code null}, the window title will not be changed.
    * To remove / clear the window title, set {@code title = ""}.</p>
    *
    * @param fxml *.fxml page to open
    * @param title title to set in the menubar at the top of the app
    *
    * @return {@code true} if the specified page was successfully loaded
    *
    **/
  protected static boolean refreshApp (String fxml, String title) {
    try {
      scene = new Scene(FXMLLoader.load(MasterController.class.getClassLoader().getResource(fxml)), 800, 450);
      if (title != null) stage.setTitle(title);
      stage.setScene(scene);
      stage.show();
      return true;

    } catch (IOException ex) {
      IOUtils.printError("refreshApp()", "IOException while attempting to refresh app");
//    ex.printStackTrace();
      return false;
    }
  }

  /**
    * Calls {@link refreshApp(String, String)} with {@code title = null}.
    *
    * @param fxml *.fxml page to open
    *
    * @return {@code true} if the specified page was successfully loaded
    *
    **/
  protected static boolean refreshApp (String fxml) {
    return refreshApp(fxml, null);
  }

  //----------------------------------------------------------------------------
  //  'Account' menu items
  //----------------------------------------------------------------------------

  /**
    * Opens a prompt to change the user's password, then logs them out so they
    * can log back in with their new password.
    *
    * @return {@code true} if and only if the user's password was successfully
    * changed
    *
    **/
  @FXML
  private boolean changePassword() {

    // have the user re-enter their password
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.setHeaderText("Change Password:");
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    GridPane gp = new GridPane();

    Label oldLabel  = new Label("Current password:");
    Label newLabel1 = new Label("New password:");
    Label newLabel2 = new Label("Repeat new password:");

    gp.add(oldLabel,  0, 0);
    gp.add(newLabel1, 0, 1);
    gp.add(newLabel2, 0, 2);

    PasswordField oldPass  = new PasswordField();
    PasswordField newPass1 = new PasswordField();
    PasswordField newPass2 = new PasswordField();

    gp.add(oldPass,   1, 0);
    gp.add(newPass1,  1, 1);
    gp.add(newPass2,  1, 2);

    gp.setPadding(new Insets(10, 10, 10, 10));
    gp.setHgap(10);
    gp.setVgap(10);
    dialog.getDialogPane().setContent(gp);

    // request focus on the old password field by default
    Platform.runLater(() -> oldPass.requestFocus());
    dialog.showAndWait();

    // quietly quit if user closed window or clicked "CANCEL"
    if (dialog.getResult() != ButtonType.OK) return false;

    // ...otherwise, create a new alert
    Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.OK);

    if (!get(newPass1).equals(get(newPass2))) {
      alert.setContentText("New passwords do not match. Password not changed.");
      alert.showAndWait();
      return false;
    }

    // attempt to change this user's password
    boolean success = db.changePassword(get(oldPass), get(newPass1));

    if (success) {
      alert.setContentText("Password successfully changed. You will now be logged out.");
      alert.showAndWait();
      logout();
      return true;

    } else {
      alert.setContentText("Password could not be changed. See log for details.");
      alert.showAndWait();
      return false;
    }
  }

  /**
    * Returns to the login screen and disconnects the database (logs out the user).
    *
    **/
  @FXML
  private void logout() {
    refreshApp("LoginFXML.fxml", "MyContacts :: Log In");
    db.disconnect();
  }

  /**
    * Logs the user out, then quits the program.
    *
    **/
  @FXML
  private void quit() {
    logout(); System.exit(0);
  }

  //----------------------------------------------------------------------------
  //  'Users' menu items
  //----------------------------------------------------------------------------

  @FXML
  private boolean addUser() { return usersOpsHelper("ADD"); }

  @FXML
  private boolean deleteUsers() { return usersOpsHelper("DELETE"); }

  @FXML
  private boolean resetPasswords() { return usersOpsHelper("RESET"); }

  // helper method for 'Users' menu items
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

        List<String> USERS = table.getSelectionModel().getSelectedItems()
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

      refreshApp("OwnerUsersFXML.fxml", "MyContacts :: User Management");
      return true;
    }

    return false;
  } // end usersOpsHelper()

  //----------------------------------------------------------------------------
  //  'Tables' menu items
  //----------------------------------------------------------------------------

  @FXML
  private void ownerContacts() {
    refreshApp("OwnerContactsFXML.fxml", "MyContacts :: User Contacts");
  }

  @FXML
  private void ownerGroups() {
    refreshApp("OwnerGroupsFXML.fxml", "MyContacts :: User Groups");
  }

  @FXML
  private void ownerSecure() {
    refreshApp("OwnerSecureFXML.fxml", "MyContacts :: User Login Information");
  }

  @FXML
  private void ownerUsers() {
    refreshApp("OwnerUsersFXML.fxml", "MyContacts :: User Management");
  }

  @FXML
  protected void displayTable (String tableName, double columnWidth, boolean firstTime) {

    // convert table to FX-formatted table
    List<List<String>> TABLE = db.table(tableName);

    // loop over table rows
    TableColumn<ObservableList<String>, String> column;
    for (int rr = 0; rr < TABLE.size(); ++rr) {
      List<String> row = TABLE.get(rr);

      // add column headers to table
      if (rr == 0) { if(firstTime) {
          for (int cc = 0; cc < row.size(); ++cc) {
            final int ff = cc;
            column = new TableColumn<>(row.get(cc));
            column.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().get(ff)));
            column.setPrefWidth(columnWidth);
            table.getColumns().add(column);

      } } } else { // add all other rows of data
        ObservableList<String> tableRow = FXCollections.observableArrayList();
        tableRow.clear();
        for (String cell : row) tableRow.add(cell);
        data.add(tableRow);
    } }

    table.setItems(data);
    table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    TableFilter tf = TableFilter.forTableView(table).apply();

  }

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


}
