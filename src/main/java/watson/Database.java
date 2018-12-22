package watson;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  // database is accessed via initialise() method
  // only returns non-null if everything succeeds
  // from then on, returns the same connection object

  public static Database database = null;
  private static boolean newDB = false;
  private static ResultSet resultSet = null;
  private static ResultSetMetaData rsmd = null;

  // prepared statement (to use when changing password) prevents injection attacks
  //  see: https://docs.oracle.com/javase/9/docs/api/java/sql/PreparedStatement.html
  //  and: http://bobby-tables.com/java

  private static PreparedStatement ps_chpwd;
  private static PreparedStatement ps_adduser;



  public static Optional<Database> initialise (
    String databaseName, String bootPassword, String userName, String userPassword) {

    if (database != null) {
      System.err.println("initialise() : database already initialised");
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
        //  -> set requireAuthentication to true
        state.executeUpdate(
          "call SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(" +
          "'derby.connection.requireAuthentication', 'true')");

        //----------------------------------------------------------------------
        //
        //  Note on user creation:
        //
        //  Only the DBO can create schemas (bit.ly/2Abear0) which are
        //  different than the current user name, and only already-existing
        //  users can log in to the database. So only the DBO can create new
        //  users. Users cannot create schemas different than their own
        //  usernames, and cannot create or delete tables outside their own
        //  schemas; they can only delete their own accounts and create or
        //  delete tables within their own schemas.
        //
        //----------------------------------------------------------------------

        // grant users the ability to reset their own passwords
        state.executeUpdate(
          "grant execute on procedure SYSCS_UTIL.SYSCS_RESET_PASSWORD to public");

      } catch (SQLException ex) {
        printSQLException(ex);
        return Optional.empty();
    } }

    database = new Database(conn, state);

    // if this is a new database, make sure we add the database owner to the
    // list of users, and give the DBO full read/write access to the database

    if (newDB) {

      if (!database.addUser(userName, userPassword)) {
        System.err.println("initialise() : error adding database owner to database");
        database = null; // reset mis-instantiated database
        return Optional.empty();
      }

      try {
        state.executeUpdate(
          "call SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(" +
          "'derby.database.fullAccessUsers', '" + userName + "')");


      } catch (SQLException ex) {
        printSQLException(ex);
        database = null; // reset mis-instantiated database
        return Optional.empty();
    } }

    // if we've gotten this far, the connection is good; return the new db
    System.out.println("initialise() : database successfully initialised");

    return Optional.of(database);
  }






  // returns the connection, if successful, otherwise Optional.empty();
  private static Optional<Connection> getConnection (
    String dbName, String dbPwd, String userName, String userPwd) {

    // get formatted URL
    Optional<StringBuilder> optSB = constructURL(dbName, dbPwd, userName, userPwd);

    // if any parameters were passed as null, constructURL returns empty
    if (!optSB.isPresent()) {
      System.err.println("getConnection() : illegal argument(s) -- no parameter can be null");
      return Optional.empty();
    } StringBuilder sb = optSB.get();

    try { // try to load database first, to avoid overwriting
      Optional<Connection> retval = Optional.of(DriverManager.getConnection(sb.toString()));

      return retval;

    // if there's an exception, the database can't be found / loaded
    } catch (SQLException ex) {

      // catch common cases
      if (ex.getErrorCode() == 40000 && "08004".equals(ex.getSQLState())) {
        System.err.println("getConnection() : invalid username or password.");
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

        // catch common cases

        // <define common cases here>

        // unusual case? print error codes:
        printSQLException(ex);
        printSQLException(e2);
        return Optional.empty();
  } } }




  // initialise the "statement" object, if it's not already initialised
  // also initialise prepared statements here

  private static Optional<Statement> getStatement (Connection connection) {

    if (connection == null) {
      System.err.println("getStatement() : connection cannot be null");
      return Optional.empty();
    }

    try {

      //------------------------------------------------------------------------
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
      printSQLException(ex);
      return Optional.empty();
  } }






/// deleteAccount() // set password to null
// have user enter password to confirm account deletion



  // first, re-verify current password
  // create another table called SECURE that stores the user's salt and hashed password
  // when the user enters the password here, check it against that table

  public boolean changePassword (String oldPassword, String newPassword) {

    // if either argument is null or empty, throw an error
    if (oldPassword == null || newPassword == null || "".equals(oldPassword) || "".equals(newPassword)) {
      System.err.println("changePassword() : neither argument can be null or empty.");
      return false;
    }

    // if new password has leading or trailing whitespace, throw error (bit.ly/2Sj7BtE)
    if (!newPassword.trim().equals(newPassword)) {
      System.err.println("changePassword() : password cannot have leading or trailing whitespace.");
      return false;
    }

    try { // get the current user's username
      String USER  =  user().orElseThrow(() -> new IllegalStateException(
        "changePassword() : error getting current user"));

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
        ps_chpwd.execute();

        // update salt and hash in 'SECURE' table
        salt = PasswordUtils.generateSalt(512).get();
        hash = PasswordUtils.hashPassword(newPassword, salt).get();

        // update salt and hash in database
        this.statement.execute("update " + USER + ".SECURE set hash = '" +
          hash + "', salt = '" + salt + "'");

        // inform the user that the password has been successfully changed
        System.out.println("changePassword() : password successfully changed");
        return true;

      } else {
        System.err.println("changePassword() : invalid password; password not changed");
        return false;
      }

    } catch (SQLException ex) {

      // catch common cases

      // <define common cases here>

      // unusual case? print error codes:
      printSQLException(ex);
      return false;
  } }



//all SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.user.<my user name>', '<your new password>')




  // table can be table name only (in current schema)
  // or table name and schema (as schema.table) when running as DBO

  public void printTable (String table) {

    // print error if `table` is null or empty
    if (table == null || "".equals(table.trim())) {
      System.err.println("printTable() : table name cannot be null or empty.");
      return;
    }

    // get current user and database owner
    String OWNER = owner().orElseThrow(() -> new IllegalStateException(
      "printTable() : error getting database owner"));

    String USER  =  user().orElseThrow(() -> new IllegalStateException(
      "printTable() : error getting current user"));

    // move table name to all-uppercase
    String TABLE = table.toUpperCase();

    // don't let any non-DBO user access "SECURE" tables
//    if (!OWNER.equals(USER) && ("SECURE".equals(TABLE) ||
//      String.format("%s.SECURE", USER).equals(TABLE))) {
//      System.err.println("printTable() : non-database-owner user cannot access 'SECURE' tables.");
//      return;
//    }

    // we can't use a prepared statement for table names, so instead, just
    // check if the table is in the list of available tables, and if not,
    // throw an error and return

    if (!tables().contains(TABLE)) {
      System.err.println("printTable() : table '" + table + "' does not exist.");
      return;
    }

    try {
      resultSet = this.statement.executeQuery("select * from " + TABLE);
      rsmd = resultSet.getMetaData();
      int numberOfColumns = rsmd.getColumnCount();

      while(resultSet.next()) {
        for (int ii = 1; ii <= numberOfColumns; ++ii) {
          if (ii > 1) System.out.print(" | ");
          System.out.print(resultSet.getString(ii));
        } System.out.println();
      }

    } catch (SQLException ex) {

      // catch common cases
      if (ex.getErrorCode() == 30000 && "42502".equals(ex.getSQLState()))
        System.err.println("printTable() : user does not have permission to view this table.");

      // unusual case? print error codes:
      printSQLException(ex);
  } }






  /**
    * Attempts to add the given user to the database, along with a "contacts"
    * table and a "groups" table for that user.
    *
    * <p>Returns {@code true} if the user was successfully added to the
    * database. If either the {@code username} or {@code password} provided
    * is null, returns {@code false}.</p>
    *
    * <p>Returns {@code false} and prints an {@link SQLException} to the console
    * if there was a problem accessing the {@code database}.</p>
    *
    * @param username username of the new user
    * @param password password of the new user
    *
    * @return {@code true} if the new user was successfully added to the
    * database, false otherwise
    *
    **/
  public boolean addUser (String username, String password) {

    //--------------------------------------------------------------------------
    //  validate username and password
    //--------------------------------------------------------------------------

    if (username == null || password == null || "".equals(username) || "".equals(password)) {
      System.err.println("addUser() : neither username nor password can be null or empty");
      return false;
    }

    // if password has leading or trailing whitespace, throw error (bit.ly/2Sj7BtE)
    if (!password.trim().equals(password)) {
      System.err.println("addUser() : password cannot have leading or trailing whitespace.");
      return false;
    }

    // only allow alphanumeric characters (and underscores) in usernames to
    // prevent SQL injection attacks; use regex to find any non-alnum chars

    Pattern p = Pattern.compile("[^a-zA-Z0-9_]");
    Matcher m = p.matcher(username);

    if (m.find()) {
      System.err.println("addUser() : usernames can only contain ASCII alphanumeric characters and underscores");
      return false;
    }

    //--------------------------------------------------------------------------
    //  only DBO can add users
    //--------------------------------------------------------------------------

    String OWNER = owner().orElseThrow(() -> new IllegalStateException(
      "addUser() : error getting database owner"));

    String USER  =  user().orElseThrow(() -> new IllegalStateException(
      "addUser() : error getting current user"));

    // if current user is not DBO, they can't use this method
    if(!OWNER.equals(USER)) {
      System.err.println("addUser() : only database owner can add new users.");
      return false;
    }

    // only DBO can view list of users
    List<String> users = users().orElseThrow(() -> new IllegalStateException(
      "addUser() : error getting list of users"));

    //--------------------------------------------------------------------------
    //  try to create a new user, if that user doesn't already exist
    //--------------------------------------------------------------------------

    try { // shift to uppercase
      String USERNAME = username.toUpperCase();

      // passwords can contain symbols, etc., so we need a prepared statement
      if (!users.contains(USERNAME)) {
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

      List<String> TABLES = tables();

      // create 'CONTACTS' table
      if (!OWNER.equals(USERNAME) && !TABLES.contains(cTable)) {
        this.statement.execute("create table " + cTable + "(test int)");
        this.statement.execute("insert into " + cTable  + "(test) values " + 4);
        this.statement.execute("grant all privileges on " + cTable + " to " + username);
      }

      // create 'GROUPS' table
      if (!OWNER.equals(USERNAME) && !TABLES.contains(gTable)) {
        this.statement.execute("create table " + gTable + "(test int)");
        this.statement.execute("insert into " + gTable  + "(test) values " + 5);
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
        String salt = PasswordUtils.generateSalt(512).get();
        String hash = PasswordUtils.hashPassword(password, salt).get();

        // add salt and hash to database
        this.statement.execute("insert into " + sTable  +
          "(salt, hash) values ('" + salt + "', '" + hash + "')");

        // grant user full permissions on SECURE table
        if (!OWNER.equals(USERNAME)) // DBO already has permissions here
          this.statement.execute("grant all privileges on " + sTable + " to " + username);
      }

      // if we've made it here and no errors have been thrown...
      // ...we've successfully added a new user to the database!

      return true;

    // catch SQL errors
    } catch (SQLException ex) {

      int    exi = ex.getErrorCode();
      String exs = ex.getSQLState();

      // catch common cases
      if        (exi == 30000 && "42X01".equals(exs)) {
        System.err.println("addUser() : username cannot be a reserved SQL word (see: bit.ly/2Abbzxc).");

      } else if (exi == 30000 && "28502".equals(exs)) {
        System.err.printf("addUser() : invalid username \"%s\"%n", username);

      // unusual case? print error codes:
      } else printSQLException(ex);
      return false;

    // print message we defined above
    } catch (IllegalStateException ex) {
      System.err.println(ex.getMessage());
      return false;
    }
  }

  /**
    * Returns the username of the database owner (DBO), wrapped in an
    * {@link Optional}.
    *
    * <p>Returns an empty {@link Optional} and prints an {@link SQLException} to
    * the console if there was a problem accessing the {@code database}.</p>
    *
    * @return the username of the database owner (DBO), wrapped in an
    * {@link Optional}.
    *
    **/
  public Optional<String> owner() {

    try { // DBO cannot be changed; creator of system tables is therefore DBO
      resultSet = this.statement.executeQuery(
        "select authorizationid from sys.sysschemas where schemaname='SYS'");

      resultSet.next();
      return Optional.of(resultSet.getString(1).toUpperCase());

    // catch SQL errors
    } catch (SQLException ex) {
      printSQLException(ex);
      return Optional.empty();
    }
  }

  /**
    * Returns the name of the current user, wrapped in an {@link Optional}.
    *
    * <p>Returns an empty {@link Optional} and prints an {@link SQLException} to
    * the console if there was a problem accessing the {@code database}.</p>
    *
    * @return the name of the current user, wrapped in an {@link Optional}.
    *
    **/
  public Optional<String> user() {
    try {
      resultSet = statement.executeQuery("values current_user");
      resultSet.next();
      return Optional.of(resultSet.getString(1).toUpperCase());

    // catch SQL errors
    } catch (SQLException ex) {
      printSQLException(ex);
      return Optional.empty();
  } }

  /**
    * Constructs a properly-formatted {@code jdbc:derby} URL, given the database
    * name and password and the user's username and password.
    *
    * <p>Returns an empty {@link Optional} if any of the arguments are
    * {@code null}, but enforces no other restrictions on them.</p>
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
  protected static Optional<StringBuilder> constructURL (
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

    String OWNER = owner().orElseThrow(() -> new IllegalStateException(
      "users() : error getting database owner"));

    String USER  =  user().orElseThrow(() -> new IllegalStateException(
      "users() : error getting current user"));

    // if current user is not DBO, they can't use this method
    if(!OWNER.equals(USER)) {
      System.err.println("users() : only database owner can view list of users.");
      return Optional.empty();
    }

    List<String> USERS = new ArrayList<>();

    try {
      resultSet = this.statement.executeQuery("select username from sys.sysusers");
      while (resultSet.next()) USERS.add(resultSet.getString(1).toUpperCase());

    // catch SQL errors
    } catch (SQLException ex) {
      printSQLException(ex);
      USERS.clear(); // clear half-filled list

    // print message we defined above
    } catch (IllegalStateException ex) {
      System.err.println(ex.getMessage());
    }

    // return user names
    return Optional.of(USERS);
  }

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
    List<String> TABLES = new ArrayList<>();

    try { // can use toUpperCase() here because usernames are case-insensitive

      String OWNER = owner().orElseThrow(() -> new IllegalStateException(
        "tables() : error getting database owner"));

      String USER  =  user().orElseThrow(() -> new IllegalStateException(
        "tables() : error getting current user"));

      // get table names, types, and schemas, and select only user-created tables
      resultSet = this.statement.executeQuery("select sys.systables.tablename, " +
        "sys.systables.tabletype, sys.sysschemas.schemaname from sys.systables " +
        "inner join sys.sysschemas on sys.systables.schemaid = sys.sysschemas.schemaid " +
        "where sys.systables.tabletype = 'T'"); // 'T' signifies user-created tables

      while (resultSet.next()) { // loop over user-created tables
        String TABLE = resultSet.getString(1).toUpperCase();
        String SCHEMA = resultSet.getString(3).toUpperCase();

        // only return this user's tables; or, if DBO, all tables
        if (OWNER.equals(USER) || SCHEMA.equals(USER)) {

          // only show non-DBO users non-SECURE tables
          if (!TABLE.equals("SECURE") || OWNER.equals(USER))
            TABLES.add(String.format("%s.%s", SCHEMA, TABLE));
        }
      }

    // catch SQL errors
    } catch (SQLException ex) {
      printSQLException(ex);
      TABLES.clear(); // clear half-filled list

    // print message we defined above
    } catch (IllegalStateException ex) {
      System.err.println(ex.getMessage());
    }

    // return table names
    return TABLES;
  }

  /// from: bit.ly/2zJV23d
  private static void printSQLException(SQLException e) {
    while (e != null) {
      System.err.println("\n----- SQLException -----");
      System.err.println("  SQL State:  " + e.getSQLState());
      System.err.println("  Error Code: " + e.getErrorCode());
      System.err.println("  Message:    " + e.getMessage());
      e = e.getNextException();
    } System.err.println();
  }

}
