package io.github.miquelo.tools.packer.commands;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static javax.xml.bind.DatatypeConverter.parseHexBinary;
import static javax.xml.bind.DatatypeConverter.printHexBinary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.google.common.base.Objects;

class FileHash
{
    private static final int DIGEST_BUFFER_SIZE = 4 * 1024;
    
    private final String algorithm;
    private final byte[] hash;
    private final String relativePath;
    
    public FileHash(String algorithm, byte[] hash, String relativePath)
    {
        this.algorithm = requireNonNull(algorithm);
        this.hash = requireNonNull(hash);
        this.relativePath = requireNonNull(relativePath);
    }
    
    public String getAlgorithm()
    {
        return algorithm;
    }
    
    public byte[] getHash()
    {
        return hash;
    }

    public String getRelativePath()
    {
        return relativePath;
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hashCode(algorithm, hash, relativePath);
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
            return true;
        if (getClass().equals(obj.getClass()))
        {
            FileHash fileHash = (FileHash) obj;
            return algorithm.equalsIgnoreCase(fileHash.algorithm)
                    && Arrays.equals(hash, fileHash.hash)
                    && relativePath.equals(fileHash.relativePath);
        }
        return false;
    }
    
    @Override
    public String toString()
    {
        return format("%s %s %s", algorithm, toString(hash), relativePath);
    }
    
    public static FileHash parse(String str)
    {
        String[] parts = str.split(" ");
        return new FileHash(parts[0], toBytes(parts[1]), parts[2]);
    }
    
    public static FileHash digest(
        MessageDigestCreator digestCreator,
        File baseDir,
        String algorithm,
        String relativePath)
    throws NoSuchAlgorithmException, IOException
    {
        try (InputStream input = new FileInputStream(new File(
            baseDir,
            relativePath)))
        {
            MessageDigest digest = digestCreator.create(algorithm);
            byte[] buf = new byte[DIGEST_BUFFER_SIZE];
            int len = input.read(buf, 0, DIGEST_BUFFER_SIZE);
            while (len > 0)
            {
                digest.update(buf, 0, len);
                len = input.read(buf, 0, DIGEST_BUFFER_SIZE);
            }
            return new FileHash(algorithm, digest.digest(), relativePath);
        }
    }
    
    private static byte[] toBytes(String str)
    {
        return parseHexBinary(str);
    }
    
    private static String toString(byte[] bytes)
    {
        return printHexBinary(bytes);
    }
}
