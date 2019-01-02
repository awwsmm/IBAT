package watson;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import javafx.fxml.FXML;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;
import java.util.List;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.ReadOnlyObjectWrapper;
import java.net.URL;
import javafx.scene.control.PasswordField;
import java.io.IOException;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.text.Text;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.util.Pair;
import javafx.scene.control.Dialog;
import javafx.scene.layout.VBox;
import java.util.stream.Collectors;
import javafx.application.Platform;



public class UsersController {

  @FXML private TextField      newUserName;
  @FXML private PasswordField  newUserPassword;
  @FXML private PasswordField  ownerPassword;
  @FXML private Text           usersMessage;

  @FXML
  private TableView<ObservableList<String>> usersTable;

  private static ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

  @FXML
  public void initialize() {

    //--------------------------------------------------------------------------
    //  build the user information table
    //--------------------------------------------------------------------------

    TableColumn<ObservableList<String>, String> column;
    data.clear();

    // loop over all users, get each username, hash, salt
    boolean firstUser = true;
    for (String USER : LoginController.USERS) {

      // convert table to FX-formatted table
      List<List<String>> TABLE = LoginController.db.table(USER + ".SECURE");

      // if DBO, these are "N/A"
      String nContacts, nGroups;

      if (!LoginController.OWNER.equals(USER)) {
        nContacts = ((Integer) (LoginController.db.table(USER + ".CONTACTS").size() - 1)).toString();
        nGroups = ((Integer) (LoginController.db.table(USER + ".GROUPS").size() - 1)).toString();
      } else {
        nContacts = "N/A";
        nGroups = "N/A";
      }

      // loop over table rows
      for (int rr = 0; rr < TABLE.size(); ++rr) {
        List<String> row = TABLE.get(rr);

        // add column headers to table, only for first user encountered
        if (rr == 0) { if (firstUser) { firstUser = false;
            row.add(0, "Username");
            row.add(1, "# Contacts");
            row.add(2, "# Groups");
            for (int cc = 0; cc < row.size(); ++cc) {
              final int ff = cc;
              column = new TableColumn<>(row.get(cc));
              column.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().get(ff)));
              column.setPrefWidth(800.0 / 5.0 - 5.0);
              usersTable.getColumns().add(column);

        } } } else { // add all other rows of data
          ObservableList<String> tableRow = FXCollections.observableArrayList();
          tableRow.clear();
          tableRow.add(USER);
          tableRow.add(nContacts);
          tableRow.add(nGroups);
          for (String cell : row) tableRow.add(cell);
          data.add(tableRow);
    } } }

    usersTable.setItems(data);
    usersTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    //--------------------------------------------------------------------------
    //  build the right-click dialog menu
    //--------------------------------------------------------------------------

    ContextMenu cm = new ContextMenu();

    MenuItem mi1 = new MenuItem("Delete Selected Users");
    MenuItem mi2 = new MenuItem("Print Selected Users");

    cm.getItems().add(mi1);
    cm.getItems().add(mi2);

    // display the menu when the user right-clicks on the table
    usersTable.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
      @Override
      public void handle(MouseEvent t) {
        if(t.getButton() == MouseButton.SECONDARY) {
          cm.show(usersTable, t.getScreenX(), t.getScreenY());
    } } });

    // action for "delete" menu item
    mi1.setOnAction(new EventHandler<ActionEvent>() {
      public void handle (ActionEvent t) {
        Alert alert = new Alert(AlertType.CONFIRMATION,
          "Are you sure you want to delete the selected users?",
          ButtonType.YES, ButtonType.CANCEL);
        alert.showAndWait();

        // have the user verify that they want to delete these users
        if (alert.getResult() == ButtonType.YES) {

          // have the user re-enter their password
          Dialog<Pair<String, String>> dialog = new Dialog<>();
          dialog.setHeaderText("Confirm Password:");
          dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);

          VBox contents = new VBox();
          PasswordField password = new PasswordField();
          contents.getChildren().add(password);
          dialog.getDialogPane().setContent(contents);

          // request focus on the password field by default
          Platform.runLater(() -> password.requestFocus());

          dialog.showAndWait();

          // loop over users and delete each selected one
          List<String> USERS = usersTable.getSelectionModel().getSelectedItems()
            .stream().map(e -> e.get(0)).collect(Collectors.toList());

          // the DBO cannot be deleted
          String OWNER = LoginController.OWNER;

          if (USERS.contains(OWNER)) {
            alert = new Alert(AlertType.ERROR, "Database owner '" +
              OWNER + "' cannot be deleted.", ButtonType.OK);
            USERS.remove(OWNER);
            alert.showAndWait();
          }

          // delete all non-DBO users
          Boolean correctPassword = true;
          for (String USER : USERS)
            correctPassword = correctPassword &&
              LoginController.db.deleteUser(USER, password.getCharacters().toString());

          // alert if DBO password is incorrect
          if (!correctPassword) {
            alert = new Alert(AlertType.ERROR, "Incorrect password");
            alert.showAndWait();
          }

          // refresh table view
          LoginController.USERS = LoginController.db.users().get();

          try {
            Parent usersPage = FXMLLoader.load(getClass().getClassLoader().getResource("UsersFXML.fxml"));
            App.scene = new Scene(usersPage, 800, 450);
            App.stage.setScene(App.scene);
            App.stage.show();

          } catch (IOException ex) {
            IOUtils.printError("initialize()", "IOException when refreshing table view");
          }




/*
// Create the username and password labels and fields.
GridPane grid = new GridPane();
grid.setHgap(10);
grid.setVgap(10);
grid.setPadding(new Insets(20, 150, 10, 10));

TextField username = new TextField();
username.setPromptText("Username");
PasswordField password = new PasswordField();
password.setPromptText("Password");

grid.add(new Label("Username:"), 0, 0);
grid.add(username, 1, 0);
grid.add(new Label("Password:"), 0, 1);
grid.add(password, 1, 1);

dialog.getDialogPane().setContent(grid);




            

*/



//          LoginController.db.
        }

      }
    });

    mi2.setOnAction(new EventHandler<ActionEvent>() {
      public void handle (ActionEvent t) {
        usersTable.getSelectionModel().getSelectedItems().stream().forEach(System.out::println);
    } });





  } // end initialize()

  @FXML
  public void addUserButton() throws IOException {

    boolean success = LoginController.db.addUser(
      newUserName.getCharacters().toString(),
      newUserPassword.getCharacters().toString(),
      ownerPassword.getCharacters().toString());

    if (success) {
      usersMessage.setText("User '" + newUserName.getCharacters().toString() + "' successfully added");

      LoginController.USERS = LoginController.db.users().get();

      Parent usersPage = FXMLLoader.load(getClass().getClassLoader().getResource("UsersFXML.fxml"));
      App.scene = new Scene(usersPage, 800, 450);
      App.stage.setScene(App.scene);
      App.stage.show();

    } else
      usersMessage.setText("User could not be added. See log for details.");




  }


}
