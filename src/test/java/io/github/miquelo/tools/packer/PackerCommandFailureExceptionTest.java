package io.github.miquelo.tools.packer;

import static io.github.miquelo.tools.packer.PackerCommandFailureCode
    .FAILURE_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class PackerCommandFailureExceptionTest
{
    private static final PackerCommandFailureCode SOME_FAILURE_CODE =
        FAILURE_ERROR;

    public PackerCommandFailureExceptionTest()
    {
    }
    
    @Test
    public void getItsFailureCode()
    {
        PackerCommandFailureException exception =
            new PackerCommandFailureException(SOME_FAILURE_CODE);
        
        assertThat(exception.getFailureCode())
            .isEqualTo(SOME_FAILURE_CODE);
    }
}
