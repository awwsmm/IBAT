package watson;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.stream.Collectors;

import org.apache.derby.jdbc.EmbeddedDriver;

/**
  * Class for creating, loading, and manipulating
  * <a href="https://db.apache.org/derby/">Apache Derby</a> databases.
  *
  * <p>This package provides methods for creating and loading Derby databases,
  * tailored to the specifications of the end-of-term project for
  * <a href="https://www.ibat.ie/">IBAT College Dublin</a>'s
  * <a href="https://www.ibat.ie/courses/advanced-java-programming-course.html">Advanced
  * Diploma in Computer Programming (Advanced Java)</a> class, for the Autumn
  * 2018 term.</p>
  *
  * <h2>Creating a Database</h2>
  *
  * <p>Users can create or load databases with the {@link connect connect()}
  * method (and disconnect from them with the {@link disconnect disconnect()}
  * method):</p>
  *
  * <pre>{@code
  * jshell> import watson.*
  *
  * jshell> String dbname = "myDatabase"
  * dbname ==> "myDatabase"
  *
  * jshell> Optional<Database> optdb = Database.connect(dbname, "bootpass", "owner", "ownerpass")
  *        MESSAGE | connect() : database successfully initialised
  * optdb ==> Optional[watson.Database@69f63d95]
  *
  * jshell> Database db = optdb.get()
  * db ==> watson.Database@69f63d95
  * }</pre>
  *
  * <p>In the call to {@link connect connect()}, above, {@code dbname} is the
  * name of the database, {@code "bootpass"} is the password to boot the
  * database, and {@code "owner"} and {@code "ownerpass"} are the username and
  * password of the user logging into the database (in this case, the owner, as
  * this database is only just being created. The database name and boot
  * password are always required to open the database, though the username and
  * user password may change. Note that the owner of the database cannot be
  * altered or transferred, so in the above case, "owner" will always be the
  * owner of this database. Database owners (or DBOs) have certain permissions
  * that regular users don't have, but they cannot interact with the database
  * like normal users in that they do not have lists of contacts or groups.</p>
  *
  * <p>You can manually check if the current user is the database owner by
  * calling the {@link owner owner()} and {@link user user()} methods, or you
  * can use the convenience method {@link userIsDBO userIsDBO()}:</p>
  *
  * <pre>{@code
  * jshell> db.owner(); db.user()
  * $5 ==> Optional[OWNER]
  * $6 ==> Optional[OWNER]
  *
  * jshell> db.userIsDBO()
  * $7 ==> true
  * }</pre>
  *
  * <p>The DBO can get a list of all users with the {@link users users()}
  * method, and any user can get a list of all tables available to them by
  * calling the {@link tables tables()} method. As the DBO can edit any user
  * and any table, they have access to all of this information:</p>
  *
  * <pre>{@code
  * jshell> db.users(); db.tables()
  * $8 ==> Optional[[OWNER]]
  * $9 ==> [OWNER.SECURE]
  * }</pre>
  *
  * <p>The DBO only has a {@code SECURE} table, which holds their hashed
  * password and the salt used during the hashing process (see
  * {@link PasswordUtils}). Every other non-DBO user also has
  * a {@code CONTACTS} table, which holds a list of their contacts, and a
  * {@code GROUPS} table, which holds all of the relationships between the
  * user's contacts and their groups of contacts. {@code SECURE} tables are, by
  * default, hidden from non-DBO users. Each non-DBO user can only see their own
  * {@code CONTACTS} and {@code GROUPS} tables.</p>
  *
  * <p>Any user (including the DBO) can change their password with the
  * {@link changePassword changePassword()} method:</p>
  *
  * <pre>{@code
  * jshell> db.changePassword("ownerpass", "newpass")
  *       MESSAGE | changePassword() : password successfully changed
  * $10 ==> true
  * }</pre>
  *
  * <p>The user must enter their old password in order to change their password,
  * as a measure of added security.</p>
  *
  * <h2>Managing Database Users</h2>
  *
  * <p>The DBO can add users to the database by providing a username and a
  * password, and by verifying their own password:</p>
  *
  * <pre>{@code
  * jshell> db.addUser("usera", "userapass", "newpass")
  *        MESSAGE | addUser() : user 'usera' successfully added
  * $11 ==> true
  *
  * jshell> db.addUser("userb", "userbpass", "newpass")
  *        MESSAGE | addUser() : user 'userb' successfully added
  * $12 ==> true
  *
  * jshell> db.addUser("userc", "usercpass", "newpass")
  *        MESSAGE | addUser() : user 'userc' successfully added
  * $13 ==> true
  *
  * jshell> db.users(); db.tables()
  * $14 ==> Optional[[OWNER, USERA, USERB, USERC]]
  * $15 ==> [OWNER.SECURE, USERA.CONTACTS, USERA.GROUPS, USERA.SECURE, USERB.CONTACTS, USERB.GROUPS, USERB.SECURE, USERC.CONTACTS, USERC.GROUPS, USERC.SECURE]
  * }</pre>
  *
  * <p>The DBO can delete a user with the {@link deleteUser deleteUser()}
  * method (again, the DBO must re-enter their password as a security
  * measure):</p>
  *
  * <pre>{@code
  * jshell> db.deleteUser("userb", "newpass")
  *        MESSAGE | deleteUser() : user 'userb' successfully deleted
  * $16 ==> true
  *
  * jshell> db.users(); db.tables()
  * $17 ==> Optional[[OWNER, USERA, USERC]]
  * $18 ==> [OWNER.SECURE, USERA.CONTACTS, USERA.GROUPS, USERA.SECURE, USERC.CONTACTS, USERC.GROUPS, USERC.SECURE]
  * }</pre>
  *
  * <p>Note that usernames are case-insensitive, but passwords (obviously) are
  * case-sensitive. Passwords cannot be {@code null}, empty, or have any leading
  * or trailing whitespace characters.</p>
  *
  * <p>The DBO can reset a user's password (if they forgot it, for instance) by
  * calling the {@link resetPassword resetPassword()} method:</p>
  *
  * <pre>{@code
  * jshell> db.resetPassword("usera", "password1", "newpass")
  *        MESSAGE | resetPassword() : password successfully changed
  * $19 ==> true
  * }</pre>
  *
  * <h2>User Permissions</h2>
  *
  * <p>To disconnect from the database, close the {@code jshell} or call the
  * {@link disconnect disconnect()} method. You can then log back into the
  * database as a different user:</p>
  *
  * <pre>{@code
  * jshell> db.disconnect()
  *
  * jshell> optdb = Database.connect(dbname, "bootpass", "usera", "password1")
  *        MESSAGE | connect() : database successfully initialised
  * optdb ==> Optional[watson.Database@3e681bc]
  *
  * jshell> db = optdb.get()
  * db ==> watson.Database@3e681bc
  * }</pre>
  *
  * <p>Non-DBO users can see their own username, the username of the DBO, and
  * all of the tables available to them. They cannot see other users' tables,
  * their own {@code SECURE} table, or the list of usernames:</p>
  *
  * <pre>{@code
  * jshell> db.user(); db.owner(); db.tables(); db.users()
  * $23 ==> Optional[USERA]
  * $24 ==> Optional[OWNER]
  * $25 ==> [USERA.CONTACTS, USERA.GROUPS]
  *          ERROR | users() : only database owner can view list of users
  * $26 ==> Optional.empty
  * }</pre>
  *
  * <p>A table can be printed to the terminal with
  * {@link printTable printTable()}:</p>
  *
  * <pre>{@code
  * jshell> db.printTable("usera.contacts", 15)
  *
  *     | ID              | FIRSTNAME       | SURNAME         | PHONE           |
  *     | --------------- | --------------- | --------------- | --------------- |
  * }</pre>
  *
  * <p>(The above table is empty, so it's not very exciting at the moment.) The
  * second argument to {@link printTable printTable()} is the column width.</p>
  *
  * <h2>Managing Contacts</h2>
  *
  * <p>To add a contact to the {@code CONTACTS} table, create a {@link Contact}
  * object, set some of its fields, and add it to the table with
  * {@link addContact addContact()}:</p>
  *
  * <pre>{@code
  * jshell> Contact c = new Contact()
  * c ==> 
  *
  * jshell> c.set("phone", "+3531234567890").set("surname", "watson")
  * $29 ==> (SURNAME, PHONE) values ('watson', '+3531234567890')
  *
  * jshell> db.addContact(c)
  *        MESSAGE | addContact() : successfully added contact
  * $30 ==> true
  *
  * jshell> db.printTable("usera.contacts", 15)
  *
  *     | ID              | FIRSTNAME       | SURNAME         | PHONE           |
  *     | --------------- | --------------- | --------------- | --------------- |
  *     | 1               |                 | watson          | +3531234567890  |
  * }</pre>
  *
  * <p>In addition to simply printing tables to the terminal with
  * {@link printTable printTable()}, the user can get a table as a
  * {@code List<List<String>>} using {@link table table()}. The first row
  * returned always contains the column headers / labels:</p>
  *
  * <pre>{@code
  * jshell> db.table("usera.contacts")
  * $31 ==> [[ID, FIRSTNAME, SURNAME, PHONE], [1, null, watson, +3531234567890]]
  * }</pre>
  *
  * <p>Setting a field of a {@link Contact} object to {@code null}, an empty
  * {@link String}, or an all-whitespace {@link String} will set the value of
  * that field to {@code null} within the database. Setting a field of a
  * {@link Contact} object which has already been set will overwrite that
  * field:</p>
  *
  * <pre>{@code
  * jshell> c.set("phone", null).set("firstname", "michael"); db.addContact(c)
  * $32 ==> (FIRSTNAME, SURNAME) values ('michael', 'watson')
  *        MESSAGE | addContact() : successfully added contact
  * $33 ==> true
  *
  * jshell> c.set("phone", "+16109991234").set("firstname", "jessica"); db.addContact(c)
  * $34 ==> (FIRSTNAME, SURNAME, PHONE) values ('jessica', 'watson', '+16109991234')
  *        MESSAGE | addContact() : successfully added contact
  * $35 ==> true
  *
  * jshell> c.set("phone", "+15850001212").set("firstname", "dave").set("surname", "   "); db.addContact(c)
  * $36 ==> (FIRSTNAME, PHONE) values ('dave', '+15850001212')
  *        MESSAGE | addContact() : successfully added contact
  * $37 ==> true
  *
  * jshell> c.set("phone", null).set("firstname", "bob").set("surname", "jones"); db.addContact(c)
  * $38 ==> (FIRSTNAME, SURNAME) values ('bob', 'jones')
  *        MESSAGE | addContact() : successfully added contact
  * $39 ==> true
  *
  * jshell> c.set("firstname", "steve").set("surname", "jenkins"); db.addContact(c)
  * $40 ==> (FIRSTNAME, SURNAME) values ('steve', 'jenkins')
  *        MESSAGE | addContact() : successfully added contact
  * $41 ==> true
  *
  * jshell> c.set("surname", "smith"); db.addContact(c)
  * $42 ==> (FIRSTNAME, SURNAME) values ('steve', 'smith')
  *        MESSAGE | addContact() : successfully added contact
  * $43 ==> true
  *
  * jshell> db.printTable("usera.contacts", 15)
  *
  *     | ID              | FIRSTNAME       | SURNAME         | PHONE           |
  *     | --------------- | --------------- | --------------- | --------------- |
  *     | 1               |                 | watson          | +3531234567890  |
  *     | 2               | michael         | watson          |                 |
  *     | 3               | jessica         | watson          | +16109991234    |
  *     | 4               | dave            |                 | +15850001212    |
  *     | 5               | bob             | jones           |                 |
  *     | 6               | steve           | jenkins         |                 |
  *     | 7               | steve           | smith           |                 |
  * }</pre>
  *
  * <p>Contacts are given a unique, immutable ID number when they're created, and
  * they can be referenced by this ID number. To update a particular contact,
  * edit the {@link Contact} object and use the
  * {@link updateContact updateContact()} method:</p>
  *
  * <pre>{@code
  * jshell> c.set("phone", "+16108440000").set("firstname", "andrew").set("surname", "watson")
  * $45 ==> (FIRSTNAME, SURNAME, PHONE) values ('andrew', 'watson', '+16108440000')
  *
  * jshell> db.updateContact(1, c)
  *        MESSAGE | updateContact() : contact successfully updated
  * $46 ==> true
  *
  * jshell> c.set("phone", "+44567992847").set("surname", "jenkins")
  * $47 ==> (FIRSTNAME, SURNAME, PHONE) values ('andrew', 'jenkins', '+44567992847')
  *
  * jshell> db.updateContact(6, c)
  *        MESSAGE | updateContact() : contact successfully updated
  * $48 ==> true
  *
  * jshell> db.printTable("usera.contacts", 15)
  *
  *     | ID              | FIRSTNAME       | SURNAME         | PHONE           |
  *     | --------------- | --------------- | --------------- | --------------- |
  *     | 1               | andrew          | watson          | +16108440000    |
  *     | 2               | michael         | watson          |                 |
  *     | 3               | jessica         | watson          | +16109991234    |
  *     | 4               | dave            |                 | +15850001212    |
  *     | 5               | bob             | jones           |                 |
  *     | 6               | andrew          | jenkins         | +44567992847    |
  *     | 7               | steve           | smith           |                 |
  * }</pre>
  *
  * <p>One or more contacts can be deleted at a time with the
  * {@link deleteContacts deleteContacts()} method. New contacts continue the
  * ID numbering sequence, so no ID is ever used twice:</p>
  *
  * <pre>{@code
  * jshell> db.deleteContacts(2, 7)
  *        MESSAGE | deleteContacts() : contacts successfully deleted
  * $50 ==> true
  *
  * jshell> c.set("phone", "+44578390838").set("firstname", "mark").set("surname", "twain")
  * $51 ==> (FIRSTNAME, SURNAME, PHONE) values ('mark', 'twain', '+44578390838')
  *
  * jshell> db.addContact(c)
  *        MESSAGE | addContact() : successfully added contact
  * $52 ==> true
  *
  * jshell> db.printTable("usera.contacts", 15)
  *
  *     | ID              | FIRSTNAME       | SURNAME         | PHONE           |
  *     | --------------- | --------------- | --------------- | --------------- |
  *     | 1               | andrew          | watson          | +16108440000    |
  *     | 3               | jessica         | watson          | +16109991234    |
  *     | 4               | dave            |                 | +15850001212    |
  *     | 5               | bob             | jones           |                 |
  *     | 6               | andrew          | jenkins         | +44567992847    |
  *     | 8               | mark            | twain           | +44578390838    |
  * }</pre>
  *
  * <p>All user input is sanitised and validated to prevent SQL injection
  * attacks and ensure valid data:</p>
  *
  * <pre>{@code
  * jshell> c.set("phone", "wrong").set("firstname", "; drop tables")
  *          ERROR | set() : phone numbers can only contain digits and '+' signs
  *          ERROR | set() : name fields can only contain letters, spaces, dashes (-) and apostrophes (')
  * $54 ==> (FIRSTNAME, SURNAME, PHONE) values ('mark', 'twain', '+44578390838')
  * }</pre>
  *
  * <h2>Contact Group Management</h2>
  *
  * <p>Contacts can be collected into named groups (group names are
  * case-insensitive):</p>
  *
  * <pre>{@code
  * jshell> db.addToGroup("family", 1, 3, 4)
  *        MESSAGE | addToGroup() : successfully added to group
  * $55 ==> true
  * }</pre>
  *
  * <p>Groups must contain at least one contact. When the last contact in a
  * group is removed, that group no longer exists:</p>
  *
  * <pre>{@code
  * jshell> db.addToGroup("work")
  *          ERROR | addToGroup() : no contact IDs given
  * $56 ==> false
  *
  * jshell> db.addToGroup("work", 6, 7, 8)
  *        MESSAGE | addToGroup() : successfully added to group
  * $57 ==> true
  * }</pre>
  *
  * <p>You can see which contacts are in which group(s) by calling
  * {@link printTable printTable()} on the {@code GROUPS} table:</p>
  *
  * <pre>{@code
  * jshell> db.printTable("usera.groups", 15)
  *
  *     | ID              | NAME            | CONTACTID       |
  *     | --------------- | --------------- | --------------- |
  *     | 1               | FAMILY          | 1               |
  *     | 2               | FAMILY          | 3               |
  *     | 3               | FAMILY          | 4               |
  *     | 4               | WORK            | 6               |
  *     | 5               | WORK            | 7               |
  *     | 6               | WORK            | 8               |
  * }</pre>
  *
  * <p>One or more contacts can be removed from a particular group by calling
  * {@link removeFromGroup removeFromGroup()}. Every contact in a group can be
  * removed from that group by calling {@link deleteGroup deleteGroup()}:</p>
  *
  * <pre>{@code
  * jshell> db.removeFromGroup("work", 7)
  *        MESSAGE | removeFromGroup() : successfully removed from group
  * $59 ==> true
  *
  * jshell> db.deleteGroup("family")
  *        MESSAGE | deleteGroup() : successfully deleted group
  * $60 ==> true
  *
  * jshell> db.printTable("usera.groups", 15)
  *
  *     | ID              | NAME            | CONTACTID       |
  *     | --------------- | --------------- | --------------- |
  *     | 4               | WORK            | 6               |
  *     | 6               | WORK            | 8               |
  * }</pre>
  *
  * <p>Groups can be renamed with {@link renameGroup renameGroup()}:</p>
  *
  * <pre>{@code
  * jshell> db.renameGroup("work", "colleagues")
  *        MESSAGE | renameGroup() : successfully renamed group
  * $62 ==> true
  * }</pre>
  *
  * <p>Safeguards are in place to ensure that a particular contact cannot be
  * added to the same group more than once:</p>
  *
  * <pre>{@code
  * jshell> db.addToGroup("colleagues", 6)
  *        WARNING | addToGroup() : user is already associated with group
  * $63 ==> false
  * }</pre>
  *
  **/
public final class Database {

  //----------------------------------------------------------------------------
  //
  //  IMPLEMENTATION NOTES:
  //
  //  In a Derby database, the database owner / creator (aka. DBO) has full
  //  permissions to add and remove users and tables, create user groups, and
  //  set permissions for individual users and user groups. When the database
  //  is encrypted (as it is here) and SQL authorization is enabled (as it is
  //  here), *only* the DBO can shut down, encrypt / re-encrypt, or upgrade the
  //  database. The DBO cannot be changed and the powers mentioned in the last
  //  sentence cannot be delegated to another user.
  //
  //  When a new database is being created, the two properties mentioned above
  //  (encryption and SQL authorization) must be declared. Database encryption
  //  is enabled by including the option `dataEncryption=true` in the jdbc:derby
  //  URL. Before that command is run, SQL authorization must be set to `true`
  //  as a system property:
  //
  //    Properties p = System.getProperties();
  //    p.setProperty("derby.database.sqlAuthorization", "true");
  //
  //  ...this is done below. Without the first option, the database will not be
  //  encrypted. Without the second, the DBO will not have grant and revoke
  //  permissions and also other users will be able to shut down, encrypt /
  //  re-encrypt, or upgrade the database, without the DBO's knowledge. So both
  //  of these flags are set here for security purposes.
  //
  //  For additional security, users are required to log in with valid usernames
  //  and passwords. For these username-password combinations to be valid, they
  //  must be stored in the Derby database itself. Note that this includes the
  //  DBO. If the database is set to require user authentication by enabling the
  //  flag `derby.connection.requireAuthentication`, and the connection is then
  //  closed (without any users being added) then NO ONE will be able to gain
  //  access to the database again, including the DBO. This class always adds
  //  the DBO as a user to any new database created, and gives the DBO full
  //  read-write permissions by adding them to the list of `fullAccessUsers`.
  //
  //  The owner of an object (like a table) has the ability to grant / revoke
  //  permissions for that object. The DBO has the ability to grant / revoke
  //  permissions for any object. By default, objects are created within the
  //  user's schema (the same as their username), and non-DBO users do not have
  //  permission to create new schemas or access objects in other users'
  //  schemas. This provides all the encapsulation we need for this basic app.
  //
  //----------------------------------------------------------------------------
  //
  //  REFERENCES
  //
  //  authentication (usernames + passwords) ............. bit.ly/2A0mn1e
  //  authentication (managing users)  ................... bit.ly/2A4dbsz
  //  create a user ...................................... bit.ly/2A0jaOY
  //  database owner ..................................... bit.ly/2A1Lfpj
  //  database-wide properties ........................... bit.ly/2A2bQCw
  //  delete a table / schema ............................ bit.ly/2A2RWaG
  //  delete a user ...................................... bit.ly/2A19S5i
  //  execute / executeQuery / executeUpdate ............. bit.ly/2A3EfrZ
  //  fine-grained user authorization .................... bit.ly/2A1QpS1
  //  grant permissions .................................. bit.ly/2RZjR2g
  //  granting / revoking examples ....................... bit.ly/2A35oeG
  //  revoke permissions ................................. bit.ly/2S71h8k
  //  roles .............................................. bit.ly/2RZjJQk
  //  sqlAuthorization ................................... bit.ly/2A1Q6GR
  //  system tables ...................................... bit.ly/2zYiKZK
  //
  //  example on StackOverflow ........................... bit.ly/2A1QXHM
  //
  //----------------------------------------------------------------------------

  private final Connection connection;
  private final Statement statement;

  // singleton class, so constructor is private
  private Database (Connection connection, Statement statement) {
    this.connection = connection;
    this.statement = statement;
  }

  // database is accessed via connect() method
  // only returns non-null if everything succeeds
  // from then on, returns the same connection object

  private static Database database = null;
  private static String derbyName = null;
  private static boolean newDB = false;
  private static ResultSet resultSet = null;
  private static ResultSetMetaData rsmd = null;

  // prepared statements (ps_) prevent injection attacks
  //  see: https://docs.oracle.com/javase/9/docs/api/java/sql/PreparedStatement.html
  //  and: http://bobby-tables.com/java

  private static PreparedStatement ps_chpwd = null;   // for changing password
  private static PreparedStatement ps_adduser = null; // for adding a new user

  ///---------------------------------------------------------------------------
  ///
  ///  CONNECT TO / DISCONNECT FROM / CREATE NEW DATABASE; RETURN Database OBJECT
  ///
  ///---------------------------------------------------------------------------

  /**
    * Returns the name of the database, or {@code null} if the database has not
    * yet been initialised.
    *
    * @return the name of the database, or {@code null} if the database has not
    * yet been initialised.
    *
    **/
  public static String name() { return derbyName; }

  /**
    * Closes the connection to the current database, if such a connection
    * exists; resets all variables.
    *
    **/
  public static void disconnect() {

    try {
      // shut down database, always throws an SQLException (http://bit.ly/2AcngnA)
      DriverManager.getConnection("jdbc:derby:" + derbyName + ";shutdown=true");

    } catch (SQLException ex) {
      // do nothing, this is expected
    }

    // reset all variables
    database   = null;
    derbyName  = null;
    newDB      = false;
    resultSet  = null;
    rsmd       = null;
    ps_chpwd   = null;
    ps_adduser = null;
  }

  /**
    * Constructs a properly-formatted {@code jdbc:derby} URL, given the database
    * name and password and the user's username and password.
    *
    * <p>Returns an {@link Optional#empty empty Optional} if any of the arguments
    * are {@code null}, but enforces no other restrictions on them.</p>
    *
    * @param dbName name of the database
    * @param dbPwd boot password for the database
    * @param userName name of the user creating / logging into the database
    * @param userPwd password of the user creating / logging into the database
    *
    * @return a {@link StringBuilder} containing a formatted {@code jdbc:derby}
    * URL, wrapped in an {@link Optional}.
    *
    **/
  private static Optional<StringBuilder> constructURL (
    String dbName, String dbPwd, String userName, String userPwd) {

    // StringBuilder cannot append null Strings
    if (dbName == null || dbPwd == null ||
      userName == null || userPwd == null)
      return Optional.empty();

    // return standard format URL for Derby connection
    StringBuilder sb = new StringBuilder();
    sb.append("jdbc:derby:");    sb.append(dbName);
    sb.append(";bootPassword="); sb.append(dbPwd);
    sb.append(";user=");         sb.append(userName);
    sb.append(";password=");     sb.append(userPwd);

    return Optional.of(sb);
  }

  /**
    * Initialises or creates the database specified by {@code databaseName} and
    * returns a reference to that {@link Database}, wrapped in an {@link Optional}.
    *
    * <p>This class is a singleton class, and databases can only be loaded /
    * created via this method. Once the database connection has been made, a new
    * connection cannot be initialised unless the program is terminated. If a
    * database has already been initialised, this method simply returns a
    * reference to that database, wrapped in an {@link Optional}.</p>
    *
    * <p>If the attempt to connect to the database fails, or the default
    * {@link Statement} and {@link PreparedStatement}s cannot be properly
    * initialised, this method returns an {@link Optional#empty empty Optional}.
    * Initialisation can then be re-attempted by the user by again calling
    * this method.</p>
    *
    * @param databaseName name of the database to connect to / create
    * @param bootPassword boot password for the database, required to connect to it
    * @param userName name of the user connecting to / creating the database
    * @param userPassword password for the user specified by {@code userName}
    *
    * @return the singleton {@link Database} object, wrapped in an
    * {@link Optional}, or an {@link Optional#empty empty Optional} if there was
    * a problem
    *
    **/
  public static Optional<Database> connect (
    String databaseName, String bootPassword, String userName, String userPassword) {

    if (database != null) {
      IOUtils.printWarning("connect()", "database already initialised");
      return Optional.of(database);
    }

    // if connection fails, return empty
    Optional<Connection> optConn = getConnection(
      databaseName, bootPassword, userName, userPassword);
    if (!optConn.isPresent()) return Optional.empty();

    // if statement initialisation fails, return empty
    Optional<Statement> optState = getStatement(optConn.get());
    if (!optState.isPresent()) return Optional.empty();

    Statement state = optState.get();
    Connection conn = optConn.get();

    // if this is a new database, there's some setup left to do:
    if (newDB) { try {

        // must be signed in as DBO to run SYSCS_SET_DATABASE_PROPERTY
        //  -> set requireAuthentication to true to enforce password authentication
        state.executeUpdate(
          "call SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(" +
          "'derby.connection.requireAuthentication', 'true')");

        //----------------------------------------------------------------------
        //
        //  Note on user creation:
        //
        //  Only the DBO can create schemas (bit.ly/2Abear0) which are
        //  different than the current user name, and only already-existing
        //  users can log in to the database. Therefore, only the DBO can create
        //  new users. Users cannot create schemas different than their own
        //  usernames, and cannot create or delete tables outside their own
        //  schemas. Theoretically, users could delete their own tables, but
        //  this would leave their accounts in an unstable state. Users should
        //  not be able to delete other users accounts, and they cannot delete
        //  their own accounts while logged in.
        //
        //  The only logical setup, then, is that only the DBO can add and
        //  delete user accounts, and that users cannot delete their own tables.
        //
        //----------------------------------------------------------------------

        // grant users the ability to reset their own passwords
        state.executeUpdate(
          "grant execute on procedure SYSCS_UTIL.SYSCS_RESET_PASSWORD to public");

        // before we can add the DBO to the database, we need to create the schema
        state.executeUpdate("create schema " + userName);

        // don't use addUser() to add the DBO to the database, because it requires
        // validation of the DBO's password from the SECURE table, which doesn't yet exist

        state.execute("create table " + userName +
          ".SECURE (salt varchar(1024) not null, hash varchar(1024) not null)");

        // generate salt and hash password
        Optional<String> optsalt = PasswordUtils.generateSalt(512);
        if (!optsalt.isPresent()) return Optional.empty();
        String salt = optsalt.get();

        Optional<String> opthash = PasswordUtils.hashPassword(userPassword, salt);
        if (!opthash.isPresent()) return Optional.empty();
        String hash = opthash.get();

        // add salt and hash to database
        state.execute("insert into " + userName  +
          ".SECURE (salt, hash) values ('" + salt + "', '" + hash + "')");

      } catch (SQLException ex) {
        IOUtils.printSQLException("connect()", ex);
        return Optional.empty();
    } }

    database = new Database(conn, state);

    // if this is a new database, make sure we add the database owner to the
    // list of users, and give the DBO full read/write access to the database

    if (newDB) {
      try { // add the DBO to the list of full read/write access users

        ps_adduser.setString(1, userName);
        ps_adduser.setString(2, userPassword);
        ps_adduser.execute();

        state.executeUpdate(
          "call SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(" +
          "'derby.database.fullAccessUsers', '" + userName + "')");

      } catch (SQLException ex) {
        IOUtils.printError("connect()", "error giving database owner full read/write access to database");
        database = null; // reset mis-instantiated database
        return Optional.empty();
    } }

    // if we've gotten this far, the connection is good; return the new db
    derbyName = databaseName;
    IOUtils.printMessage("connect()", "database successfully initialised");
    return Optional.of(database);
  }

  /**
    * Attempts to acquire a connection to the database specified by
    * {@code dbName} with the user account specified by {@code userName}.
    *
    * <p>If any arguments passed to this method are {@code null}, an
    * {@link Optional#empty empty Optional} is returned. If the database
    * specified by {@code dbName} already exists, it is loaded; otherwise, an
    * attempt will be made to create a new database with the specified
    * {@code dbName} and {@code dbPwd}.</p>
    *
    * <p>This method returns an {@link Optional#empty empty Optional} if the
    * specified database already exists, but an invalid {@code userName} or
    * {@code userPwd} was given, or if the database does not exist, but cannot
    * be created.</p>
    *
    * @param dbName name of the database to connect to / create
    * @param dbPwd boot password for the database, required to connect to it
    * @param userName name of the user connecting to / creating the database
    * @param userPwd password for the user specified by {@code userName}
    *
    * @return a {@link Connection} to the specified database, wrapped in an
    * {@link Optional}, or an {@link Optional#empty empty Optional} if there
    * was a problem
    *
    **/
  private static Optional<Connection> getConnection (
    String dbName, String dbPwd, String userName, String userPwd) {

    // get formatted URL
    Optional<StringBuilder> optSB = constructURL(dbName, dbPwd, userName, userPwd);

    // if any parameters were passed as null, constructURL returns empty
    if (!optSB.isPresent()) {
      IOUtils.printError("getConnection()", "illegal argument(s) -- no parameter can be null");
      return Optional.empty();
    } StringBuilder sb = optSB.get();

    try { // try to load database first, to avoid overwriting

      DriverManager.registerDriver(new EmbeddedDriver());
      Optional<Connection> retval = Optional.of(DriverManager.getConnection(sb.toString()));
      return retval;

    // if there's an exception, the database can't be loaded
    } catch (SQLException ex) {

      int    exi = ex.getErrorCode();
      String exs = ex.getSQLState();

      // catch common cases
      if (exi == 40000 && "08004".equals(exs)) {
        IOUtils.printError("getConnection()", "invalid username or password");
        return Optional.empty();
      }

      // otherwise, there was some other issue; try to create the database
      try { // add extra bit to URL for database creation
        sb.append(";create=true;dataEncryption=true"); // encrypted always

        // set system properties before creating database
        Properties p = System.getProperties();
        p.setProperty("derby.database.sqlAuthorization", "true");

        // try to create the database
        Optional<Connection> optConn = Optional.of(
          DriverManager.getConnection(sb.toString(), p) );

        // flip the newDB switch
        newDB = true;

        // return the connection
        return optConn;

      // if there's an exception, the database can't be created
      } catch (SQLException e2) {

        // unusual case? print error codes:
        IOUtils.printSQLException("getConnection()", ex);
        IOUtils.printSQLException("getConnection()", e2);
        return Optional.empty();
  } } }

  /**
    * Initialises all {@link Statement} and {@link PreparedStatement} objects
    * to be used with the current database.
    *
    * <p>Returns an {@link Optional#empty empty Optional} if there was a
    * problem, otherwise, returns the default {@link Statement} object for
    * this database, wrapped in an {@link Optional}.</p>
    *
    * @param connection {@link Connection} used to create the default
    * {@link Statement} and all {@link PreparedStatement}s
    *
    * @return the default {@link Statement} object for this database, wrapped in
    * an {@link Optional}, or an {@link Optional#empty empty Optional} if there
    * was a problem
    *
    **/
  private static Optional<Statement> getStatement (Connection connection) {

    if (connection == null) {
      System.err.println("getStatement() : connection cannot be null");
      return Optional.empty();
    }

    try { //--------------------------------------------------------------------
      //  DEFINE PREPARED STATEMENTS
      //------------------------------------------------------------------------

      // change user passwords
      ps_chpwd = connection.prepareStatement(
        "call SYSCS_UTIL.SYSCS_RESET_PASSWORD(?, ?)");

      // create new users
      ps_adduser = connection.prepareStatement(
        "call SYSCS_UTIL.SYSCS_CREATE_USER(?, ?)");

      // return the statement wrapped in an Optional
      return Optional.of(connection.createStatement());

    } catch (SQLException ex) {
      IOUtils.printSQLException("getStatement()", ex);
      return Optional.empty();
  } }

  ///---------------------------------------------------------------------------
  ///
  ///  ADD, REMOVE, UPDATE CONTACTS IN CONTACTS TABLE
  ///
  ///---------------------------------------------------------------------------

  /**
    * Attempts to add the given {@link Contact} to the list of contacts in the
    * current user's {@code CONTACTS} table.
    *
    * <p>Returns {@code false} if the contact could not be added to the current
    * user's {@code CONTACTS} table for any reason. Returns {@code true} if the
    * contact was successfully added to the current user's {@code CONTACTS}
    * table.</p>
    *
    * @param contact {@link Contact} to add to the current user's
    * {@code CONTACTS} table
    *
    * @return {@code true} if and only if the provided {@link Contact} was
    * successfully added to the current user's {@code CONTACTS} table
    *
    * @see tables tables(), to see available tables
    * @see printTable printTable(), to print a particular table to the terminal
    * @see table table(), to get a particular table as a List
    *
    **/
  public boolean addContact (Contact contact) {

    // run some initial validation
    String opName = "addContact()";
    String USER = contactOpsInit(opName);
    if (USER == null) return false;

    // operation-specific validation
    if (contact == null) {
      IOUtils.printError(opName, "contact cannot be null");
      return false;
    }

    try {
      this.statement.execute("insert into " + USER + ".CONTACTS" + contact);
      IOUtils.printMessage(opName, "successfully added contact");
      return true;

    } catch (SQLException ex) {
      IOUtils.printSQLException(opName, ex);
      return false;
    }
  }

  /**
    * Attempts to update the contact with the given contact {@code ID} in the
    * current user's {@code CONTACTS} table by replacing it with the provided
    * {@link Contact}.
    *
    * <p>Returns {@code false} if the contact with the given {@code ID} could
    * not be updated for any reason. Returns {@code true} if the contact in the
    * user's {@code CONTACTS} table was successfully replaced by the
    * {@link Contact} provided as an argument to this method.</p>
    *
    * @param ID ID index of the contact to update (from the {@code CONTACTS} table)
    * @param contact {@link Contact} which should replace the current contact
    * with the specified {@code ID} in the current user's {@code CONTACTS} table
    *
    * @return {@code true} if and only if the contact with the specified
    * {@code ID} was successfully replaced by the provided {@link Contact}
    *
    * @see tables tables(), to see available tables
    * @see printTable printTable(), to print a particular table to the terminal
    * @see table table(), to get a particular table as a List
    *
    **/
  public boolean updateContact (int ID, Contact contact) {

    // run some initial validation
    String opName = "updateContact()";
    String USER = contactOpsInit(opName);
    if (USER == null) return false;

    // operation-specific validation
    if (contact == null) {
      IOUtils.printError(opName, "contact cannot be null");
      return false;
    }

    try { // to update specified contacts in CONTACTS table

      // return false if no contacts are affected
      String query = "select * from " + USER + ".CONTACTS where id = " + ID;
      if (!contactOpsContactsAffected(opName, query)) return false;

      String updates = contact.info.entrySet().stream().map(e -> {
          String k = e.getKey();
          String v = e.getValue().getValue();
          if (v == null) return (k + " = null");
          else return (k + " = '" + v + "'");
        }).collect(Collectors.joining(", "));

      this.statement.execute("update " + USER + ".CONTACTS set " + updates + " where id = " + ID);
      IOUtils.printMessage(opName, "contact successfully updated");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IOUtils.printSQLException(opName, ex);
      return false;
    }
  }

  /**
    * Attempts to delete the contacts with the given contact {@code ID}s from
    * the current user's {@code CONTACTS} table.
    *
    * <p>Returns {@code false} if no contacts were deleted (whether due to invalid
    * {@code ID} numbers, database connectivity problems, or some other issue).
    * Returns {@code true} if at least one contact was deleted from the current
    * user's {@code CONTACTS} table as a result of this method.</p>
    *
    * @param IDs ID indices of the contacts to delete (from the {@code CONTACTS} table)
    *
    * @return {@code true} if at least one contact was deleted from the current
    * user's {@code CONTACTS} table
    *
    * @see tables tables(), to see available tables
    * @see printTable printTable(), to print a particular table to the terminal
    * @see table table(), to get a particular table as a List
    *
    **/
  public boolean deleteContacts (int... IDs) {

    // run some initial validation
    String opName = "deleteContacts()";
    String USER = contactOpsInit(opName);
    if (USER == null) return false;

    // operation-specific validation
    if (IDs.length < 1) {
      IOUtils.printError(opName, "no contact IDs given");
      return false;
    }

    try { // to delete specified contacts from CONTACTS table

      // return false if no contacts are affected
      boolean any = false;
      for (int ID : IDs) {
        String query = "select * from " + USER + ".CONTACTS where id = " + ID;
        any = (any || contactOpsContactsAffected(opName, query));
      } if (!any) return false;

      for (int ID : IDs)
        this.statement.execute("delete from " + USER + ".CONTACTS where id = " + ID);

      IOUtils.printMessage(opName, "contacts successfully deleted");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IOUtils.printSQLException(opName, ex);
      return false;
    }
  }

  /**
    * Attempts to add the contacts with the given contact {@code ID}s to the
    * specified group in the current user's {@code GROUPS} table.
    *
    * <p>Returns {@code false} if no contacts were added to the specified group
    * (whether due to an invalid {@code groupName}, invalid {@code ID} numbers,
    * database connectivity problems, or some other issue). Returns {@code true}
    * if at least one contact was added to the specified group in the current
    * user's {@code GROUPS} table.</p>
    *
    * @param groupName name of the group with which the specified contacts should
    * be associated
    * @param IDs ID indices of the contacts to add to the specified group
    *
    * @return {@code true} if at least one contact was added to the specified
    * group
    *
    * @see tables tables(), to see available tables
    * @see printTable printTable(), to print a particular table to the terminal
    * @see table table(), to get a particular table as a List
    *
    **/
  public boolean addToGroup (String groupName, int... IDs) {

    // run some initial validation
    String opName = "addToGroup()";
    String USER = contactOpsInit(opName);
    if (USER == null) return false;

    // operation-specific validation
    if (IDs.length < 1) {
      IOUtils.printError(opName, "no contact IDs given");
      return false;
    }

    // validate group names
    if (!contactOpsValidateGroups(opName, groupName)) return false;

    try { // to add specified contacts to this group

      // return false if no contacts are affected
      boolean any = false;
      for (int ID : IDs) {
        String query = "select * from " + USER + ".CONTACTS where id = " + ID;
        any = (any || contactOpsContactsAffected(opName, query));
      }

      if (!any) {
        IOUtils.printWarning(opName, "no users added to group");
        return false;
      }

      // move groupName to all-caps
      String GROUPNAME = groupName.toUpperCase();

      any = false;
      for (int ID : IDs) {

        // first, check if this user is already associated with this group
        resultSet = this.statement.executeQuery("select * from " + USER +
          ".GROUPS where name = '" + GROUPNAME + "' and contactid = " + ID);

        if (resultSet.next()) {
          IOUtils.printWarning(opName, "user is already associated with group");
          continue;
        } any = true;

        // if not, add this user to the group
        this.statement.execute("insert into " + USER + ".GROUPS(name, contactid) values ('" +
          GROUPNAME + "', " + ID + ")");
      }

      if (any) {
        IOUtils.printMessage(opName, "successfully added to group");
        return true;
      } else return false;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IOUtils.printSQLException(opName, ex);
      return false;
    }
  }

  /**
    * Attempts to remove the contacts with the given contact {@code ID}s from
    * the specified group in the current user's {@code GROUPS} table.
    *
    * <p>Returns {@code false} if no contacts were removed from the specified
    * group (whether due to an invalid {@code groupName}, invalid {@code ID}
    * numbers, database connectivity problems, or some other issue). Returns
    * {@code true} if at least one contact was removed from the specified group
    * in the current user's {@code GROUPS} table.</p>
    *
    * @param groupName name of the group from which the specified contacts
    * should be removed
    * @param IDs ID indices of the contacts to remove from the specified group
    *
    * @return {@code true} if at least one contact was removed from the
    * specified group
    *
    * @see tables tables(), to see available tables
    * @see printTable printTable(), to print a particular table to the terminal
    * @see table table(), to get a particular table as a List
    *
    **/
  public boolean removeFromGroup (String groupName, int... IDs) {

    // run some initial validation
    String opName = "removeFromGroup()";
    String USER = contactOpsInit(opName);
    if (USER == null) return false;

    // operation-specific validation
    if (IDs.length < 1) {
      IOUtils.printError(opName, "no contact IDs given");
      return false;
    }

    // validate group names
    if (!contactOpsValidateGroups(opName, groupName)) return false;

    try { // to remove specified contacts from this group

      // check that this group has at least one member
      String GROUPNAME = contactOpsGroupExists(opName, USER, groupName);
      if (GROUPNAME == null) return false;

      // return false if no contacts are affected
      boolean any = false;
      for (int ID : IDs) {
        String query = "select * from " + USER + ".GROUPS where contactid = " +
          ID + " and name = '" + GROUPNAME + "'";
        any = (any || contactOpsContactsAffected(opName, query));
      } if (!any) return false;

      for (int ID : IDs)
        this.statement.execute("delete from " + USER + ".GROUPS where contactid = " +
          ID + " and name = '" + GROUPNAME + "'");

      IOUtils.printMessage(opName, "successfully removed from group");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IOUtils.printSQLException(opName, ex);
      return false;
    }
  }

  /**
    * Attempts to delete a group from the current user's {@code GROUPS} table
    * by removing all contacts from that group.
    *
    * @param groupName name of the group to remove from the current user's
    * {@code GROUPS} table
    *
    * @return {@code true} if and only if the specified group was successfully
    * removed from the current user's {@code GROUPS} table, and no contacts
    * remain associated with that group
    *
    * @see tables tables(), to see available tables
    * @see printTable printTable(), to print a particular table to the terminal
    * @see table table(), to get a particular table as a List
    *
    **/
  public boolean deleteGroup (String groupName) {

    // run some initial validation
    String opName = "deleteGroup()";
    String USER = contactOpsInit(opName);
    if (USER == null) return false;

    // validate group names
    if (!contactOpsValidateGroups(opName, groupName)) return false;

    try { // to remove all contacts from this group

      // check that this group has at least one member
      String GROUPNAME = contactOpsGroupExists(opName, USER, groupName);
      if (GROUPNAME == null) return false;

      // delete relationships between given group and contacts
      this.statement.execute("delete from " + USER + ".GROUPS where name = '" + GROUPNAME + "'");

      IOUtils.printMessage(opName, "successfully deleted group");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IOUtils.printSQLException(opName, ex);
      return false;
    }
  }

  /**
    * Attempts to rename a group in the current user's {@code GROUPS} table.
    *
    * @param oldName current name of the group which should be renamed
    * @param newName new name to give to the group
    *
    * @return {@code true} if and only if the group referenced by
    * {@code oldName} existed, and was successfully renamed to {@code newName}
    *
    * @see tables tables(), to see available tables
    * @see printTable printTable(), to print a particular table to the terminal
    * @see table table(), to get a particular table as a List
    *
    **/
  public boolean renameGroup (String oldName, String newName) {

    // run some initial validation
    String opName = "renameGroup()";
    String USER = contactOpsInit(opName);
    if (USER == null) return false;

    // validate group names
    if (!contactOpsValidateGroups(opName, oldName, newName)) return false;

    // operation-specific validation
    if (oldName.equals(newName)) {
      IOUtils.printWarning(opName, "old name is the same as new name");
      return false;
    }

    try { // to rename this group

      // check that this group has at least one member
      String OLDNAME = contactOpsGroupExists(opName, USER, oldName);
      if (OLDNAME == null) return false;

      // change group name
      String NEWNAME = newName.toUpperCase(); // capitalise
      this.statement.execute("update " + USER + ".GROUPS set name = '" + NEWNAME +
        "' where name = '" + OLDNAME + "'");

      IOUtils.printMessage(opName, "successfully renamed group");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IOUtils.printSQLException(opName, ex);
      return false;
    }
  }

  //----------------------------------------------------------------------------
  //
  //  PRIVATE METHODS FOR CONTACTS-RELATED OPERATIONS
  //
  //----------------------------------------------------------------------------

  // "header" for Contacts-related operations
  private String contactOpsInit (String opName) {

    // if current user is DBO, they can't use this method
    if (userIsDBO()) {
      IOUtils.printError(opName, "only regular (non-DBO) users have lists of contacts");
      return null;
    }

    // if current user cannot be found for any reason, quit
    Optional<String> OPTUSER = user();
    if (!OPTUSER.isPresent()) return null;
    String USER = OPTUSER.get();

    return USER;
  }

  // returns true only if all group names are valid
  private boolean contactOpsValidateGroups (String opName, String... args) {

    for (String arg : args)
      if (isNullOrWhitespace(arg)) {
        IOUtils.printError(opName, "group names cannot be null, empty, or all whitespace");
        return false;
      }

    // only allow alphanumeric characters (and underscores) in group names to
    // prevent SQL injection attacks; use regex to find any non-alnum chars

    Pattern p = Pattern.compile("[^a-zA-Z0-9_]");
    Matcher m;

    for (String arg : args) {
      m = p.matcher(arg);
      if (m.find()) {
        IOUtils.printError(opName, "group names can only contain letters, numbers, and underscores");
        return false;
    } }

    return true;
  }

  private String contactOpsGroupExists (String opName, String USER, String group) throws SQLException {

    // get current groups from GROUPS table
    List<String> GROUPS = new ArrayList<String>();
    resultSet = this.statement.executeQuery("select distinct name from " + USER + ".GROUPS");
    while (resultSet.next()) GROUPS.add(resultSet.getString(1));

    // if no groups or given group doesn't exist, return true
    String GROUP = group.toUpperCase(); // capitalise
    if (GROUPS.size() < 1 || !GROUPS.contains(GROUP)) {
      IOUtils.printWarning(opName, "group doesn't exist; no contacts affected");
      return null;
    } return GROUP;
  }

  private boolean contactOpsContactsAffected (String opName, String query) throws SQLException {

    // get number of rows affected (if 0, return false)
    int rowCount = 0;
    resultSet = this.statement.executeQuery(query);
    while (resultSet.next()) { ++rowCount; }

    if (rowCount < 1) {
      IOUtils.printWarning(opName, "no contacts affected");
      return false;
    } return true;
  }

  private boolean isNullOrWhitespace (String s) {
    return (s == null || "".equals(s.trim()));
  }

  ///---------------------------------------------------------------------------
  ///
  ///  LIST, ADD, DELETE USERS; CHANGE, RESET USER PASSWORDS
  ///
  ///---------------------------------------------------------------------------

  /**
    * Returns an {@link Optional Optional&lt;List&lt;String&gt;&gt;} containing
    * the names of all users in the database.
    *
    * <p>Can only be executed by the database {@link owner}. If run by any other
    * user, an {@link Optional#empty empty Optional} will be returned, and a
    * warning will be printed to the console. All usernames are returned in
    * all-uppercase letters.</p>
    *
    * <p>Returns an empty {@link List} wrapped in an {@link Optional} and prints
    * an {@link SQLException} to the console if there was a problem accessing
    * the {@code database}.</p>
    *
    * @return an {@link Optional Optional&lt;List&lt;String&gt;&gt;} containing
    * the names of all users in the database
    *
    **/
  public Optional<List<String>> users() {

    // if current user is not DBO, they can't use this method
    if(!userIsDBO()) {
      IOUtils.printError("users()", "only database owner can view list of users");
      return Optional.empty();
    }

    // list of users to return
    List<String> USERS = new ArrayList<>();

    try {
      resultSet = this.statement.executeQuery("select username from sys.sysusers");
      while (resultSet.next()) USERS.add(resultSet.getString(1).toUpperCase());

    // catch SQL errors -- return empty list if there was a problem
    } catch (SQLException ex) {
      IOUtils.printSQLException("users()", ex);
      USERS.clear(); // clear half-filled list
    }

    // return user names
    return Optional.of(USERS);
  }

  /**
    * Attempts to add a new user to the database with the given {@code username}
    * and {@code password}.
    *
    * <p>This method can only be run by the database owner (DBO), and, as an
    * added measure of security, it requires the DBO to re-enter their password
    * ({@code dboPassword}).</p>
    *
    * <p>Returns {@code true} if the user was successfully added to the
    * database. If either the {@code username} or {@code password} provided
    * is null, returns {@code false}.</p>
    *
    * <p>Returns {@code false} and prints an {@link SQLException} to the console
    * if there was a problem accessing the {@code database}.</p>
    *
    * @param username new user's username
    * @param password new user's password
    * @param dboPassword password of the database owner
    *
    * @return {@code true} if the new user was successfully added to the
    * database, false otherwise
    *
    **/
  public boolean addUser (String username, String password, String dboPassword) {

    //--------------------------------------------------------------------------
    //  validate username and password
    //--------------------------------------------------------------------------

    if (isNullOrWhitespace(username) || isNullOrWhitespace(password)) {
      IOUtils.printError("addUser()", "neither username nor password can be null, empty, or all whitespace");
      return false;
    }

    // if password has leading or trailing whitespace, throw error (bit.ly/2Sj7BtE)
    if (!password.trim().equals(password)) {
      IOUtils.printError("addUser()", "password cannot have leading or trailing whitespace");
      return false;
    }

    // only allow alphanumeric characters (and underscores) in usernames to
    // prevent SQL injection attacks; use regex to find any non-alnum chars

    Pattern p = Pattern.compile("[^a-zA-Z0-9_]");
    Matcher m = p.matcher(username);

    if (m.find()) {
      IOUtils.printError("addUser()", "usernames can only contain letters, numbers, and underscores");
      return false;
    }

    /// add restrictions on usernames and passwords (> 8 chars, etc?)

    //--------------------------------------------------------------------------
    //  get all prerequisite information; if there are any problems, fail fast
    //--------------------------------------------------------------------------

    Optional<String> OPTOWNER = owner();
    if (!OPTOWNER.isPresent()) return false;
    String OWNER = OPTOWNER.get();

    Optional<List<String>> OPTUSERS = users();
    if (!OPTUSERS.isPresent()) return false;
    List<String> USERS = OPTUSERS.get();

    // tables() returns an empty list if there are no tables
    List<String> TABLES = tables();

    //--------------------------------------------------------------------------
    //  only DBO can add users
    //--------------------------------------------------------------------------

    // if current user is not DBO, they can't use this method
    if(!userIsDBO()) {
      IOUtils.printError("addUser()", "only database owner can add new users");
      return false;
    }

    try { // verify the DBO's password
      // get the salt and hash from the DBO's SECURE table
      resultSet = this.statement.executeQuery("select * from " + OWNER + ".SECURE");
      resultSet.next();

      String salt = resultSet.getString(1);
      String hash = resultSet.getString(2);

      // check if given password can be transformed to hash in database
      boolean isValid = PasswordUtils.verifyPassword(dboPassword, hash, salt);

      // if valid, continue, otherwise return false
      if (!isValid) {
        IOUtils.printError("addUser()", "could not verify database owner's password");
        return false;
      }

    // catch SQL exceptions
    } catch (SQLException ex) {
      IOUtils.printSQLException("addUser()", ex);
      return false;
    }

    //--------------------------------------------------------------------------
    //  try to create a new user, if that user doesn't already exist
    //--------------------------------------------------------------------------

    try { // shift to uppercase
      String USERNAME = username.toUpperCase();

      // check that user doesn't already exist
      if (USERS.contains(USERNAME)) {
        IOUtils.printError("addUser()", "user already exists");
        return false;
      }

      // passwords can contain symbols, etc., so we need a prepared statement
      if (!USERS.contains(USERNAME)) {
        ps_adduser.setString(1, USERNAME);
        ps_adduser.setString(2, password);
        ps_adduser.execute();
      }

      //------------------------------------------------------------------------
      //
      //  CREATE USER'S DEFAULT TABLES AND GRANT FULL PERMISSIONS
      //
      //   - create tables only if they don't already exist
      //   - don't create 'GROUPS' or 'CONTACTS' tables for DBO
      //       (DBO is strictly a utility account for user management)
      //   - since USERNAME is alnum only, no prepared statements needed
      //
      //------------------------------------------------------------------------

      String cTable = USERNAME + ".CONTACTS"; // user's contacts list
      String gTable = USERNAME + ".GROUPS";   // user's contacts groups
      String sTable = USERNAME + ".SECURE";   // user's hashed password and salt

      // create 'CONTACTS' table
      //  auto-increment: https://www.binarytides.com/create-autoincrement-columnfield-in-apache-derby/
      //  phone numbers: https://www.cm.com/blog/how-to-format-international-telephone-numbers/

      if (!OWNER.equals(USERNAME) && !TABLES.contains(cTable)) {

        // get column names and descriptions from Contact class
        Contact c = new Contact();

        // Contact class defines schema for Contacts table
        this.statement.execute("create table " + cTable +
          "(id int not null generated always as identity (start with 1, increment by 1), " +
          (c.info.entrySet().stream().map(e -> e.getKey() + " " + e.getValue().getKey()).collect(Collectors.joining(", "))) +
          ", constraint primary_key_c primary key (id))");
        this.statement.execute("grant all privileges on " + cTable + " to " + username);
      }

      // create 'GROUPS' table
      if (!OWNER.equals(USERNAME) && !TABLES.contains(gTable)) {
        this.statement.execute("create table " + gTable +
          "(id int not null generated always as identity (start with 1, increment by 1), " +
          "name varchar(40), contactid int" +
          ", constraint primary_key_g primary key (id))");
        this.statement.execute("grant all privileges on " + gTable + " to " + username);
      }

      //------------------------------------------------------------------------
      //
      //  CREATE 'SECURE' TABLE
      //
      //  User needs read/write permissions on SECURE table in order to update
      //  their password, but they shouldn't be able to edit this table outside
      //  of that use case. If the user edits the salt or the hash they could
      //  leave their account in a corrupted state.
      //
      //------------------------------------------------------------------------

      if (!TABLES.contains(sTable)) {
        this.statement.execute("create table " + sTable +
          "(salt varchar(1024) not null, hash varchar(1024) not null)");

        // generate salt and hash password
        Optional<String> optsalt = PasswordUtils.generateSalt(512);
        if (!optsalt.isPresent()) return false;
        String salt = optsalt.get();

        Optional<String> opthash = PasswordUtils.hashPassword(password, salt);
        if (!opthash.isPresent()) return false;
        String hash = opthash.get();

        // add salt and hash to database
        this.statement.execute("insert into " + sTable  +
          "(salt, hash) values ('" + salt + "', '" + hash + "')");

        // grant user full permissions on SECURE table
        if (!OWNER.equals(USERNAME)) // DBO already has permissions here
          this.statement.execute("grant all privileges on " + sTable + " to " + username);
      }

      // if we've made it here and no errors have been thrown...
      // ...we've successfully added a new user to the database!

      IOUtils.printMessage("addUser()", "user '" + username + "' successfully added");
      return true;

    // catch SQL errors
    } catch (SQLException ex) {

      int    exi = ex.getErrorCode();
      String exs = ex.getSQLState();

      // catch common cases
      if        (exi == 30000 && "42X01".equals(exs)) {
        IOUtils.printError("addUser()", "username cannot be a reserved SQL word (see: bit.ly/2Abbzxc)");

      } else if (exi == 30000 && "28502".equals(exs)) {
        IOUtils.printError("addUser()", "invalid username \"" + username + "\"");

      // unusual case? print error codes:
      } else IOUtils.printSQLException("addUser()", ex);
      return false;
  } }

  /**
    * Attempts to delete the user with the given {@code username} from the
    * database, along with all of their data.
    *
    * <p>This method can only be run by the database owner (DBO), and, as an
    * added measure of security, it requires the DBO to re-enter their password
    * ({@code dboPassword}).</p>
    *
    * <p>Returns {@code true} if the user was successfully removed from the
    * database. Returns {@code false} if the user doesn't exist, if there was
    * some problem accessing the database, or if the database owner's password
    * ({@code dboPassword}) is incorrect.</p>
    *
    * @param username username of the user to delete
    * @param dboPassword password of the database owner
    *
    * @return {@code true} if the user was successfully removed from the
    * database, false otherwise
    *
    **/
  public boolean deleteUser (String username, String dboPassword) {

    //--------------------------------------------------------------------------
    //  verify that `username` is in the list of users
    //--------------------------------------------------------------------------

    Optional<List<String>> OPTUSERS = users();
    if (!OPTUSERS.isPresent()) return false;
    List<String> USERS = OPTUSERS.get();

    // capitalise username
    String USERNAME = username.toUpperCase();

    if (!USERS.contains(USERNAME)) {
      IOUtils.printError("deleteUser()", "user '" + username + "' doesn't exist");
      return false;
    }

    //--------------------------------------------------------------------------
    //  verify DBO password
    //--------------------------------------------------------------------------

    // if current user is not DBO, they can't use this method
    if(!userIsDBO()) {
      IOUtils.printError("deleteUser()", "only database owner can add new users");
      return false;
    }

    Optional<String> OPTOWNER = user();
    if (!OPTOWNER.isPresent()) return false;
    String OWNER = OPTOWNER.get();

    try { // verify the DBO's password
      // get the salt and hash from the DBO's SECURE table
      resultSet = this.statement.executeQuery("select * from " + OWNER + ".SECURE");
      resultSet.next();

      String salt = resultSet.getString(1);
      String hash = resultSet.getString(2);

      // check if given password can be transformed to hash in database
      boolean isValid = PasswordUtils.verifyPassword(dboPassword, hash, salt);

      // if valid, continue, otherwise return false
      if (!isValid) {
        IOUtils.printError("deleteUser()", "could not verify database owner's password");
        return false;
      }

      //------------------------------------------------------------------------
      //  drop user's tables and schema
      //------------------------------------------------------------------------

      this.statement.execute("drop table "  + USERNAME + ".GROUPS");
      this.statement.execute("drop table "  + USERNAME + ".CONTACTS");
      this.statement.execute("drop table "  + USERNAME + ".SECURE");
      this.statement.execute("drop schema " + USERNAME + " restrict");

      this.statement.executeUpdate( // delete user
        "call SYSCS_UTIL.SYSCS_DROP_USER('" + USERNAME + "')");

      // if we've made it this far without throwing an error, success!
      IOUtils.printMessage("deleteUser()", "user '" + username + "' successfully deleted");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IOUtils.printSQLException("deleteUser()", ex);
      return false;
    }
  }

  /**
    * Changes the current user's password.
    *
    * <p>If the user enters a {@code null}, empty, or all-whitespace password,
    * this method prints an error and returns {@code false}. Also, if
    * {@code newPassword} has any leading or trailing whitespace, an error is
    * printed and {@code false} is returned.</p>
    *
    * <p>If the user enters an incorrect {@code oldPassword}, the password is
    * not changed and {@code false} is returned.</p>
    *
    * @param oldPassword this user's current password
    * @param newPassword new password for this user
    *
    * @return true if this user's password was successfully changed to the
    * {@code newPassword}
    *
    **/
  public boolean changePassword (String oldPassword, String newPassword) {

    // if either argument is null or empty, throw an error
    if (isNullOrWhitespace(oldPassword) || isNullOrWhitespace(newPassword)) {
      IOUtils.printError("changePassword()", "neither argument can be null, empty, or all whitespace");
      return false;
    }

    // if new password has leading or trailing whitespace, throw error (bit.ly/2Sj7BtE)
    if (!newPassword.trim().equals(newPassword)) {
      IOUtils.printError("changePassword()", "password cannot have leading or trailing whitespace");
      return false;
    }

    // if there's a problem getting the current user, fail fast
    Optional<String> OPTUSER = user();
    if (!OPTUSER.isPresent()) return false;
    String USER = OPTUSER.get();

    try {
      // get the salt and hash from this user's SECURE table
      resultSet = this.statement.executeQuery("select * from SECURE");
      resultSet.next();

      String salt = resultSet.getString(1);
      String hash = resultSet.getString(2);

      // check if given password can be transformed to hash in database
      boolean isValid = PasswordUtils.verifyPassword(oldPassword, hash, salt);

      // if so, change current password to given password
      if (isValid) {
        ps_chpwd.setString(1, USER);
        ps_chpwd.setString(2, newPassword);

        // generate salt and hash password
        Optional<String> optsalt = PasswordUtils.generateSalt(512);
        if (!optsalt.isPresent()) return false;
        salt = optsalt.get();

        Optional<String> opthash = PasswordUtils.hashPassword(newPassword, salt);
        if (!opthash.isPresent()) return false;
        hash = opthash.get();

        // update salt and hash in database
        this.statement.execute("update " + USER + ".SECURE set hash = '" +
          hash + "', salt = '" + salt + "'");

        // don't update password until hash and salt are updated
        ps_chpwd.execute();

        // inform the user that the password has been successfully changed
        IOUtils.printMessage("changePassword()", "password successfully changed");
        return true;

      } else {
        IOUtils.printMessage("changePassword()", "invalid password; password not changed");
        return false;
      }

    // catch SQL errors
    } catch (SQLException ex) {
      IOUtils.printSQLException("changePassword()", ex);
      return false;
  } }

  /**
    * Sets the password of the user with the given {@code username} to
    * {@code newPassword}, provided that the current user is the DBO.
    *
    * <p>An error will be printed and {@code false} will be returned from this
    * method if any of the following are true:</p>
    * <ul>
    * <li>if any argument of this method is {@code null}, empty, or all whitespace</li>
    * <li>if the {@code newPassword} has any leading or trailing whitespace</li>
    * <li>if there's a problem acquiring the username of the DBO or the current user</li>
    * <li>if {@code username} references a user that doesn't exist</li>
    * <li>if the current user is not the database owner</li>
    * <li>if the database owner's password ({@code dboPassword}) was entered incorrectly</li>
    * <li>if there was a problem hashing the new password</li>
    * <li>if there was any problem communicating with the database</li>
    * </ul>
    *
    * <p>...otherwise, the specified user's password will be changed to
    * {@code newPassword} and {@code true} will be returned.</p>
    *
    * @param username user whose password should be reset
    * @param newPassword user's password will be set to this new password
    * @param dboPassword password of the database owner
    *
    * @return {@code true} if and only if the specified user's password was
    * successfully changed to the {@code newPassword}
    *
    **/
  public boolean resetPassword (String username, String newPassword, String dboPassword) {

    // if any argument is null or empty, throw an error
    if (isNullOrWhitespace(username) || isNullOrWhitespace(newPassword)) {
      IOUtils.printError("resetPassword()", "no argument can be null, empty, or all whitespace");
      return false;
    }

    // if new password has leading or trailing whitespace, throw error (bit.ly/2Sj7BtE)
    if (!newPassword.trim().equals(newPassword)) {
      IOUtils.printError("resetPassword()", "password cannot have leading or trailing whitespace");
      return false;
    }

    //--------------------------------------------------------------------------
    //  verify that `username` is in the list of users
    //--------------------------------------------------------------------------

    Optional<List<String>> OPTUSERS = users();
    if (!OPTUSERS.isPresent()) return false;
    List<String> USERS = OPTUSERS.get();

    // capitalise username
    String USERNAME = username.toUpperCase();

    if (!USERS.contains(USERNAME)) {
      IOUtils.printError("resetPassword()", "user '" + username + "' doesn't exist");
      return false;
    }

    //--------------------------------------------------------------------------
    //  verify DBO password
    //--------------------------------------------------------------------------

    // if current user is not DBO, they can't use this method
    if(!userIsDBO()) {
      IOUtils.printError("resetPassword()", "only database owner can reset user passwords");
      return false;
    }

    Optional<String> OPTOWNER = user();
    if (!OPTOWNER.isPresent()) return false;
    String OWNER = OPTOWNER.get();

    try { // verify the DBO's password
      // get the salt and hash from the DBO's SECURE table
      resultSet = this.statement.executeQuery("select * from " + OWNER + ".SECURE");
      resultSet.next();

      String salt = resultSet.getString(1);
      String hash = resultSet.getString(2);

      // check if given password can be transformed to hash in database
      boolean isValid = PasswordUtils.verifyPassword(dboPassword, hash, salt);

      // if valid, continue, otherwise return false
      if (!isValid) {
        IOUtils.printError("resetPassword()", "could not verify database owner's password");
        return false;
      }

      ps_chpwd.setString(1, USERNAME);
      ps_chpwd.setString(2, newPassword);

      // generate salt and hash password
      Optional<String> optsalt = PasswordUtils.generateSalt(512);
      if (!optsalt.isPresent()) return false;
      salt = optsalt.get();

      Optional<String> opthash = PasswordUtils.hashPassword(newPassword, salt);
      if (!opthash.isPresent()) return false;
      hash = opthash.get();

      // update salt and hash in database
      this.statement.execute("update " + USERNAME + ".SECURE set hash = '" +
        hash + "', salt = '" + salt + "'");

      // don't update password until hash and salt are updated
      ps_chpwd.execute();

      // inform the user that the password has been successfully changed
      IOUtils.printMessage("resetPassword()", "password successfully changed");
      return true;

    // catch SQL errors
    } catch (SQLException ex) {
      IOUtils.printSQLException("resetPassword()", ex);
      return false;
    }
  }

  ///---------------------------------------------------------------------------
  ///
  ///  GET LIST OF TABLES, PRINT A TABLE, RETURN A TABLE AS List<List<String>>
  ///
  ///---------------------------------------------------------------------------

  /**
    * Returns a {@link List} containing the names of all user-created tables in
    * the {@code database} accessible by the current user.
    *
    * <p>Returns an empty {@link List} and prints an {@link SQLException} to the
    * console if there was a problem accessing the {@code database}. If there
    * are no user-created tables, this method returns an empty {@link List}.
    * The database owner can access all tables in the database, but other users
    * can only access tables in their schema.</p>
    *
    * @return a {@link List} containing the names of all user-created tables in
    * the {@code database} accessible by the current user
    *
    **/
  public List<String> tables() {

    // list of tables to return
    List<String> TABLES = new ArrayList<>();

    // if there's a problem getting the current user, fail fast
    Optional<String> OPTUSER = user();
    if (!OPTUSER.isPresent()) return TABLES;
    String USER = OPTUSER.get();

    // is the current user the DBO?
    boolean isDBO = userIsDBO();

    try { // get table names, types, and schemas, and select only user-created tables
      resultSet = this.statement.executeQuery("select sys.systables.tablename, " +
        "sys.systables.tabletype, sys.sysschemas.schemaname from sys.systables " +
        "inner join sys.sysschemas on sys.systables.schemaid = sys.sysschemas.schemaid " +
        "where sys.systables.tabletype = 'T'"); // 'T' signifies user-created tables

      while (resultSet.next()) { // loop over user-created tables
        String TABLE  = resultSet.getString(1).toUpperCase();
        String SCHEMA = resultSet.getString(3).toUpperCase();

        // only return this user's non-SECURE tables; or, if DBO, all tables in database
        if (isDBO || (SCHEMA.equals(USER) && !TABLE.equals("SECURE")))
          TABLES.add(String.format("%s.%s", SCHEMA, TABLE));
      }

    // catch SQL errors -- return empty list if there was a problem
    } catch (SQLException ex) {
      IOUtils.printSQLException("tables()", ex);
      TABLES.clear(); // clear half-filled list
    }

    // return table names
    return TABLES;
  }

  /**
    * Prints a table to the standard output device, provided the current user
    * has permission to view that table.
    *
    * <p>Regular users can print a table using its fully-qualified name (like
    * {@code printTable("Bob.contacts")}) or its shortened table name (like
    * {@code printTable("contacts")}), but the database owner must always use
    * fully-qualified names.</p>
    *
    * @param tableName name of table to print
    * @param columnWidth printed width (in characters) of each column
    *
    * @see tables tables() to view tables available to the current user
    * @see table table() to get a given table as a {@code List<List<String>>}
    *
    **/
  public void printTable (String tableName, int columnWidth) {

    // first, get table; if null, quit
    List<List<String>> table = table(tableName);
    if (table == null) return;

    // if table has no rows, quit
    int nRows = table.size();
    if (nRows < 1) return;
    int nCols = table.get(0).size();

    // insert an empty row in the table to separate headers from data
    String empty = String.join("", Collections.nCopies(columnWidth, "-"));
    List<String> br = new ArrayList<>(Collections.nCopies(nCols, empty));
    table.add(1, br);

    // print table by looping over rows
    System.out.println();
    for (List<String> row : table)
      System.out.println(row.stream()
        .map(e -> {
          if (e == null) e = "";
          String temp = String.format("%-" + columnWidth + "." + columnWidth + "s", e);
          if (e.length() > columnWidth) {
            temp = temp.substring(0, columnWidth-3);
            temp = temp + "...";
          } return temp;
        }).collect(Collectors.joining(" | ", "    | ", " |")));
  }

  /**
    * Returns the specified table as a {@code List<List<String>>}, provided the
    * current user has permission to view that table.
    *
    * <p>If the table with name {@code tableName} exists, this method will
    * return that table as a {@link List} of {@code List<String>}, where the
    * inner lists are the rows of the table and the {@link String}s they hold
    * are the data in each column of the table. Rows are ordered top-to-bottom
    * and columns are ordered left-to-right, both with 0-indexing.</p>
    *
    * <p>If the table has zero rows, its column headers will still be returned,
    * and if a table doesn't exist (or can't be accessed by the current user),
    * an empty {@link List} will be returned.</p>
    *
    * @param tableName name of the table of interest
    *
    * @return the specified table as a {@link List} of {@code List<String>}
    * rows, if the table exists and the user has permission to view it
    *
    **/
  public List<List<String>> table (String tableName) {

    // return value
    List<List<String>> retval = new ArrayList<>();

    // if tableName is null, empty, or all whitespace, throw error
    if (tableName == null || "".equals(tableName.trim())) {
      IOUtils.printError("table()", "tableName cannot be null, empty, or all whitespace");
      return null;
    }

    // move table name to all-uppercase
    String TABLE = tableName.toUpperCase();

    // we can't use a prepared statement for table names, so instead, just
    // check if the table is in the list of available tables, and if not,
    // print an error and return

    if (!tables().contains(TABLE)) {
      IOUtils.printError("table()", "table '" + tableName + "' cannot be found");
      return retval;
    }

    try {
      resultSet = this.statement.executeQuery("select * from " + TABLE);
      rsmd = resultSet.getMetaData();
      int numberOfColumns = rsmd.getColumnCount();
      int rowCount = 0;

      // add column label row to table
      retval.add(new ArrayList<String>());

      // get current row
      List<String> row = retval.get(rowCount);

      // add column names to 0th row of table
      for (int cc = 1; cc <= numberOfColumns; ++cc)
        row.add(rsmd.getColumnName(cc));

      while (resultSet.next()) {

        // increment the row count
        ++rowCount;

        // add a new row to retval
        retval.add(new ArrayList<String>());

        // get current row
        row = retval.get(rowCount);

        // loop over columns and add to this row
        for (int ii = 1; ii <= numberOfColumns; ++ii)
          row.add(resultSet.getString(ii));
      }

      return retval;

    // catch SQL errors
    } catch (SQLException ex) {
      IOUtils.printSQLException("table()", ex);
      retval.clear(); // clear the half-initialised list
      return retval;
  } }



  ///---------------------------------------------------------------------------
  ///
  ///  GET USER, OWNER; FIND OUT IF CURRENT USER IS DATABASE OWNER
  ///
  ///---------------------------------------------------------------------------

  /**
    * Returns the name of the current user, in all-uppercase letters, wrapped in
    * an {@link Optional}.
    *
    * <p>Returns an empty {@link Optional} and prints an {@link SQLException} to
    * the console if there was a problem accessing the {@code database}.</p>
    *
    * @return the name of the current user, in all-uppercase letters, wrapped in
    * an {@link Optional}
    *
    **/
  public Optional<String> user() {
    try {
      resultSet = statement.executeQuery("values current_user");
      resultSet.next();
      return Optional.of(resultSet.getString(1).toUpperCase());

    // catch SQL errors -- return empty if there was a problem
    } catch (SQLException ex) {
      IOUtils.printSQLException("user()", ex);
      return Optional.empty();
  } }

  /**
    * Returns the username of the database owner (DBO), in all-uppercase
    * letters, wrapped in an {@link Optional}.
    *
    * <p>Returns an empty {@link Optional} and prints an {@link SQLException} to
    * the console if there was a problem accessing the {@code database}.</p>
    *
    * @return the username of the database owner (DBO), in all-uppercase
    * letters, wrapped in an {@link Optional}.
    *
    **/
  public Optional<String> owner() {

    try { // DBO cannot be changed; creator of system tables is therefore DBO
      resultSet = this.statement.executeQuery(
        "select authorizationid from sys.sysschemas where schemaname='SYS'");

      resultSet.next();
      return Optional.of(resultSet.getString(1).toUpperCase());

    // catch SQL errors -- return empty if there was a problem
    } catch (SQLException ex) {
      IOUtils.printSQLException("owner()", ex);
      return Optional.empty();
    }
  }

  /**
    * Returns {@code true} if and only if the current user is the database owner.
    *
    * <p>Returns {@code false} if there was a problem getting the name of the
    * database owner or the current user, or if the current user is not the
    * database owner.</p>
    *
    * @return {@code true} if and only if the current user is the database owner
    *
    **/
  public boolean userIsDBO() {

    Optional<String> OPTOWNER = owner();
    Optional<String> OPTUSER  =  user();

    if (!OPTOWNER.isPresent() || !OPTUSER.isPresent()) {
      IOUtils.printError("userIsDBO()", "problem acquiring current user or database owner");
      return false;

    } else return OPTOWNER.get().equals(OPTUSER.get());
  }

}
