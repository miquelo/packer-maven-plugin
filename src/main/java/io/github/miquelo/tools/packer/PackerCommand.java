package io.github.miquelo.tools.packer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.concat;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class PackerCommand
{
    private final PackerCommandLauncher launcher;
    private final Consumer<PackerOutputMessage> messageConsumer;
    private final String name;
    private final List<String> args;
    
    protected final PackerCommandLogger logger;
    
    protected PackerCommand(
        PackerCommandLauncher launcher,
        PackerCommandLogger commandLogger,
        Consumer<PackerOutputMessage> messageConsumer,
        String name,
        List<String> args)
    throws PackerCommandException
    {
        this.launcher = requireNonNull(launcher);
        this.logger = requireNonNull(commandLogger);
        this.messageConsumer = requireNonNull(messageConsumer);
        this.name = ofNullable(name)
            .map(String::trim)
            .filter(str -> !str.isEmpty())
            .orElseThrow(() -> new IllegalArgumentException(
                 "'name' parameter must be non empty"));
        this.args = requireNonNull(args);
    }
    
    public final void execute()
    throws
        PackerCommandException,
        PackerCommandFailureException,
        InterruptedException
    {
        try
        {
            if (init())
            {
                File workingDir = getDesiredWorkingDir()
                    .orElseGet(PackerCommand::defaultWorkingDir);
                
                logger.debug(format(
                    "Going to run Packer with arguments [%s] on %s",
                    concat(Stream.of(name), args.stream())
                        .collect(joining(", ")),
                    workingDir.getAbsolutePath()));
                
                int errorCode = launch(workingDir);
                if (errorCode == 0)
                    onSuccess();
                else
                {
                    FailureCode failureCode = mapFailureCode(errorCode);
                    onFailure(failureCode);
                    throw new PackerCommandFailureException(failureCode);
                }
            }
        }
        catch (ExecutionException exception)
        {
            throw new PackerCommandException(
                "Packer command execution failure",
                exception);
        }
    }
    
    protected Optional<File> getDesiredWorkingDir()
    {
        return Optional.empty();
    }
    
    protected boolean init()
    throws PackerCommandException
    {
        return true;
    }
    
    protected void onSuccess()
    {
        // Do nothing by default...
    }
    
    protected void onFailure(FailureCode failureCode)
    {
        // Do nothing by default...
    }
    
    protected abstract FailureCode mapFailureCode(int errorCode);
    
    private int launch(File workingDir)
    throws PackerCommandException, InterruptedException, ExecutionException
    {
        try
        {
            return launcher.launch(messageConsumer, workingDir, name, args);
        }
        catch (IOException exception)
        {
            throw new PackerCommandException(
                "Could not launch Packer command",
                exception);
        }
    }
    
    private static File defaultWorkingDir()
    {
        return new File(System.getProperty("user.dir"));
    }
    
    public static enum FailureCode
    {
        FAILURE_ERROR;
    }
}
