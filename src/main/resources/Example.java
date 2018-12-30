
  //----------------------------------------------------------------------------
  //
  //  DATABASE / USER MANAGEMENT
  //
  //  create a new database, check database status, print a table
  //
  //----------------------------------------------------------------------------

  // define database name
  String dbn = "db86"

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
  db.printTable("usera.contacts", 15)

  //----------------------------------------------------------------------------
  //
  //  USER TABLE / DATA MANAGEMENT
  //
  //  adding, updating, and removing contacts to/from the CONTACTS table
  //
  //----------------------------------------------------------------------------











  // create a new contact
  Contact c0 = new Contact()

  // add any combination of firstname, surname, phone:
  c0.set("phone", "+353830360019").set("surname", "watson")

  // add contact to contacts list
  db.addContact(c0)

  // edit c0 and add more contacts to database
  c.set("phone", null).set("firstname", "michael"); db.addContact(c)
  c.set("phone", "+16109991234").set("firstname", "jessica"); db.addContact(c)
  c.set("phone", "+15850001212").set("firstname", "dave"); db.addContact(c)
  c.set("phone", null).set("firstname", "bob").set("surname", "jones"); db.addContact(c)
  c.set("firstname", "steve").set("surname", "jenkins"); db.addContact(c)
  c.set("surname", "smith"); db.addContact(c)

  // print table to see new contact
  db.printTable("usera.contacts", 15)

  // update the n-th row by passing a new Contact object -- every field is overwritten
  c.set("phone", "+16108440000").set("firstname", "andrew").set("surname", "watson")
  db.updateContact(1, c0)
  c0.set("phone", "+44567992847").set("surname", "jenkins")
  db.updateContact(6, c0)
  db.printTable("usera.contacts", 15)

  // if that contact id doesn't exist, an error is printed
  db.updateContact(-1, c0)
  db.updateContact(77, c0)

  // delete one or more contacts with deleteContacts()
  db.deleteContacts(2, 7)

  // id numbering continues with new contacts added
  c0.set("phone", "+44578390838").set("firstname", "mark").set("surname", "twain")
  db.addContact(c0)
  db.printTable("usera.contacts", 15)

  // names and phone numbers are validated to ensure they only contain particular characters
  c0.set("phone", "wrong").set("firstname", "; drop tables")

  //----------------------------------------------------------------------------
  //
  //  USER TABLE / DATA MANAGEMENT
  //
  //  adding, updating, and removing groups to/from the GROUPS table
  //
  //----------------------------------------------------------------------------

  // create new group with (case-insensitive) name and contact id numbers
  db.addToGroup("family", 1, 3, 4)

  // passing no int arguments prints an error
  db.addToGroup("work")
  db.addToGroup("work", 6, 7, 8)

  // print the GROUPS table to get an overview of groups and their members
  db.printTable("usera.groups", 15)

  // remove members from group by contact id number(s)
  db.removeFromGroup("work", 7)
  db.printTable("usera.groups", 15)

  // delete a group with deleteGroup (removes all members)
  db.deleteGroup("family")
  db.printTable("usera.groups", 15)

  // rename a group
  db.renameGroup("work", "colleagues")  
  db.printTable("usera.groups", 15)

  // users cannot be added twice to the same group
  db.addToGroup("colleagues", 6)



// USER MANAGEMENT: DONE

// DATA MANAGEMENT:
//  - add README.md
//  - clean up userOps code?

// USER INTERFACE:
//  - all of it
//  - ability to search and sort (asc/desc on fields) contacts
//    - do this within the user interface:
//      - get the table with table("USERNAME.CONTACTS")
//      - sort / search that List<List<String>>




// Considerations:
//
//   When set up to do so, Derby can require a valid username and password to allow
//   a user to sign in:
//
//     User authorization / SQLauthorization
//       [http://bit.ly/2GISMit]
//       Passwords hashed using SHA-256 [http://bit.ly/2GKPhIj]
//
//     Database encryption:
//       DES [http://bit.ly/2GWs46n] [http://bit.ly/2GIlOi9]
//
//     Hashed password and salt stored in user database to validate user password
//     when performing sensitive operations (changing password, etc.). Salt generated
//     using SecureRandom, a cryptographically-secure RNG.
//
//       Derby has methods to reset and modify the user password, but not simply to
//       verify it. Requiring the user to re-enter their password before changing it
//       ensures that someone else can't perform an over-the-shoulder attack and change
//       the user's password while they're logged in, but away from their machine.
//
//   Everything accomplished in the UI should be accomplishable on the command line,
//   as well. Command line functionality should be a superset of UI functionality.
//
//   Database.java code is a bit repetitive and could use some cleaning up
//
//   Contacts are set up to only have char or varchar fields (see set()) at the moment
//     would have to do some slight retooling of Contact.java to extend this
//
//   Phone numbers can only contain digits and the '+' character. If it appears in the
//   number, '+' must be the first character and can only appear once. Formatting should
//   not be (and is not) part of the information in the database.
//
//   Only ASCII alphanumeric characters and underscores accepted in usernames, contact
//     info fields, etc., to reduce likelihood of SQL injection attacks. Could expand
//     this character set a bit with more work.
//
//   Limited restrictions on user passwords:
//     cannot be null, empty, or all-whitespace
//     cannot start or end with any whitespace characters
//     ...could implement additional checks in addUser(), changePassword(), resetPassword()
//
//   Thread safety not considered
//
//   Logging
//     should use Log4j, SLF4J, or Apache Commons Logging instead of printing to screen
//
//   Testing
//     no tests written, but should use JUnit for unit testing







