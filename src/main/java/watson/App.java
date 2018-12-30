package watson;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Parent;

public class App extends Application {

  public static void main (String[] args) {
    launch(args);
  }

  @Override
  public void start (Stage stage) throws Exception {
    Parent root = FXMLLoader.load(getClass().getClassLoader().getResource("FXML.fxml"));
    Scene scene = new Scene(root, 800, 450);

    stage.setTitle("title");
    stage.setScene(scene);
    stage.show();
  }
}
