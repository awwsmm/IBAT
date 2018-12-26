package watson;

import java.util.LinkedHashMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

import java.util.Optional;
import java.util.stream.Collectors;

/**
  * Contact class for associated {@code CONTACT} table in database.
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

    if (value == null || "".equals(value.trim())) value = null;
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
      IOUtils.printError("toString()", "all contact information is null");
      return null;
    }

    return (varNames + " values " + varVals);
  }

}
