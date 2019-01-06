//------------------------------------------------------------------------------
//
//  Run this script in the jshell to create a small, sample MyContacts database.
//
//    jshell> /open resource/Example.java
//
//------------------------------------------------------------------------------

// create database
import watson.*
Optional<Database> optdb = Database.connect("example", "bootpass", "owner", "ownerpass")
Database db = optdb.get()

// add some users
db.addUser("jeff", "jeffpass", "ownerpass")
db.addUser("susan", "susanpass", "ownerpass")

// sign out as the DBO, and back in as "jeff"
Database.disconnect()
optdb = Database.connect("example", "bootpass", "jeff", "jeffpass")
Database db = optdb.get()

// add contacts to "jeff"
Contact c = new Contact()
db.addContact(c.set("firstname", "mark").set("surname", "jones").set("phone", "+44567829344"))
db.addContact(c.set("firstname", "julia").set("phone", "+494230572507"))
db.addContact(c.set("firstname", "chet").set("phone", "+323985729852"))
db.addContact(c.set("firstname", "harry").set("surname", "stevens").set("phone", "+24938574045"))
db.addContact(c.set("firstname", "phil").set("surname", "watson").set("phone", "+214325467"))
db.addContact(c.set("firstname", "susan").set("surname", "mumson").set("phone", "+424632475324"))

// move contacts to groups
db.addToGroup("family", 1, 2, 3)
db.addToGroup("lads", 1, 3, 4, 5)
db.addToGroup("work", 3, 4, 5, 6)

// sign out as "jeff" and back in as "susan"
Database.disconnect()
optdb = Database.connect("example", "bootpass", "susan", "susanpass")
Database db = optdb.get()

// add contacts to "susan"
db.addContact(c.set("firstname", "raj").set("surname", "mumson").set("phone", "+5467867546"))
db.addContact(c.set("firstname", "quentin").set("surname", "O'Brien").set("phone", "+257468635754"))
db.addContact(c.set("firstname", "george").set("phone", "+34768745375"))
db.addContact(c.set("firstname", "tom").set("surname", "wills").set("phone", "+67567575676"))
db.addContact(c.set("firstname", "edward").set("surname", "unger").set("phone", "+463654747675"))

// move contacts to groups
db.addToGroup("family", 1)
db.addToGroup("clients", 3, 5)
