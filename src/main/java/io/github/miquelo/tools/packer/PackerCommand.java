package io.github.miquelo.tools.packer;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Attributes and life cycle contracts of a Packer command.
 * 
 * Each command must determine Packer the resulting command name, additional
 * arguments and the working directory. In addition, it can control an
 * specific task life cycle in order to fulfill its command requirements.
 */
public interface PackerCommand
{
    /**
     * Name of the command.
     */
    String getName();
    
    /**
     * Arguments passed to the command.
     */
    List<Object> getArguments();
    
    /**
     * Desired working directory for the command to run.
     * 
     * It returns {@code Optional.empty()} if default directory must be used.
     */
    Optional<File> getWorkingDir();
    
    /**
     * Life cycle initialization step.
     * 
     * It may prepare all that the command needs to be ran. Return flag
     * determines if command will continue running or it must be skipped.
     * 
     * @param logger
     *     Logger passed by command task.
     * @param timeoutHandler
     *     Timeout handler aligned with command task.
     *     
     * @return
     *     {@code true} if command must continue running, {@code false}
     *     otherwise.
     *     
     * @throws PackerCommandException
     *     When initialization error has occurred.
     * @throws TimeoutException
     *     When timeout has reached during initialization.
     */
    boolean init(PackerCommandLogger logger, TimeoutHandler timeoutHandler)
    throws PackerCommandException, TimeoutException;
    
    /**
     * Callback called when command succeeds.
     */
    void onSuccess();
    
    /**
     * Callback called when command fails.
     * 
     * @param failureCode
     *     Related failure code.
     */
    void onFailure(PackerCommandFailureCode failureCode);
    
    /**
     * Callback called when command execution is aborted.
     */
    void onAbort();
    
    /**
     * Map underlying command execution error code to command failure.
     * 
     * @param errorCode
     *     Underlying execution error code value.
     *     
     * @return
     *     Mapped failure code.
     */
    PackerCommandFailureCode mapFailureCode(int errorCode);
}
