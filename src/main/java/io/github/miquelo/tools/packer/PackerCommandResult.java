package io.github.miquelo.tools.packer;

import static java.util.Objects.requireNonNull;

/**
 * Result of Packer command task execution.
 * 
 * It can have four states.
 * 
 * <dl>
 *   <dt>Succeed</dt>
 *   <dd>Execution finished successfully</dd>
 *   <dt>Ignored</dt>
 *   <dd>Execution was ignored due to initialization decision</dd>
 *   <dt>Failed</dt>
 *   <dd>Execution was failed due to Packer execution failure</dd>
 *   <dt>Command error</dt>
 *   <dd>Error was occurred due to command initialization error</dd>
 * </dl>
 * 
 * @see PackerCommandTask#get()
 * @see PackerCommandTask#get(long, java.util.concurrent.TimeUnit)
 */
public interface PackerCommandResult
{
    /**
     * Check this result was finished successfully.
     * 
     * @return
     *     {@code false} if execution was ignored, {@code false} otherwise.
     *     
     * @throws PackerCommandException
     *     If initialization error was occurred.
     * @throws PackerCommandFailureException
     *     If Packer failure was occurred.
     */
    boolean success()
    throws PackerCommandException, PackerCommandFailureException;
}

class PackerCommandResultImpl
implements PackerCommandResult
{
    private final boolean ignored;
    private final PackerCommandException exception;
    private final PackerCommandFailureCode failureCode;
    
    PackerCommandResultImpl()
    {
        ignored = false;
        exception = null;
        failureCode = null;
    }
    
    PackerCommandResultImpl(PackerCommandException exception)
    {
        ignored = false;
        this.exception = requireNonNull(exception);
        failureCode = null;
    }
    
    PackerCommandResultImpl(PackerCommandFailureCode failureCode)
    {
        ignored = false;
        exception = null;
        this.failureCode = requireNonNull(failureCode);
    }
    
    PackerCommandResultImpl(boolean ignored)
    {
        this.ignored = true;
        exception = null;
        failureCode = null;
    }
    
    @Override
    public boolean success()
    throws PackerCommandException, PackerCommandFailureException
    {
        if (exception != null)
            throw exception;
        if (failureCode != null)
            throw new PackerCommandFailureException(failureCode);
        return !ignored;
    }
}
