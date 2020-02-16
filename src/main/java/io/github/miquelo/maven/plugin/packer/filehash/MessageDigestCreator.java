package io.github.miquelo.maven.plugin.packer.filehash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@FunctionalInterface
public interface MessageDigestCreator
{
    MessageDigest create(String algorithm)
    throws NoSuchAlgorithmException;
}
