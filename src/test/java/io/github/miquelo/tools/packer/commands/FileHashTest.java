package io.github.miquelo.tools.packer.commands;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.miquelo.tools.packer.commands.FileHash;

@ExtendWith(MockitoExtension.class)
public class FileHashTest
{
    private static final String ANY_ALGORITHM = "ANY-ALGORITHM";
    private static final byte[] ANY_HASH = new byte[0];
    private static final String ANY_RELATIVE_PATH = "any-relative-path";
    
    private static final String SOME_ALGORITHM = "SOME-ALGORITHM";
    private static final byte[] SOME_HASH = { 0x01 };
    private static final String SOME_RELATIVE_PATH = "some-relative-path";
    
    private static final String ANOTHER_ALGORITHM = "ANOTHER-ALGORITHM";
    private static final byte[] ANOTHER_HASH = { 0x02 };
    private static final String ANOTHER_RELATIVE_PATH = "another-relative-path";

    public FileHashTest()
    {
    }
    
    @Test
    public void getItsAlgorithm()
    {
        FileHash fileHash = new FileHash(
            SOME_ALGORITHM,
            ANY_HASH,
            ANY_RELATIVE_PATH);
        
        assertThat(fileHash.getAlgorithm())
            .isEqualTo(SOME_ALGORITHM);
    }
    
    @Test
    public void getItsHash()
    {
        FileHash fileHash = new FileHash(
            ANY_ALGORITHM,
            SOME_HASH,
            ANY_RELATIVE_PATH);
        
        assertThat(fileHash.getHash())
            .isEqualTo(SOME_HASH);
    }
    
    @Test
    public void getItsRelativePath()
    {
        FileHash fileHash = new FileHash(
            ANY_ALGORITHM,
            ANY_HASH,
            SOME_RELATIVE_PATH);
        
        assertThat(fileHash.getRelativePath())
            .isEqualTo(SOME_RELATIVE_PATH);
    }
    
    @Test
    public void hasHashCode()
    {
        FileHash fileHash = new FileHash(
            ANY_ALGORITHM,
            ANY_HASH,
            ANY_RELATIVE_PATH);
        
        fileHash.hashCode();
    }
    
    @Test
    public void isEqualByObjectReference()
    {
        FileHash someFileHash = new FileHash(
            ANY_ALGORITHM,
            ANY_HASH,
            ANY_RELATIVE_PATH);
        FileHash anotherFileHash = someFileHash;
        
        boolean result = someFileHash.equals(anotherFileHash);
        
        assertThat(result).isTrue();
    }
    
    @Test
    public void isNotEqualByObjectClass()
    {
        FileHash someFileHash = new FileHash(
            ANY_ALGORITHM,
            ANY_HASH,
            ANY_RELATIVE_PATH);
        Object anotherObject = new Object();
        
        boolean result = someFileHash.equals(anotherObject);
        
        assertThat(result).isFalse();
    }
    
    @Test
    public void isNotEqualByAlgorithm()
    {
        FileHash someFileHash = new FileHash(
            SOME_ALGORITHM,
            SOME_HASH,
            SOME_RELATIVE_PATH);
        FileHash anotherFileHash = new FileHash(
            ANOTHER_ALGORITHM,
            SOME_HASH,
            SOME_RELATIVE_PATH);
        
        boolean result = someFileHash.equals(anotherFileHash);
        
        assertThat(result).isFalse();
    }
    
    @Test
    public void isNotEqualByHash()
    {
        FileHash someFileHash = new FileHash(
            SOME_ALGORITHM,
            SOME_HASH,
            SOME_RELATIVE_PATH);
        FileHash anotherFileHash = new FileHash(
            SOME_ALGORITHM,
            ANOTHER_HASH,
            SOME_RELATIVE_PATH);
        
        boolean result = someFileHash.equals(anotherFileHash);
        
        assertThat(result).isFalse();
    }
    
    @Test
    public void isNotEqualByRelativePath()
    {
        FileHash someFileHash = new FileHash(
            SOME_ALGORITHM,
            SOME_HASH,
            SOME_RELATIVE_PATH);
        FileHash anotherFileHash = new FileHash(
            SOME_ALGORITHM,
            SOME_HASH,
            ANOTHER_RELATIVE_PATH);
        
        boolean result = someFileHash.equals(anotherFileHash);
        
        assertThat(result).isFalse();
    }
}
