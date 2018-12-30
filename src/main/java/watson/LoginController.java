package watson;

import javafx.fxml.FXML;
import javafx.scene.text.Text;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Parent;

import java.io.IOException;
import java.util.Optional;

public class LoginController {

  @FXML private TextField      dbName;
  @FXML private PasswordField  bootPassword;
  @FXML private TextField      username;
  @FXML private PasswordField  password;
  @FXML private Text           message;

  @FXML
  public void loginButton() throws IOException {

    Optional<Database> optdb = Database.connect(
      dbName.getCharacters().toString(), bootPassword.getCharacters().toString(),
      username.getCharacters().toString(), password.getCharacters().toString());

    if (!optdb.isPresent()) { message.setText("Invalid login information.");

    } else {
      message.setText("");
      Parent contactsPage = FXMLLoader.load(getClass().getClassLoader().getResource("ContactsFXML.fxml"));
      App.scene = new Scene(contactsPage, 800, 450);
      App.stage.setScene(App.scene);
      App.stage.show();
    }

  }

}
