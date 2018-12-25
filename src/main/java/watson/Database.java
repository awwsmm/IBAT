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
      IO.printWarning("connect()", "database already initialised");
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
        IO.printSQLException("connect()", ex);
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
        IO.printError("connect()", "error giving database owner full read/write access to database");
        database = null; // reset mis-instantiated database
        return Optional.empty();
    } }

    // if we've gotten this far, the connection is good; return the new db
    derbyName = databaseName;
    IO.printMessage("connect()", "database successfully initialised");
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
      IO.printError("getConnection()", "illegal argument(s) -- no parameter can be null");
      return Optional.empty();
    } StringBuilder sb = optSB.get();

    try { // try to load database first, to avoid overwriting
      Optional<Connection> retval = Optional.of(DriverManager.getConnection(sb.toString()));
      return retval;

    // if there's an exception, the database can't be loaded
    } catch (SQLException ex) {

      int    exi = ex.getErrorCode();
      String exs = ex.getSQLState();

      // catch common cases
      if (exi == 40000 && "08004".equals(exs)) {
        IO.printError("getConnection()", "invalid username or password");
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
        IO.printSQLException("getConnection()", ex);
        IO.printSQLException("getConnection()", e2);
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

/*
      // add contact to group
      ps_addToGroup = connection.prepareStatement(
        "insert into 

this.statement.execute("insert into " + USER + ".GROUPS(name, contactid) values ('" +
          GROUPNAME
*/

      // return the statement wrapped in an Optional
      return Optional.of(connection.createStatement());

    } catch (SQLException ex) {
      IO.printSQLException("getStatement()", ex);
      return Optional.empty();
  } }

  ///---------------------------------------------------------------------------
  ///
  ///  ADD, REMOVE, UPDATE CONTACTS IN CONTACTS TABLE
  ///
  ///---------------------------------------------------------------------------









  // adds the given Contact to the current user's CONTACTS table
  public boolean addContact (Contact contact) {

    // if current user is DBO, they can't use this method
    if (userIsDBO()) {
      IO.printError("addContact()", "only regular (non-DBO) users have lists of contacts");
      return false;
    }

    // if contact is null, throw error
    if (contact == null) {
      IO.printError("addContact()", "contact cannot be null");
      return false;
    }

    Optional<String> OPTUSER = user();
    if (!OPTUSER.isPresent()) return false;
    String USER = OPTUSER.get();

    // insert Contact into CONTACTS table
    try {
      this.statement.execute("insert into " + USER + ".CONTACTS" + contact);
      IO.printMessage("addContact()", "contact successfully added");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IO.printSQLException("addContact()", ex);
      return false;
    }
  }






  // updates the contact with the given ID number
  public boolean updateContact (int ID, Contact contact) {

    // if current user is DBO, they can't use this method
    if (userIsDBO()) {
      IO.printError("updateContact()", "only regular (non-DBO) users have lists of contacts");
      return false;
    }

    // if contact is null, throw error
    if (contact == null) {
      IO.printError("updateContact()", "contact cannot be null");
      return false;
    }

    Optional<String> OPTUSER = user();
    if (!OPTUSER.isPresent()) return false;
    String USER = OPTUSER.get();

    try { // update Contact in CONTACTS table

      // get number of rows affected (if 0, return false)
      resultSet = this.statement.executeQuery("select * from " + USER + ".CONTACTS where id = " + ID);

      int rowCount = 0;
      while (resultSet.next()) { ++rowCount; }

      if (rowCount < 1) {
        IO.printWarning("updateContact()", "no contacts affected");
        return false;
      }

      String updates = contact.info.entrySet().stream().map(e -> {
          String k = e.getKey();
          String v = e.getValue().getValue();
          if (v == null) return (k + " = null");
          else return (k + " = '" + v + "'");
        }).collect(Collectors.joining(", "));

      this.statement.execute("update " + USER + ".CONTACTS set " + updates + " where id = " + ID);
      IO.printMessage("updateContact()", "contact successfully updated");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IO.printSQLException("updateContact()", ex);
      return false;
    }
  }







  // deletes the contacts with the given ID numbers
  public boolean deleteContacts (int... IDs) {

    // if current user is DBO, they can't use this method
    if (userIsDBO()) {
      IO.printError("deleteContacts()", "only regular (non-DBO) users have lists of contacts");
      return false;
    }

    Optional<String> OPTUSER = user();
    if (!OPTUSER.isPresent()) return false;
    String USER = OPTUSER.get();

    if (IDs.length < 1) {
      IO.printError("deleteContacts()", "no contact IDs given");
      return false;
    }

    try { // delete Contacts from CONTACTS table

      // get number of rows affected (if 0, return false)

      int rowCount = 0;
      for (int ID : IDs) {
        resultSet = this.statement.executeQuery("select * from " + USER + ".CONTACTS where id = " + ID);
        while (resultSet.next()) { ++rowCount; }
      }

      if (rowCount < 1) {
        IO.printWarning("deleteContacts()", "no contacts affected");
        return false;
      }

      for (int ID : IDs)
        this.statement.execute("delete from " + USER + ".CONTACTS where id = " + ID);

      IO.printMessage("deleteContacts()", "contacts successfully deleted");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IO.printSQLException("deleteContacts()", ex);
      return false;
    }
  }





  // add one or more users to one group
  public boolean addToGroup (String groupName, int... IDs) {

    // if current user is DBO, they can't use this method
    if (userIsDBO()) {
      IO.printError("addToGroup()", "only regular (non-DBO) users have lists of groups");
      return false;
    }

    Optional<String> OPTUSER = user();
    if (!OPTUSER.isPresent()) return false;
    String USER = OPTUSER.get();

    if (IDs.length < 1) {
      IO.printError("addToGroup()", "no contact IDs given");
      return false;
    }

    // if groupName is null, empty, or all whitespace, throw error
    if (groupName == null || "".equals(groupName.trim())) {
      IO.printError("addToGroup()", "group name cannot be null, empty, or all whitespace");
      return false;
    }

    // only allow alphanumeric characters (and underscores) in group names to
    // prevent SQL injection attacks; use regex to find any non-alnum chars

    Pattern p = Pattern.compile("[^a-zA-Z0-9_]");
    Matcher m = p.matcher(groupName);

    if (m.find()) {
      IO.printError("addToGroup()", "group names can only contain ASCII alphanumeric characters and underscores");
      return false;
    }

    try { // add Contacts to group

      // get number of rows affected (if 0, return false)

      int rowCount = 0;
      for (int ID : IDs) {
        resultSet = this.statement.executeQuery("select * from " + USER + ".CONTACTS where id = " + ID);
        while (resultSet.next()) { ++rowCount; }
      }

      if (rowCount < 1) {
        IO.printWarning("addToGroup()", "no contacts affected");
        return false;
      }

      // move groupName to all-caps
      String GROUPNAME = groupName.toUpperCase();

      for (int ID : IDs)
        this.statement.execute("insert into " + USER + ".GROUPS(name, contactid) values ('" +
          GROUPNAME + "', " + ID + ")");

      IO.printMessage("addToGroup()", "successfully added to group");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IO.printSQLException("addToGroup()", ex);
      return false;
    }
  }



  // remove one or more users from one group
  public boolean removeFromGroup (String groupName, int... IDs) {

    // if current user is DBO, they can't use this method
    if (userIsDBO()) {
      IO.printError("removeFromGroup()", "only regular (non-DBO) users have lists of groups");
      return false;
    }

    Optional<String> OPTUSER = user();
    if (!OPTUSER.isPresent()) return false;
    String USER = OPTUSER.get();

    if (IDs.length < 1) {
      IO.printError("removeFromGroup()", "no contact IDs given");
      return false;
    }

    // if groupName is null, empty, or all whitespace, throw error
    if (groupName == null || "".equals(groupName.trim())) {
      IO.printError("removeFromGroup()", "group name cannot be null, empty, or all whitespace");
      return false;
    }

    try { // remove Contacts from group

      // get current groups from GROUPS table
      List<String> GROUPS = new ArrayList<String>();
      resultSet = this.statement.executeQuery("select distinct name from " + USER + ".GROUPS");
      while (resultSet.next()) GROUPS.add(resultSet.getString(1));

      // if no groups or given group doesn't exist, return false
      String GROUPNAME = groupName.toUpperCase(); // capitalise
      if (GROUPS.size() < 1 || !GROUPS.contains(GROUPNAME)) {
        IO.printWarning("removeFromGroup()", "group doesn't exist; no contacts affected");
        return false;
      }

      // get number of rows affected (if 0, return false)
      int rowCount = 0;
      for (int ID : IDs) {
        resultSet = this.statement.executeQuery("select * from " + USER + ".GROUPS " +
          "where contactid = " + ID + " and name = '" + GROUPNAME + "'");
        while (resultSet.next()) { ++rowCount; }
      }

      if (rowCount < 1) {
        IO.printWarning("removeFromGroup()", "no contacts affected");
        return false;
      }

      for (int ID : IDs)
        this.statement.execute("delete from " + USER + ".GROUPS where contactid = " +
          ID + " and name = '" + GROUPNAME + "'");

      IO.printMessage("removeFromGroup()", "successfully removed from group");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IO.printSQLException("removeFromGroup()", ex);
      return false;
    }
  }



  // deletes a group (removes all members from that group)
  public boolean deleteGroup (String groupName) {

    // if current user is DBO, they can't use this method
    if (userIsDBO()) {
      IO.printError("deleteGroup()", "only regular (non-DBO) users have lists of groups");
      return false;
    }

    Optional<String> OPTUSER = user();
    if (!OPTUSER.isPresent()) return false;
    String USER = OPTUSER.get();

    // if groupName is null, empty, or all whitespace, throw error
    if (groupName == null || "".equals(groupName.trim())) {
      IO.printError("deleteGroup()", "group name cannot be null, empty, or all whitespace");
      return false;
    }

/// FIX THIS

    try { // remove Contacts from group

      // get current groups from GROUPS table
      List<String> GROUPS = new ArrayList<String>();
      resultSet = this.statement.executeQuery("select distinct name from " + USER + ".GROUPS");
      while (resultSet.next()) GROUPS.add(resultSet.getString(1));

      // if no groups or given group doesn't exist, return true
      String GROUPNAME = groupName.toUpperCase(); // capitalise
      if (GROUPS.size() < 1 || !GROUPS.contains(GROUPNAME)) {
        IO.printWarning("deleteGroup()", "group doesn't exist; no contacts affected");
        return true;
      }

      // get number of rows affected (if 0, return false)
      int rowCount = 0;
      for (int ID : IDs) {
        resultSet = this.statement.executeQuery("select * from " + USER + ".GROUPS " +
          "where name = '" + GROUPNAME + "'");
        while (resultSet.next()) { ++rowCount; }
      }

      if (rowCount < 1) {
        IO.printWarning("deleteGroup()", "no contacts affected");
        return false;
      }

      for (int ID : IDs)
        this.statement.execute("delete from " + USER + ".GROUPS where contactid = " +
          ID + " and name = '" + GROUPNAME + "'");

      IO.printMessage("deleteGroup()", "successfully removed from group");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IO.printSQLException("deleteGroup()", ex);
      return false;
    }
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
      IO.printError("users()", "only database owner can view list of users");
      return Optional.empty();
    }

    // list of users to return
    List<String> USERS = new ArrayList<>();

    try {
      resultSet = this.statement.executeQuery("select username from sys.sysusers");
      while (resultSet.next()) USERS.add(resultSet.getString(1).toUpperCase());

    // catch SQL errors -- return empty list if there was a problem
    } catch (SQLException ex) {
      IO.printSQLException("users()", ex);
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

    if (username == null || password == null ||
          "".equals(username.trim()) || "".equals(password.trim())) {
      IO.printError("addUser()", "neither username nor password can be null, empty, or all whitespace");
      return false;
    }

    // if password has leading or trailing whitespace, throw error (bit.ly/2Sj7BtE)
    if (!password.trim().equals(password)) {
      IO.printError("addUser()", "password cannot have leading or trailing whitespace");
      return false;
    }

    // only allow alphanumeric characters (and underscores) in usernames to
    // prevent SQL injection attacks; use regex to find any non-alnum chars

    Pattern p = Pattern.compile("[^a-zA-Z0-9_]");
    Matcher m = p.matcher(username);

    if (m.find()) {
      IO.printError("addUser()", "usernames can only contain ASCII alphanumeric characters and underscores");
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
      IO.printError("addUser()", "only database owner can add new users");
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
        IO.printError("addUser()", "could not verify database owner's password");
        return false;
      }

    // catch SQL exceptions
    } catch (SQLException ex) {
      IO.printSQLException("addUser()", ex);
      return false;
    }

    //--------------------------------------------------------------------------
    //  try to create a new user, if that user doesn't already exist
    //--------------------------------------------------------------------------

    try { // shift to uppercase
      String USERNAME = username.toUpperCase();

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

      IO.printMessage("addUser()", "user '" + username + "' successfully added");
      return true;

    // catch SQL errors
    } catch (SQLException ex) {

      int    exi = ex.getErrorCode();
      String exs = ex.getSQLState();

      // catch common cases
      if        (exi == 30000 && "42X01".equals(exs)) {
        IO.printError("addUser()", "username cannot be a reserved SQL word (see: bit.ly/2Abbzxc)");

      } else if (exi == 30000 && "28502".equals(exs)) {
        IO.printError("addUser()", "invalid username \"" + username + "\"");

      // unusual case? print error codes:
      } else IO.printSQLException("addUser()", ex);
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
      IO.printError("deleteUser()", "user '" + username + "' doesn't exist");
      return false;
    }

    //--------------------------------------------------------------------------
    //  verify DBO password
    //--------------------------------------------------------------------------

    // if current user is not DBO, they can't use this method
    if(!userIsDBO()) {
      IO.printError("deleteUser()", "only database owner can add new users");
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
        IO.printError("deleteUser()", "could not verify database owner's password");
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
      IO.printMessage("deleteUser()", "user '" + username + "' successfully deleted");
      return true;

    // catch SQL exceptions
    } catch (SQLException ex) {
      IO.printSQLException("deleteUser()", ex);
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
    if (oldPassword == null || newPassword == null ||
          "".equals(oldPassword.trim()) || "".equals(newPassword.trim())) {
      IO.printError("changePassword()", "neither argument can be null, empty, or all whitespace");
      return false;
    }

    // if new password has leading or trailing whitespace, throw error (bit.ly/2Sj7BtE)
    if (!newPassword.trim().equals(newPassword)) {
      IO.printError("changePassword()", "password cannot have leading or trailing whitespace");
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
        IO.printMessage("changePassword()", "password successfully changed");
        return true;

      } else {
        IO.printMessage("changePassword()", "invalid password; password not changed");
        return false;
      }

    // catch SQL errors
    } catch (SQLException ex) {
      IO.printSQLException("changePassword()", ex);
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
    if (username == null || newPassword == null || dboPassword == null ||
          "".equals(username.trim()) || "".equals(newPassword.trim()) || "".equals(dboPassword.trim())) {
      IO.printError("resetPassword()", "no argument can be null, empty, or all whitespace");
      return false;
    }

    // if new password has leading or trailing whitespace, throw error (bit.ly/2Sj7BtE)
    if (!newPassword.trim().equals(newPassword)) {
      IO.printError("resetPassword()", "password cannot have leading or trailing whitespace");
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
      IO.printError("resetPassword()", "user '" + username + "' doesn't exist");
      return false;
    }

    //--------------------------------------------------------------------------
    //  verify DBO password
    //--------------------------------------------------------------------------

    // if current user is not DBO, they can't use this method
    if(!userIsDBO()) {
      IO.printError("resetPassword()", "only database owner can reset user passwords");
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
        IO.printError("resetPassword()", "could not verify database owner's password");
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
      IO.printMessage("resetPassword()", "password successfully changed");
      return true;

    // catch SQL errors
    } catch (SQLException ex) {
      IO.printSQLException("resetPassword()", ex);
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
      IO.printSQLException("tables()", ex);
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
      IO.printError("table()", "tableName cannot be null, empty, or all whitespace");
      return null;
    }

    // move table name to all-uppercase
    String TABLE = tableName.toUpperCase();

    // we can't use a prepared statement for table names, so instead, just
    // check if the table is in the list of available tables, and if not,
    // print an error and return

    if (!tables().contains(TABLE)) {
      IO.printError("printTable()", "table '" + tableName + "' cannot be found");
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
      IO.printSQLException("printTable()", ex);
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
      IO.printSQLException("user()", ex);
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
      IO.printSQLException("owner()", ex);
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
      IO.printError("userIsDBO()", "problem acquiring current user or database owner");
      return false;

    } else return OPTOWNER.get().equals(OPTUSER.get());
  }

}
