package watson;

import java.io.IOException;
import java.util.List;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import static watson.App.*;

/**
  *
  * Master Controller class which provides global variables and methods for the
  * other Controllers to access and use.
  *
  **/
public class MasterController {

  // private constructor for utility class
  private MasterController() { }

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
  //  methods common to multiple pages : called by lesser controllers
  //----------------------------------------------------------------------------

  /**
    * Opens a prompt to change the user's password, then logs them out so they
    * can log back in with their new password.
    *
    * @return {@code true} if and only if the user's password was successfully
    * changed
    *
    **/
  protected static boolean changePassword() {

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
  protected static void logout() {
    refreshApp("LoginFXML.fxml", "MyContacts :: Log In");
    db.disconnect();
  }

  /**
    * Logs the user out, then quits the program.
    *
    **/
  protected static void quit() {
    logout(); System.exit(0);
  }

}
