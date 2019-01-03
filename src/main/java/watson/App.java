package watson;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Parent;

import java.io.IOException;

public class App extends Application {

  protected static Scene scene;
  protected static Stage stage;

  public static void main (String[] args) {
    launch(args);
  }

  @Override
  public void start (Stage s) throws IOException {
    Parent loginPage = FXMLLoader.load(getClass().getClassLoader().getResource("LoginFXML.fxml"));
    scene = new Scene(loginPage, 800, 450);

    stage = s;
    stage.setTitle("MyContacts :: Log In");
    stage.setScene(scene);
    stage.show();
  }
}
