package watson;

import java.util.AbstractMap.SimpleEntry;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
  * Contact class for associated {@code CONTACTS} table in database.
  *
  * <p>A {@link Contact} object contains information about a given contact.
  * Contacts must be constructed using the {@link Contact}s class, and can be
  * added to the database with
  * {@link Database#addContact Database.addContact()}.</p>
  *
  * <p>A {@link Contact} object can be created with:</p>
  *
  * <pre>{@code
  * jshell> import watson.*
  *
  * jshell> Contact c = new Contact()
  * c ==> 
  * }</pre>
  *
  * <p>{@link Contact}s only allow particular, predefined fields. The user can
  * see what fields are available by calling the {@link fields fields()}
  * method, which returns a {@link Set}:</p>
  *
  * <pre>{@code
  * jshell> c.fields()
  * $3 ==> [SURNAME=varchar(40), FIRSTNAME=varchar(40), PHONE=varchar(16)]
  * }</pre>
  *
  * <p>In the {@link Set} above, each element is a {@link SimpleEntry SimpleEntry}
  * containing two {@link String}s (or, a "key" and a "value"). The key of each
  * element is the SQL identifier for that particular piece of contact
  * information. The value of each element is the SQL description of that field.
  * As this is a {@link Set}, these key-value pairs may not always be returned
  * in the same order. To set a particular field, use the
  * {@link set set()} function:</p>
  *
  * <pre>{@code
  * jshell> c.set("SURNAME", "O'Neill")
  * $4 ==> (SURNAME) values ('O''Neill')
  * }</pre>
  *
  * <p>Field assignment can be chained, and multiple fields can be set at
  * once (the {@link Contact} object itself is the return value):</p>
  *
  * <pre>{@code
  * jshell> c.set("firstname", "Colin").set("Phone", "+353445671234")
  * $5 ==> (FIRSTNAME, SURNAME, PHONE) values ('Colin', 'O''Neill', '+353445671234')
  * }</pre>
  *
  * <p>Note that the SQL identifiers ({@code "Phone"}, {@code "firstname"},
  * etc.) are case-insensitive, and that the apostrophe in "O'Neill" has been
  * escaped (by doubling). Field validation and sanitisation is performed in the
  * {@link set set()} method, which is why fields must be hardcoded and cannot
  * be added by the user. Attempting to set an invalid value to a particular
  * field results in an error (and the unchanged {@link Contact} object is
  * returned):</p>
  *
  * <pre>{@code
  * jshell> c.set("phone", "this is not a phone number")
  *          ERROR | set() : phone numbers can only contain digits and '+' signs
  * $6 ==> (FIRSTNAME, SURNAME, PHONE) values ('Colin', 'O''Neill', '+353445671234')
  * }</pre>
  *
  * <p>...as does attempting to set a value to a nonexistent field:</p>
  *
  * <pre>{@code
  * jshell> c.set("email", "oneillc@fast.net")
  *          ERROR | keyExists() : Contact doesn't contain key 'email'
  * $7 ==> (FIRSTNAME, SURNAME, PHONE) values ('Colin', 'O''Neill', '+353445671234')
  * }</pre>
  *
  * <p>Fields can be overwritten by simply calling {@link set set()} again with
  * a valid value, and can be removed by setting their values to {@code null},
  * an empty {@link String}, or an all-whitespace {@link String}:</p>
  *
  * <pre>{@code
  * jshell> c.set("PhOnE", " ").set("firstNAME", "T'Challa")
  * $8 ==> (FIRSTNAME, SURNAME) values ('T''Challa', 'O''Neill')
  * }</pre>
  *
  **/
public final class Contact {

  //----------------------------------------------------------------------------
  //
  //  "info" defines all of the contact information:
  //
  //    first entry of LinkedHashMap is String
  //      this is the value of that SQL id for this particular contact
  //      for example: "O'Neill"
  //      this field is case-sensitive
  //
  //    second entry is Map.Entry<String,String>
  //      this is the <[SQL id], [SQL descriptor]>, for example:
  //                  <"surname", "varchar(40)">
  //       both of these are case-insensitive
  //
  //  info is a LinkedHashMap so iteration order is consistent
  //
  //----------------------------------------------------------------------------

  /** Information associated with this {@link Contact} object. **/
  // "double brace" initialisation: http://bit.ly/2Agb7xX
  protected LinkedHashMap<String, Entry<String,String>> info =
        new LinkedHashMap<String, Entry<String,String>>(){{

    put("FIRSTNAME", new SimpleEntry<String, String>("varchar(40)", null));
    put("SURNAME",   new SimpleEntry<String, String>("varchar(40)", null));
    put("PHONE",     new SimpleEntry<String, String>("varchar(16)", null));

  }};

  /**
    * Returns a {@link Set} containing the name and SQL description of each
    * piece of information which can be added to this {@link Contact} object.
    *
    * <p>The first element of each {@link Entry} is the SQL identifier for that
    * particular piece of information (i.e. {@code "PHONE"}) and the second
    * element is the SQL description (i.e. {@code "varchar(16)"}).</p>
    *
    * @return a {@link Set} containing the name and SQL description of each
    * piece of information which can be added to this {@link Contact} object.
    *
    **/
  public Set<Entry<String,String>> fields() {
    return info.entrySet().stream().map(e ->
      new SimpleEntry<String,String>(e.getKey(), e.getValue().getKey()))
      .collect(Collectors.toSet());
  }

  /**
    * Returns {@code true} if the specified {@code key} is valid (if a
    * {@link Contact} object allows a value associated with that {@code key}).
    *
    * <p>Returns false if the {@code key} is {@code null}, empty, or
    * all-whitespace, or if the {@link Contact} doesn't contain the specified
    * {@code key}. Otherwise, returns {@code true}.</p>
    *
    * @param key key to check for existence within this {@link Contact}'s {@code info}
    *
    * @return {@code true} if the specified {@code key} is valid (if a
    * {@link Contact} object allows a value associated with that {@code key})
    *
    **/
  public boolean keyExists (String key) {

    // filter out null, empty, and all-whitespace keys
    if (key == null || "".equals(key.trim())) {
      IOUtils.printError("keyExists()", "null, empty, or all-whitespace keys not allowed");
      return false;
    }

    // filter out keys that don't exist above
    String KEY = key.toUpperCase();
    if (!info.containsKey(KEY)) {
      IOUtils.printError("keyExists()", "Contact doesn't contain key '" + key + "'");
      return false;
    }

    // otherwise, key exists
    return true;
  }

  /**
    * Returns the value associated with the {@code key} in this {@link Contact}'s
    * {@code info}, if the {@code key} exists.
    *
    * <p>If the {@code key} doesn't exist, an {@link Optional#empty empty Optional}
    * is returned. Note that {@code key}s are case-insensitive.</p>
    *
    * @param key name of the value to get from this {@link Contact}'s {@code info}
    *
    * @return the value associated with the {@code key} in this {@link Contact}'s
    * {@code info}, if the {@code key} exists
    *
    **/
  public Optional<String> get (String key) {
    if (!keyExists(key)) return Optional.empty();
    String KEY = key.toUpperCase();
    return Optional.of(info.get(KEY).getValue());
  }

  /**
    * If the given {@code key} exists in this {@link Contact}'s {@code info},
    * sets it to the given {@code value}.
    *
    * <p>If the {@code key} does not exist, this {@link Contact} object is
    * returned as-is and this method throws no error. If the {@code value} is
    * {@code null}, an empty {@link String}, or an all-whitespace {@code String},
    * the value associated with the {@code key} in this {@link Contact}'s
    * {@code info} will be set to {@code null}.</p>
    *
    * @param key variable to set (must be a variable listed in {@code info})
    * @param value value to assign to the variable referenced by {@code key}
    *
    * @return this {@link Contact}, with {@code key} set to its new
    * {@code value} (if both {@code key} and {@code value} are valid)
    *
    **/
  public Contact set (String key, String value) {
    if (!keyExists(key)) return this;

    String KEY = key.toUpperCase();
    Entry<String,String> old = info.get(KEY);

    //--------------------------------------------------------------------------
    //  validate arguments
    //--------------------------------------------------------------------------

    // if value is null, no validation needed
    if (value == null || "".equals(value.trim())) {
      value = null;

    } else {

      Pattern p; Matcher m;
      switch (KEY) {

        case "FIRSTNAME":
        case "SURNAME":

          // only allow letters, spaces, dashes, and apostrophes in
          // names, in an attempt to prevent SQL injection attacks

          p = Pattern.compile("[^a-zA-Z -']");
          m = p.matcher(value);

          if (m.find()) {
            IOUtils.printError("set()", "name fields can only contain letters, spaces, dashes (-) and apostrophes (')");
            return this;
          }

          // if there are any apostrophes, escape by doubling them
          value = value.replace("'", "''");

        break;

        case "PHONE":

          // only allow numbers and + signs; formatting shouldn't be included in database

          p = Pattern.compile("[^0-9+]");
          m = p.matcher(value);

          if (m.find()) {
            IOUtils.printError("set()", "phone numbers can only contain digits and '+' signs");
            return this;
          }

          if (value.lastIndexOf('+') > 0) {
            IOUtils.printError("set()", "'+' can only appear as the first character in a phone number");
            return this;
          }

        break;

      } } // end else { switch(KEY) {

    info.put(KEY, new SimpleEntry<String, String>(old.getKey(), value));
    return this;
  }

  /**
    * Returns this {@link Contact} formatted so that it can be inserted as a
    * list of values into an SQL table.
    *
    * <p>If all fields of this {@link Contact} are {@code null}, this method
    * returns a {@code null} {@link String}.</p>
    *
    * @return this {@link Contact} formatted so that it can be inserted as a
    * list of values into an SQL table
    *
    **/
  @Override
  public String toString() {

    // get names of non-null variables, (format, like, this):
    String varNames = this.info.entrySet().stream()
      .filter(e -> e.getValue().getValue() != null).map(e -> e.getKey())
      .collect(Collectors.joining(", ", "(", ")"));

    // get non-null variables, ('format', 'like', 'this'):
    String varVals = this.info.entrySet().stream()
      .map(e -> e.getValue().getValue()).filter(v -> v != null)
      .collect(Collectors.joining("', '", "('", "')"));

    // if empty, return null
    if ("()".equals(varNames) && "('')".equals(varVals)) {
//    IOUtils.printError("toString()", "all contact information is null");
      return null;
    }

    return (varNames + " values " + varVals);
  }

}
