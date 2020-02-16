package io.github.miquelo.tools.packer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FileHashTest
{
    private static final byte[] ANY_HASH = new byte[0];
    private static final String ANY_RELATIVE_PATH = "any-relative-path";
    
    private static final String SOME_ALGORITHM = "some-algorithm";

    public FileHashTest()
    {
    }
    
    @Test
    public void algorithmGetter()
    {
        FileHash fileHash = new FileHash(
            SOME_ALGORITHM,
            ANY_HASH,
            ANY_RELATIVE_PATH);
        
        assertThat(fileHash.getAlgorithm())
            .isEqualTo(SOME_ALGORITHM);
    }
}
