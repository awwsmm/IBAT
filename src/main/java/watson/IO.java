package watson;

import java.sql.SQLException;

public class IO {

  // prints an error message to the standard error stream
  protected static void printError (String methodSignature, String message) {
    System.err.printf("%n         ERROR | %s : %s%n", methodSignature, message);
  }

  // prints a warning message to the standard error stream
  protected static void printWarning (String methodSignature, String message) {
    System.err.printf("%n       WARNING | %s : %s%n", methodSignature, message);
  }

  // prints a message to the standard error stream
  protected static void printMessage (String methodSignature, String message) {
    System.err.printf("%n       MESSAGE | %s : %s%n", methodSignature, message);
  }

  // prints an SQLException message to the standard error stream (bit.ly/2zJV23d)
  protected static void printSQLException (String methodSignature, SQLException ex) {
    while (ex != null) {
      System.err.printf("%n  SQLException | %s : %s [SQL State: %s, Error Code: %d]",
        methodSignature, ex.getMessage(), ex.getSQLState(), ex.getErrorCode());
      ex = ex.getNextException();
    } System.err.println();
  }

}
