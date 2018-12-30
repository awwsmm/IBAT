package watson;

import javafx.fxml.FXML;
import javafx.scene.text.Text;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.Optional;

public class FXMLController {

  @FXML private TextField      dbName;
  @FXML private PasswordField  bootPassword;
  @FXML private TextField      username;
  @FXML private PasswordField  password;
  @FXML private Text           message;

  @FXML
  public void loginButton() {

    Optional<Database> optdb = Database.connect(
      dbName.getCharacters().toString(), bootPassword.getCharacters().toString(),
      username.getCharacters().toString(), password.getCharacters().toString());

    if (!optdb.isPresent()) {
      message.setText("Invalid login information.");
    } else {
      message.setText("");
    }

  }


}
