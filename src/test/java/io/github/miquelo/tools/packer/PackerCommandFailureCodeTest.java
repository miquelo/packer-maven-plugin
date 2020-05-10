package io.github.miquelo.tools.packer;

import static io.github.miquelo.tools.packer.PackerCommandFailureCode
    .FAILURE_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class PackerCommandFailureCodeTest
{
    public PackerCommandFailureCodeTest()
    {
    }
    
    @Test
    public void hasFailureError()
    {
        assertThat(FAILURE_ERROR)
            .isNotNull();
    }
}
