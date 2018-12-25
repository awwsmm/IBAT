package watson;

import java.util.LinkedHashMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

import java.util.Optional;
import java.util.stream.Collectors;

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

  // "double brace" initialisation: http://bit.ly/2Agb7xX
  protected LinkedHashMap<String, Entry<String,String>> info =
        new LinkedHashMap<String, Entry<String,String>>(){{

    put("FIRSTNAME", new SimpleEntry<String, String>("varchar(40)", null));
    put("SURNAME",   new SimpleEntry<String, String>("varchar(40)", null));
    put("PHONE",     new SimpleEntry<String, String>("varchar(16)", null));

  }};

  // check if key exists
  public boolean keyExists (String key) {

    // filter out null, empty, and all-whitespace keys
    if (key == null || "".equals(key.trim())) {
      IO.printError("keyExists()", "null, empty, or all-whitespace keys not allowed");
      return false;
    }

    // filter out keys that don't exist above
    String KEY = key.toUpperCase();
    if (!info.containsKey(KEY)) {
      IO.printError("keyExists()", "Contact doesn't contain key '" + key + "'");
      return false;
    }

    // otherwise, key exists
    return true;
  }

  // get the value associated with the given key
  public Optional<String> get (String key) {
    if (!keyExists(key)) return Optional.empty();
    String KEY = key.toUpperCase();
    return Optional.of(info.get(KEY).getValue());
  }

  // set value to null or empty to remove value
  public Contact set (String key, String value) {
    if (!keyExists(key)) return this;

    String KEY = key.toUpperCase();
    Entry old = info.get(KEY);

    if ("".equals(value)) value = null;
    info.put(KEY, new SimpleEntry(old.getKey(), value));
    return this;
  }

  // returns null if all fields are null
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
      IO.printError("toString()", "all contact information is null");
      return null;
    }

    return (varNames + " values " + varVals);
  }

}
