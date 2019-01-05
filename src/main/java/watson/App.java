package watson;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import static watson.MasterController.*;

/**
  * Main {@link Application} of MyContacts.
  *
  **/
public class App extends Application {

  /** Main {@link Scene} of app. **/
  protected static Scene scene;

  /** Main {@link Stage} of app. **/
  protected static Stage stage;

  /**
    * Entry point for MyContacts application.
    *
    * @param args arguments passed to
    * {@link Application#launch(String...) Application.launch()}
    *
    **/
  public static void main (String[] args) {
    launch(args);
  }

  @Override
  public void start (Stage s) {
    stage = s;
    refreshApp("LoginFXML.fxml", "MyContacts :: Log In");
  }

}
