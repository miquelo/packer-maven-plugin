package io.github.miquelo.tools.packer;

import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.concat;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Packer command task available as {@link RunnableFuture} that is intended to
 * obtain a result represented by {@link PackerCommandResult}.
 */
public final class PackerCommandTask
implements RunnableFuture<PackerCommandResult>
{
    private static final long EXECUTION_WAIT_TIMEOUT = 2;
    private static final TimeUnit EXECUTION_WAIT_UNIT = DAYS;
    
    private final PackerExecutionStarter executionStarter;
    private final PackerCommandLogger logger;
    private final Consumer<PackerOutputMessage> messageConsumer;
    private final PackerCommand command;
    
    private final AtomicBoolean started;
    private final AtomicReference<PackerCommandResult> result;
    private final ReadWriteLock resultLock;
    private final AtomicBoolean cancelled;
    private final AtomicReference<ExecutionException> executionException;
    private final AtomicReference<TimeoutException> timeoutException;
    private final AtomicReference<PackerExecution> execution;
    
    /**
     * Command task with its logger, its output message consumer and the
     * command to be executed.
     * 
     * @param logger
     *     Logger for this task, that is also available for executed command.
     * @param messageConsumer
     *     Consumer for the output messages produced by Packer command
     *     execution.
     * @param command
     *     Command to be executed by this task.
     */
    public PackerCommandTask(
        PackerCommandLogger logger,
        Consumer<PackerOutputMessage> messageConsumer,
        PackerCommand command)
    {
        this(
            PackerCommandTask::executionStart,
            logger,
            messageConsumer,
            command);
    }
    
    PackerCommandTask(
        PackerExecutionStarter executionStarter,
        PackerCommandLogger logger,
        Consumer<PackerOutputMessage> messageConsumer,
        PackerCommand command)
    {
        this.executionStarter = requireNonNull(executionStarter);
        this.logger = requireNonNull(logger);
        this.messageConsumer = requireNonNull(messageConsumer);
        this.command = requireNonNull(command);
        
        started = new AtomicBoolean(false);
        result = new AtomicReference<>();
        resultLock = new ReentrantReadWriteLock();
        cancelled = new AtomicBoolean(false);
        executionException = new AtomicReference<>();
        timeoutException = new AtomicReference<>();
        execution = new AtomicReference<>();
    }
    
    /**
     * Complete task if it is not already started.
     */
    @Override
    public void run()
    {
        complete(TimeoutHandlerIrrelevant.INSTANCE, true);
    }
    
    /**
     * Flag indicating this task is already done.
     */
    @Override
    public boolean isDone()
    {
        return result.get() != null
            || cancelled.get()
            || executionException.get() != null
            || timeoutException.get() != null;
    }
    
    /**
     * Flag indicating this task is cancelled.
     */
    @Override
    public boolean isCancelled()
    {
        return cancelled.get();
    }
    
    /**
     * Obtain result with waiting for computing it when needed.
     */
    @Override
    public PackerCommandResult get()
    throws InterruptedException, ExecutionException
    {
        complete(TimeoutHandlerIrrelevant.INSTANCE, false);
        if (executionException.get() != null)
            throw executionException.get();
        return resultGet();
    }
    
    /**
     * Obtain result with waiting for computing it when needed during the
     * specified time.
     */
    @Override
    public PackerCommandResult get(long timeout, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException
    {
        TimeoutHandler timeoutHandler = new TimeoutHandlerRelevant(
            timeout,
            unit);
        complete(timeoutHandler, false);
        if (executionException.get() != null)
            throw executionException.get();
        if (timeoutException.get() != null)
            throw timeoutException.get();
        return resultTryGet(timeoutHandler.checkIt());
    }
    
    /**
     * Cancel this task.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        if (cancelled.get()
           || execution.get() == null
           || executionException.get() != null)
            return false;
        
        if (mayInterruptIfRunning)
            cancelled.set(execution.get().interrupt());
        else
            cancelled.set(true);
        return cancelled.get();
    }
    
    private void complete(TimeoutHandler timeoutHandler, boolean fromRun)
    {
        if (cancelled.get())
            throw new CancellationException();
        if (started.compareAndSet(false, true))
            complete(new Thread(this::aborted), timeoutHandler);
        else if (fromRun)
            throw new IllegalStateException("Already started...");
    }
    
    private void complete(Thread shutdownHook, TimeoutHandler timeoutHandler)
    {
        try
        {
            resultLock.writeLock().lock();
            getRuntime().addShutdownHook(shutdownHook);
            
            if (command.init(logger, timeoutHandler.checkIt()))
            {
                File workingDir = command.getWorkingDir()
                    .orElseGet(PackerCommandTask::defaultWorkingDir);
                
                logger.debug(format(
                    "Going to run Packer with arguments [%s] on %s",
                    concat(
                        Stream.of(command.getName()),
                        command.getArguments().stream())
                        .map(Object::toString)
                        .collect(joining(", ")),
                    workingDir.getAbsolutePath()));
                
                execution.set(executionStarter.start(
                    messageConsumer,
                    workingDir,
                    command.getName(),
                    command.getArguments(),
                    EXECUTION_WAIT_TIMEOUT,
                    EXECUTION_WAIT_UNIT));
                int errorCode = execution.get().errorCode(
                    timeoutHandler.checkIt());
                if (errorCode == 0)
                {
                    command.onSuccess();
                    result.set(new PackerCommandResultImpl());
                }
                else
                {
                    PackerCommandFailureCode failureCode =
                        command.mapFailureCode(errorCode);
                    command.onFailure(failureCode);
                    result.set(new PackerCommandResultImpl(failureCode));
                }
            }
            else
                result.set(new PackerCommandResultImpl(true));
        }
        catch (PackerCommandException exception)
        {
            result.set(new PackerCommandResultImpl(exception));
        }
        catch (InterruptedException | IOException | RuntimeException exception)
        {
            this.executionException.set(new ExecutionException(exception));
        }
        catch (TimeoutException exception)
        {
            this.timeoutException.set(exception);
        }
        finally
        {
            getRuntime().removeShutdownHook(shutdownHook);
            resultLock.writeLock().unlock();
        }
    }
    
    private PackerCommandResult resultGet()
    {
        try
        {
            resultLock.readLock().lock();
            return result.get();
        }
        finally
        {
            resultLock.readLock().unlock();
        }
    }
    
    private PackerCommandResult resultTryGet(TimeoutHandler timeoutHandler)
    throws InterruptedException, TimeoutException
    {
        try
        {
            if (!resultLock.readLock().tryLock(
                timeoutHandler.getTimeout(),
                timeoutHandler.getUnit()))
                throw new TimeoutException();
            return result.get();
        }
        finally
        {
            resultLock.readLock().unlock();
        }
    }
    
    private void aborted()
    {
        command.onAbort();
        logger.warn("Packer execution aborted");
    }
    
    private static File defaultWorkingDir()
    {
        return new File(System.getProperty("user.dir"));
    }  
    
    private static PackerExecution executionStart(
        Consumer<PackerOutputMessage> messageConsumer,
        File workingDir,
        String name,
        List<Object> args,
        long waitTimeout,
        TimeUnit waitUnit)
    throws IOException, InterruptedException
    {
    	return new PackerExecution(
    	    messageConsumer,
    	    workingDir,
    	    name,
    	    args,
    	    waitTimeout,
    	    waitUnit);
    }
}

@FunctionalInterface
interface PackerExecutionStarter
{
    PackerExecution start(
        Consumer<PackerOutputMessage> messageConsumer,
        File workingDir,
        String name,
        List<Object> args,
        long waitTimeout,
        TimeUnit waitUnit)
    throws IOException, InterruptedException;
}
