package watson;

import java.sql.SQLException;

/**
  * Utility class for uniformly-formatted error/warning messaging.
  *
  **/
public class IOUtils {

  // private constructor for utility class
  private IOUtils() { }

  /**
    * Prints an error message to the standard error stream.
    *
    * @param methodSignature signature of the calling method, for debugging
    * @param message message to present to the user
    *
    **/
  protected static void printError (String methodSignature, String message) {
    System.err.printf("%n         ERROR | %s : %s%n", methodSignature, message);
  }

  /**
    * Prints a warning message to the standard error stream.
    *
    * @param methodSignature signature of the calling method, for debugging
    * @param message message to present to the user
    *
    **/
  protected static void printWarning (String methodSignature, String message) {
    System.err.printf("%n       WARNING | %s : %s%n", methodSignature, message);
  }

  /**
    * Prints a message to the standard error stream.
    *
    * @param methodSignature signature of the calling method, for debugging
    * @param message message to present to the user
    *
    **/
  protected static void printMessage (String methodSignature, String message) {
    System.err.printf("%n       MESSAGE | %s : %s%n", methodSignature, message);
  }

  /**
    * Prints an {@link SQLException} message to the standard error stream.
    *
    * @param methodSignature signature of the calling method, for debugging
    * @param ex {@link SQLException} to print to the terminal
    *
    **/
  /// add "see also" link: http://bit.ly/2zJV23d
  protected static void printSQLException (String methodSignature, SQLException ex) {
    while (ex != null) {
      System.err.printf("%n  SQLException | %s : %s [SQL State: %s, Error Code: %d]",
        methodSignature, ex.getMessage(), ex.getSQLState(), ex.getErrorCode());
      ex = ex.getNextException();
    } System.err.println();
  }

}
