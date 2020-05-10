package io.github.miquelo.tools.packer;

import static io.github.miquelo.tools.packer.PackerCommandFailureCode.FAILURE_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

public class PackerCommandResultTest
{
    private static final boolean NOT_IGNORED = false;
    private static final boolean IGNORED = true;
    
    private static final PackerCommandException SOME_EXCEPTION =
        new PackerCommandException("any-message");
    private static final PackerCommandFailureCode SOME_FAILURE_CODE =
        FAILURE_ERROR;

    public PackerCommandResultTest()
    {
    }
    
    @Test
    public void getSuccessNotIgnoredByDefault()
    throws Exception
    {
        PackerCommandResult result = new PackerCommandResultImpl();
        
        boolean notIgnored = result.success();
        
        assertThat(notIgnored)
            .isTrue();
    }
    
    @Test
    public void getSuccessNotIgnored()
    throws Exception
    {
        PackerCommandResult result = new PackerCommandResultImpl(NOT_IGNORED);
        
        boolean notIgnored = result.success();
        
        assertThat(notIgnored)
            .isTrue();
    }
    
    @Test
    public void getSuccessIgnored()
    throws Exception
    {
        PackerCommandResult result = new PackerCommandResultImpl(IGNORED);
        
        boolean notIgnored = result.success();
        
        assertThat(notIgnored)
            .isFalse();
    }
    
    @Test
    public void getUnsuccessDueToError()
    throws Exception
    {
        PackerCommandResult result = new PackerCommandResultImpl(
            SOME_EXCEPTION);
        
        Throwable exception = catchThrowable(() -> result.success());
        
        assertThat(exception)
            .isEqualTo(SOME_EXCEPTION);
    }
    
    @Test
    public void getUnsuccessDueToFailure()
    throws Exception
    {
        PackerCommandResult result = new PackerCommandResultImpl(
            SOME_FAILURE_CODE);
        
        Throwable exception = catchThrowable(() -> result.success());
        
        assertThat(exception)
            .isInstanceOfSatisfying(
                PackerCommandFailureException.class,
                failureException -> assertThat(
                    failureException.getFailureCode())
                        .isEqualTo(SOME_FAILURE_CODE));
    }
}
