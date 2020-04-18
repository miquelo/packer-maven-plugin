package io.github.miquelo.tools.packer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@FunctionalInterface
public interface MessageDigestCreator
{
    MessageDigest create(String algorithm)
    throws NoSuchAlgorithmException;
}
