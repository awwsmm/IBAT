package watson;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
  * Utility class for password encryption and verification.
  *
  * <p>This class uses the
  * <a href="http://bit.ly/2GJPdZk">PBKDF2WithHmacSHA512</a> algorithm for
  * generating 512-bit cryptographic keys. This is a
  * Password-Based-Key-Derivative Function (PBKDF) which is set to mix the hash
  * at least 2<sup>16</sup> (65536) times. Salt for this hashing algorithm is
  * generate using a {@link SecureRandom}, which is a cryptographically-secure
  * random number generator.</p>
  *
  * <p>A blog post, inspired by writing this class, can be found on
  * <a href="https://dev.to/awwsmm/how-to-encrypt-a-password-in-java-42dh">my
  * Dev.To page</a>. It contains a thorough explanation of this code, while the
  * text below only provides example usage.</p>
  *
  * <p>To generate a {@code salt} string, do:</p>
  *
  * <pre>{@code
  * jshell> import watson.*
  *
  * jshell> String salt = PasswordUtils.generateSalt(512).get()
  * salt ==> "1T1uB6kjrp9YEBTvROPa8n8VbOGE+WJk4ds5asPxzH76HUEu ... 18Ly52X02aAfgUhKHo4B9JXk="
  * }</pre>
  *
  * <p>Then, hash the password and generate the key:</p>
  *
  * <pre>{@code
  * jshell> String key = PasswordUtils.hashPassword("Of Salesmen!", salt).get()
  * key ==> "AszLKBIMpxGdUKlcGryNUizi7yKhVGOqpyrxpT6UOU+XG6ie ... rZGgLP7wq/HhBmKPdqXG8eA=="
  * }</pre>
  *
  * <p>(Try not to store the password as a {@link String} anywhere, if you can
  * help it.) Then, you can tell if a user-entered password is correct:</p>
  *
  * <pre>{@code
  * jshell> PasswordUtils.verifyPassword("Of Salesmen!", key, salt)
  * $4 ==> true
  *
  * jshell> PasswordUtils.verifyPassword("By-Tor! And the Snow Dog!", key, salt)
  * $5 ==> false
  * }</pre>
  *
  **/
public final class PasswordUtils {

  // private constructor because this is a utility class
  private PasswordUtils(){}

  // cryptographically-secure random number generator
  private static final SecureRandom RAND;

  // static initializer for SecureRandom instance
  // need to do this, because getInstanceStrong() throws an Exception
  // it should never actually throw that Exception, though (see docs)

  static {

    // default random number algorithm
    SecureRandom temp = new SecureRandom();

    try { // strong algorithm; Java distribution-specific
      temp = SecureRandom.getInstanceStrong();

    } catch (NoSuchAlgorithmException ex) {
      // do nothing, use default SecureRandom()

    } finally {
      RAND = temp;
    }
  }

  // perform hash mixing X times (default: 2^16 = 65536 times)
  private static final int ITERATIONS = 65536;

  // return a final cryptographic key of X bytes
  private static final int KEY_LENGTH = 512;

  // use this algorithm to generate the cryptographic key
  private static final String ALGORITHM = "PBKDF2WithHmacSHA512";

  /**
    * Generates a random {@code byte[]} of the given {@code length} using
    * {@link SecureRandom#getInstanceStrong()} and returns that array as a
    * base-64-encoded {@link String}, wrapped in an {@link Optional}.
    *
    * <p>Returns {@link Optional#empty an empty Optional} if {@code length}
    * is {@code < 1}. A {@code length} of at least 32 is recommended, though the
    * default in this package is 512.</p>
    *
    * @param length length of the random {@code byte[]} to generate
    *
    * @return a "salt" {@link String} to use as the second argument to
    * {@link hashPassword(String, String) hashPassword()}, wrapped in an
    * {@link Optional}
    *
    **/
  public static Optional<String> generateSalt (final int length) {

    if (length < 1) {
      IOUtils.printError("generateSalt()", "length must be > 0");
      return Optional.empty();
    }

    byte[] salt = new byte[length];
    RAND.nextBytes(salt);

    return Optional.of(Base64.getEncoder().encodeToString(salt));
  }

  /**
    * Hashes the given password using the
    * <a href="http://bit.ly/2GJPdZk">PBKDF2WithHmacSHA512</a> algorithm,
    * mixing at least 2<sup>16</sup> times, generating a 512-bit key,
    * which is returned as a base-64-encoded {@link String}.
    *
    * <p>Returns {@link Optional#empty an empty Optional} if the algorithm
    * "PBKDF2WithHmacSHA512" can't be found in this Java distribution or if the
    * PBEKey otherwise cannot be created.</p>
    *
    * @param password password to hash
    * @param salt extra random string to use in the hash mixing algorithm
    *
    * @return the hashed password as an {@code Optional<String>}, or
    * {@link Optional#empty an empty Optional} if there was a problem
    *
    **/
  public static Optional<String> hashPassword (String password, String salt) {

    // convert the password to a char array
    char[] chars = password.toCharArray();

    // `salt` is generated outside this function, with the intention that it
    // will be written to the database along with the user's encrypted password

    // convert the salt to a byte array
    byte[] bytes = salt.getBytes();

    // `spec` is a "transparent representation of the underlying key material"
    //   the third argument is the number of times the hashing algorithm runs
    //   the fourth argument is the length of the resulting key

    PBEKeySpec spec = new PBEKeySpec(chars, bytes, ITERATIONS, KEY_LENGTH);

    // zero out the password once it's no longer needed
    //   (fill with null characters, '\000')

    Arrays.fill(chars, Character.MIN_VALUE);

    // generateSecret() returns an opaque SecretKey object, which contains no
    // underlying "raw key material" -- just the cryptographic key

    try {
      SecretKeyFactory fac = SecretKeyFactory.getInstance(ALGORITHM);
      byte[] securePassword = fac.generateSecret(spec).getEncoded();

      // encrypted key is returned encoded in Base64 because, using base-64, we
      // can compactly represent binary data using only printable ASCII chars
      //   (and save this to a database as a character string)

      return Optional.of(Base64.getEncoder().encodeToString(securePassword));

    // if exception encountered, return empty Optional
    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
      IOUtils.printError("hashPassword()", "exception encountered... hashing aborted");
      return Optional.empty();

    } finally {

      //  return statement is executed *after* finally block, so password is
      //  always cleared from `spec`, unless JVM crashes / exits in try{} block

      spec.clearPassword();
    }
  }

  /**
    * Given the {@code password} and the {@code salt} used to hash that
    * password, verifies that the cryptographic key generated matches the given
    * {@code key}.
    *
    * <p>Returns {@code false} if the {@code password} and {@code salt} do not
    * generate the {@code key}, or if {@link hashPassword(String, String)}
    * with {@code password} and {@code salt} as arguments returns
    * {@link Optional#empty an empty Optional}.</p>
    *
    * @param password password to verify
    * @param key cryptographic key assumed to have been generated from the given
    * {@code password}
    * @param salt salt used to generate {@code key} (along with original password)
    *
    * @return {@code true} if the {@code password} and {@code salt} can be used
    * to reconstruct the {@code key}, i.e. if the {@code password} is correct
    *
    **/
  public static boolean verifyPassword (String password, String key, String salt) {
    Optional<String> optEncrypted = hashPassword(password, salt);
    if (!optEncrypted.isPresent()) return false;
    return optEncrypted.get().equals(key);
  }

}

// Resources:
//   https://www.baeldung.com/java-password-hashing
//   http://appsdeveloperblog.com/encrypt-user-password-example-java
//   https://stackoverflow.com/questions/4070693/what-is-the-purpose-of-base-64-encoding-and-why-is-it-used-in-http-basic-authentica
//   https://en.wikipedia.org/wiki/PBKDF2
//   https://stackoverflow.com/questions/19348501/pbkdf2withhmacsha512-vs-pbkdf2withhmacsha1
