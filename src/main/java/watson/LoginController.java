package watson;

import java.io.IOException;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.text.Text;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;

import static watson.MasterController.*;

public class LoginController {

  // FXML fields from accompanying *.fxml file
  @FXML private TextField      dbName;
  @FXML private PasswordField  bootPassword;
  @FXML private TextField      username;
  @FXML private PasswordField  password;
  @FXML private Text           message;

  @FXML
  public void loginButton() throws IOException {

    Optional<Database> optdb = Database.connect(
      get(dbName), get(bootPassword), get(username), get(password));

    if (!optdb.isPresent()) {
      message.setText("Invalid login information.");
      IOUtils.printError("loginButton()", "Invalid login information");

    } else {

      // get the database and its name
      db = optdb.get();
      DBNAME = db.name();

      // current user and database owner
      USER = db.user().get();
      OWNER = db.owner().get();
      isOwner = db.userIsDBO();

      // if DBO, load USERS list and open user management page
      if (isOwner) {
        USERS = db.users().get();
        refreshApp("OwnerUsersFXML.fxml", "MyContacts :: User Management");

      } else // otherwise, leave USERS list null and open CONTACTS table
        refreshApp("UserContactsFXML.fxml", "MyContacts :: Contacts");

    }
  } // end of loginButton() method


  // allow user to press "Enter" after user password to submit form
  @FXML
  public void onEnter (ActionEvent ae) throws IOException {
    loginButton();
  }

}
