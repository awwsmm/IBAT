package watson;

import javafx.fxml.FXML;
import javafx.scene.text.Text;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.event.ActionEvent;
import javafx.scene.control.TableView;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class LoginController {

  @FXML private TextField      dbName;
  @FXML private PasswordField  bootPassword;
  @FXML private TextField      username;
  @FXML private PasswordField  password;
  @FXML private Text           message;

  // get the database if login is correct
  protected static Database db = null;

  // current user, DBO, list of users
  protected static String USER = null;
  protected static String OWNER = null;
  protected static boolean isOwner = false;
  protected static List<String> USERS = null;

  @FXML
  public void loginButton() throws IOException {

    Optional<Database> optdb = Database.connect(
      dbName.getCharacters().toString(), bootPassword.getCharacters().toString(),
      username.getCharacters().toString(), password.getCharacters().toString());

    if (!optdb.isPresent()) { message.setText("Invalid login information.");

    } else {

      // get the database
      db = optdb.get();

      // current user and database owner
      USER = db.user().get();
      OWNER = db.owner().get();
      isOwner = db.userIsDBO();

      //------------------------------------------------------------------------
      //  if user is DBO, send to user management page; else to contacts page
      //------------------------------------------------------------------------

      // get user tables
      if (isOwner) {
        USERS = db.users().get();

        message.setText(""); // open up the default page -- the users management page
        Parent usersPage = FXMLLoader.load(getClass().getClassLoader().getResource("UsersFXML.fxml"));
        App.scene = new Scene(usersPage, 800, 450);
        App.stage.setScene(App.scene);
        App.stage.show();

      // get the secure table if this user is the DBO
      } else {

        message.setText(""); // open up the default page -- the contacts page
        Parent contactsPage = FXMLLoader.load(getClass().getClassLoader().getResource("ContactsFXML.fxml"));
        App.scene = new Scene(contactsPage, 800, 450);
        App.stage.setScene(App.scene);
        App.stage.show();


      }
    }
  }

  // allow user to press "Enter" after user password to submit form
  @FXML
  public void onEnter (ActionEvent ae) throws IOException {
    loginButton();
  }

}
