package io.github.miquelo.maven.plugin.packer;

import java.util.function.Supplier;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import io.github.miquelo.tools.packer.PackerCommandLogger;
import io.github.miquelo.tools.packer.PackerOutputMessage;
import io.github.miquelo.tools.packer.PackerTool;

public abstract class AbstractPackerMojo
extends AbstractMojo
{
    private final PackerTool packerTool;
    
    /**
     * Whether this execution must be skipped.
     */
    @Parameter(
        defaultValue="false"
    )
    private boolean skip;
    
    protected AbstractPackerMojo()
    {
        packerTool = PackerTool.newInstance(
            new MojoPackerCommandLogger(this::getLog),
            this::acceptOutputMessage);
        skip = false;
    }
    
    @Override
    public final void execute()
    throws MojoFailureException, MojoExecutionException
    {
        if (skip)
            getLog().info("Execution skipped...");
        else
            execute(packerTool);
    }
    
    protected abstract void execute(PackerTool packerTool)
    throws MojoFailureException, MojoExecutionException;
    
    protected void acceptOutputMessage(PackerOutputMessage message)
    {
        // Do nothing by default...
    }
    
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
        public void warn(String message)
        {
            logSupplier.get().warn(message);
        }
        
        @Override
        public void debug(String message)
        {
            logSupplier.get().debug(message);
        }
    }
}
