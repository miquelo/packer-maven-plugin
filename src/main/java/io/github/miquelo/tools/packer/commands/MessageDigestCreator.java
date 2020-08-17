package io.github.miquelo.tools.packer.commands;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

/**
 * Factory interface for creating {@link MessageDigest} instances.
 * 
 * Some commands may need a {@link MessageDigest} instance in order fulfill some
 * specific requirement. This function permits to decouple instance creation
 * given a digest algorithm from an specific {@link Provider} specification.
 * 
 * It adds the ability to throw a {@link NoSuchAlgorithmException} when creation
 * fails for that reason.
 * 
 * @see MessageDigest#getInstance(String)
 */
@FunctionalInterface
public interface MessageDigestCreator
{
	/**
	 * Create a {@link MessageDigest} instance given an algorithm.
	 * 
	 * It allows to decouple it from a {@link Provider} specification.
	 * 
	 * @param algorithm
	 *     Digest algorithm.
	 *     
	 * @return
	 *     The new created {@link MessageDigest} instance.
	 *     
	 * @throws NoSuchAlgorithmException
	 *     If the given algorithm is not recognized.
	 *     
	 * @see MessageDigest#getInstance(String)
	 * @see MessageDigest#getInstance(String, Provider)
	 * @see MessageDigest#getInstance(String, String)
	 */
    MessageDigest create(String algorithm)
    throws NoSuchAlgorithmException;
}
