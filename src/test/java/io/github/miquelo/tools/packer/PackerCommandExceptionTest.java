package io.github.miquelo.tools.packer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class PackerCommandExceptionTest
{
    private static final String SOME_MESSAGE = "some-message";
    private static final Throwable SOME_CAUSE = new Exception();

    public PackerCommandExceptionTest()
    {
    }
    
    @Test
    public void getItsMessage()
    {
        PackerCommandException exception = new PackerCommandException(
            SOME_MESSAGE);
        
        assertThat(exception.getMessage())
            .isEqualTo(SOME_MESSAGE);
    }
    
    @Test
    public void getItsCause()
    {
        PackerCommandException exception = new PackerCommandException(
            SOME_CAUSE);
        
        assertThat(exception.getCause())
            .isEqualTo(SOME_CAUSE);
    }
    
    @Test
    public void getItsMessageAndCause()
    {
        PackerCommandException exception = new PackerCommandException(
            SOME_MESSAGE,
            SOME_CAUSE);
        
        assertThat(exception.getMessage())
            .isEqualTo(SOME_MESSAGE);
        assertThat(exception.getCause())
            .isEqualTo(SOME_CAUSE);
    }
}
