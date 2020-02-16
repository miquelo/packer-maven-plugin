package io.github.miquelo.maven.plugin.packer;

import static java.util.concurrent.Executors.newFixedThreadPool;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import io.github.miquelo.tools.packer.PackerCommand;
import io.github.miquelo.tools.packer.PackerCommandException;
import io.github.miquelo.tools.packer.PackerCommandFailureException;
import io.github.miquelo.tools.packer.PackerCommandLogger;
import io.github.miquelo.tools.packer.PackerCommandTask;
import io.github.miquelo.tools.packer.PackerOutputMessage;

public abstract class AbstractPackerMojo
extends AbstractMojo
{
    private final ExecutorService commandExecutorService;
    private final PackerCommandLogger commandLogger;
    
    /**
     * Whether this execution must be skipped.
     */
    @Parameter(
        defaultValue="false"
    )
    private boolean skip;
    
    protected AbstractPackerMojo()
    {
        commandExecutorService = newFixedThreadPool(1);
        commandLogger = new MojoPackerCommandLogger(this::getLog);
        skip = false;
    }
    
    @Override
    public final void execute()
    throws MojoFailureException, MojoExecutionException
    {
        try
        {
            if (skip)
                getLog().info("Execution skipped...");
            else
            {
                PackerCommandTask task = new PackerCommandTask(
                    commandLogger,
                    this::acceptOutputMessage,
                    command());
                commandExecutorService.submit(task, task)
                    .get()
                    .get()
                    .success();
            }
        }
        catch (
            InterruptedException |
            ExecutionException |
            PackerCommandException exception)
        {
            throw new MojoExecutionException("Command error", exception);
        }
        catch (PackerCommandFailureException exception)
        {
            throw new MojoFailureException("Command failure", exception);
        }
    }
    
    protected abstract PackerCommand command();
    
    protected abstract void acceptOutputMessage(PackerOutputMessage message);
    
    private static class MojoPackerCommandLogger
    implements PackerCommandLogger
    {
        private final Supplier<Log> logSupplier;
        
        private MojoPackerCommandLogger(Supplier<Log> logSupplier)
        {
            this.logSupplier = logSupplier;
        }

        @Override
        public void info(String message)
        {
            logSupplier.get().info(message);
        }
        
        @Override
        public void debug(String message)
        {
            logSupplier.get().debug(message);
        }
        
        @Override
        public void warn(String message)
        {
            logSupplier.get().warn(message);
        }
        
        @Override
        public void warn(String message, Throwable cause)
        {
            logSupplier.get().warn(message, cause);
        }
        
        @Override
        public void error(String message)
        {
            logSupplier.get().error(message);
        }
        
        @Override
        public void error(String message, Throwable cause)
        {
            logSupplier.get().error(message, cause);
        }
    }
}
