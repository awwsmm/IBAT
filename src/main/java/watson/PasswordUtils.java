package watson;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
// import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtils {

  // private constructor because this is a utility class
  private PasswordUtils(){}

  // cryptographically-secure random number generator
  private static final SecureRandom RAND = new SecureRandom();

  // perform hash mixing X times (default: 2^16 times)
  private static final int ITERATIONS = 65536;

  // return a final cryptographic key of X bytes
  private static final int KEY_LENGTH = 256;

  // use this algorithm to generate the cryptographic key
  private static final String ALGORITHM = "PBKDF2WithHmacSHA1";


  // return a randomly-generated salt String
  public static Optional<String> generateSalt (final int length) {

    if (length < 1) {
      System.err.println("error in generateSalt: length must be > 0");
      return Optional.empty();
    }

    byte[] salt = new byte[length];
    RAND.nextBytes(salt);

    return Optional.of(Base64.getEncoder().encodeToString(salt));
  }


  // encrypt user password using PBKDF2
  public static Optional<String> encryptPassword (String password, String salt) {

    // convert the password to a char array
    char[] chars = password.toCharArray();

    //  `salt` is generated outside this function, with the intention that it
    //  will be written to the database along with the user's encrypted password

    // convert the salt to a byte array
    byte[] bytes = salt.getBytes();

    //--------------------------------------------------------------------------
    //
    //  DEFINITIONS:
    //
    //    password: passed in here as a char[] array to minimize the use of
    //      Strings as much as possible. Since Strings are immutable, there is
    //      no way to overwrite their internal value when the passwords stored
    //      in them are no longer needed (see: PBEKeySpec Javadoc)
    //
    //    salt: random byte attay used to prevent dictionary attacks; the salt
    //      is not secret and can be stored in the database along with the
    //      encrypted passwords
    //
    //    spec: the cryptographic key, generated using the user's password and
    //      the random salt; as a PBEKeySpec object, spec contains methods to
    //      get and set the user's password as plaintext ("transparent")
    //
    //    fac: a SecretKeyFactory which takes `spec` and returns an "opaque
    //      cryptographic key", which "contains no methods or constants";
    //      returns just the cryptographic key itself
    //
    //--------------------------------------------------------------------------

    //  `spec` is a "transparent representation of the underlying key material"
    //    the third argument is the number of times the hashing algorithm runs
    //    the fourth argument is the length of the resulting key

    PBEKeySpec spec = new PBEKeySpec(chars, bytes, ITERATIONS, KEY_LENGTH);

    //  zero out the password once it's no longer needed
    //    fill with null characters ('\000')

    Arrays.fill(chars, Character.MIN_VALUE);

    //  generateSecret() returns an opaque SecretKey object, which contains no
    //  underlying "raw key material" -- just the cryptographic key

    try {
      SecretKeyFactory fac = SecretKeyFactory.getInstance(ALGORITHM);
      byte[] securePassword = fac.generateSecret(spec).getEncoded();

      //  encrypted key is returned encoded in Base64 because, using base 64, we
      //  can compactly represent binary data using only printable ASCII chars

      return Optional.of(Base64.getEncoder().encodeToString(securePassword));

    // if exception encountered, return empty Optional
    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
      System.err.println("Exception encountered in encryptPassword()");
      return Optional.empty();

    } finally {

      //  return statement is executed *after* finally block, so password is
      //  always cleared from `spec`, unless JVM crashes / exits in try{} block

      spec.clearPassword();
    }
  }


  // verify user password from secret key and salt; returns false if any errors
  public static boolean verifyPassword (String password, String key, String salt) {
    Optional<String> optEncrypted = encryptPassword(password, salt);
    if (!optEncrypted.isPresent()) return false;

    System.out.println(optEncrypted.get());
    System.out.println(key);

    return optEncrypted.get().equals(key);
  }





}


// Resources:
//   https://www.baeldung.com/java-password-hashing
//   http://appsdeveloperblog.com/encrypt-user-password-example-java
//   https://stackoverflow.com/questions/4070693/what-is-the-purpose-of-base-64-encoding-and-why-is-it-used-in-http-basic-authentica
//   https://en.wikipedia.org/wiki/PBKDF2


