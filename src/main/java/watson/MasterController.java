package watson;

import java.io.IOException;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.fxml.FXMLLoader;

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
      ex.printStackTrace();
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
/*
  protected static boolean changePassword() {
    System.out.println("\"Change Password\" pressed");
    return false;
  }

  protected static boolean logout() {
    System.out.println("\"Log Out\" pressed");
    return false;
  }

  protected static boolean quit() {
    System.out.println("\"Quit\" pressed");
    return false;
  }
*/
}
