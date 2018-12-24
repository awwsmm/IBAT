
  //----------------------------------------------------------------------------
  //
  //  DATABASE / USER MANAGEMENT
  //
  //  create a new database, check database status, print a table
  //
  //----------------------------------------------------------------------------

  // define database name
  String dbn = "db46"

  // load the package
  import watson.*

  // disconnect from old database, if old connection exists
  Database.disconnect()

  // initialise the database
  Optional<Database> optdb = Database.connect(dbn, "bootpass", "owner", "ownerpass")

  // for ease of use
  Database db = optdb.get()

  // is this user the database owner (DBO)?
  db.userIsDBO()

  // get the DBO's username and the current user's username to check
  db.owner(); db.user()

  // get lists of all database users and tables
  db.users(); db.tables()

  // print a table to the terminal; second argument is column width
  db.printTable("owner.secure", 20)

  //----------------------------------------------------------------------------
  //
  //  change current user's password; add / delete users, reset user passwords
  //
  //----------------------------------------------------------------------------

  // change the current user's password (can be called by any user)
  db.changePassword("ownerpass", "newpass")

  // create some users (callable by DBO only)
  db.addUser("usera", "userapass", "newpass") // DBO's password required as
  db.addUser("userb", "userbpass", "newpass") // third argument for a bit of
  db.addUser("userc", "usercpass", "newpass") // extra security

  // check status of database again; note that DBO has no CONTACTS or GROUPS
  db.users(); db.tables()

  // delete a user (callable by DBO only)
  db.deleteUser("userb", "newpass")

  // check status of database again
  db.users(); db.tables()

  // reset a user's password (callable by DBO only)
  db.resetPassword("usera", "password1", "newpass")

  //----------------------------------------------------------------------------
  //
  //  RESTRICTIONS ON NON-DBO USERS
  //
  //   - cannot view list of users
  //   - cannot see other users' tables
  //   - cannot see own 'SECURE' table
  //   - cannot create or delete users (including themselves)
  //   - cannot reset any user's password (including own; but can *change* own)
  //
  //----------------------------------------------------------------------------

  // close the connection to the database
  db.disconnect()

  // invalid passwords throw an error
  Optional<Database> optdb = Database.connect(dbn, "bootpass", "usera", "password2")

  // reconnect as a non-DBO user, usernames are case-insensitive
  Optional<Database> optdb = Database.connect(dbn, "bootpass", "uSeRa", "password1")

  // for ease of use
  Database db = optdb.get()

  // is this user the database owner (DBO)?
  db.userIsDBO()

  // get the DBO's username and the current user's username to check
  db.owner(); db.user()

  // non-DBO users can't view the list of users, for privacy / security
  db.users()

  // non-DBO users only see their own, non-SECURE tables
  db.tables()

  // non-DBO users cannot create new users (even if they have the DBO's password)
  db.addUser("userd", "userdpass", "newpass")

  // non-DBO users cannot delete users
  db.deleteUser("userb", "newpass")

  // non-DBO users cannot reset passwords (doesn't require user's password)
  db.resetPassword("usera", "blep", "newpass")

  // any user can change their own password, though
  db.changePassword("password1", "blep")

  // non-DBO users cannot see, modify, or delete other users' tables
  db.printTable("userc.groups", 15)

  // non-DBO users cannot see or directly modify their own 'SECURE' table
  db.printTable("usera.secure", 15)

  // any non-DBO user can see or modify their own 'CONTACTS' and 'GROUPS' tables
  db.printTable("usera.groups", 15)

  //----------------------------------------------------------------------------
  //
  //  USER TABLE / DATA MANAGEMENT
  //
  //----------------------------------------------------------------------------










// USER MANAGEMENT: DONE

// DATA MANAGEMENT:
//  - ability to add, update, remove contacts from contacts table
//  - ability to add, update, remove groups from groups table
//  - ability to associate any contact with any number of groups

















