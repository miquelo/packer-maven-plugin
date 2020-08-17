package io.github.miquelo.tools.packer;

import static io.github.miquelo.tools.packer.PackerCommandFailureCode.FAILURE_ERROR;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PackerCommandTaskTest
{
    private static final boolean ANY_MAY_INTERRUPT_IF_RUNNING = false;

    private static final String ANY_COMMAND_NAME = "any-command-name";
    private static final List<Object> ANY_ARGUMENTS = emptyList();
    
    private static final int SUCCESS_ERROR_CODE = 0;

    private static final Throwable SOME_EXCEPTION = new RuntimeException();

    private static final long SOME_TIMEOUT = 1L;
    private static final TimeUnit SOME_TIME_UNIT = SECONDS;

    private static final PackerCommandFailureCode SOME_FAILURE_CODE =
        FAILURE_ERROR;
    private static final int SOME_FAILURE_ERROR_CODE = 1;

    private static final PackerCommandException SOME_COMMAND_EXCEPTION =
        new PackerCommandException("any-message");

    @Mock
    private PackerExecutionBuilder executionBuilder;
    
    @Mock
    private TimeoutHandlerBuilder timeoutHandlerBuilder;

    @Mock
    private PackerCommandLogger logger;
    
    @Mock
    private TimeoutHandler timeoutHandler;

    @Mock
    private Consumer<PackerOutputMessage> messageConsumer;

    @Mock
    private PackerCommand command;
    
    @Mock
    private PackerExecution execution;
    
    private PackerCommandTask commandTask;
    
    public PackerCommandTaskTest()
    {
        executionBuilder = null;
        logger = null;
        messageConsumer = null;
        command = null;
        execution = null;
        commandTask = null;
    }
    
    @BeforeEach
    public void setUp()
    {
        commandTask = new PackerCommandTask(
            executionBuilder,
            timeoutHandlerBuilder,
            logger,
            messageConsumer,
            command);
    }
    
    @Test
    public void isNotDoneByDefault()
    {
        boolean done = commandTask.isDone();
        
        assertThat(done).isFalse();
    }
    
    @Test
    public void isDoneWhenCompleted()
    {
        commandTask.run();
        
        boolean done = commandTask.isDone();
        
        assertThat(done).isTrue();
    }
    
    @Test
    public void isDoneWhenCancelled()
    {
        commandTask.cancel(ANY_MAY_INTERRUPT_IF_RUNNING);
        
        boolean done = commandTask.isDone();
        
        assertThat(done).isTrue();
    }
    
    @Test
    public void isDoneWhenExecutionBuildHasFailed()
    throws Exception
    {
        when(executionBuilder.build(any(), any(), anyString(), anyList()))
            .thenThrow(IOException.class);
        when(command.getName())
            .thenReturn(ANY_COMMAND_NAME);
        when(command.getArguments())
            .thenReturn(ANY_ARGUMENTS);
        when(command.getWorkingDir())
            .thenReturn(Optional.empty());
        when(command.init(logger, IrrelevantTimeoutHandler.INSTANCE))
            .thenReturn(true);
        commandTask.run();
        
        boolean done = commandTask.isDone();
        
        assertThat(done).isTrue();
    }
    
    @Test
    public void isDoneWhenExecutionHasBeenInterrumpted()
    throws Exception
    {
        when(executionBuilder.build(any(), any(), anyString(), anyList()))
            .thenReturn(execution);
        when(command.getName())
            .thenReturn(ANY_COMMAND_NAME);
        when(command.getArguments())
            .thenReturn(ANY_ARGUMENTS);
        when(command.getWorkingDir())
            .thenReturn(Optional.empty());
        when(command.init(logger, IrrelevantTimeoutHandler.INSTANCE))
            .thenReturn(true);
        when(execution.errorCode(any()))
            .thenThrow(InterruptedException.class);
        commandTask.run();
        
        boolean done = commandTask.isDone();
        
        assertThat(done).isTrue();
    }
    
    @Test
    public void isDoneWhenExecutionHasFailed()
    throws Exception
    {
        when(executionBuilder.build(any(), any(), anyString(), anyList()))
            .thenReturn(execution);
        when(command.getName())
            .thenReturn(ANY_COMMAND_NAME);
        when(command.getArguments())
            .thenReturn(ANY_ARGUMENTS);
        when(command.getWorkingDir())
            .thenReturn(Optional.empty());
        when(command.init(logger, IrrelevantTimeoutHandler.INSTANCE))
            .thenReturn(true);
        when(execution.errorCode(any()))
            .thenThrow(RuntimeException.class);
        commandTask.run();
        
        boolean done = commandTask.isDone();
        
        assertThat(done).isTrue();
    }
    
    @Test
    public void isDoneWhenExecutionTimedout()
    throws Exception
    {
        when(command.init(logger, IrrelevantTimeoutHandler.INSTANCE))
            .thenThrow(TimeoutException.class);
        commandTask.run();
        
        boolean done = commandTask.isDone();
        
        assertThat(done).isTrue();
    }
    
    @Test
    public void isNotCancelledByDefault()
    {
        boolean cancelled = commandTask.isCancelled();
        
        assertThat(cancelled).isFalse();
    }
    
    @Test
    public void skipWhenInitReturnsFalse()
    throws Exception
    {
        when(command.init(logger, IrrelevantTimeoutHandler.INSTANCE))
            .thenReturn(false);
        
        commandTask.run();
        
        verify(command, never())
            .onSuccess();
        verify(command, never())
            .onFailure(any());
        verify(command, never())
            .onAbort();
    }
    
    @Test
    public void throwAlreadyRunningWhenRunMoreThanOnce()
    throws Exception
    {
        commandTask.run();
        
        Throwable exception = catchThrowable(commandTask::run);
        
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Already started...");
    }
    
    @Test
    public void throwCancelledWhenRunAlreadyCancelled()
    {
        commandTask.cancel(ANY_MAY_INTERRUPT_IF_RUNNING);
        
        Throwable exception = catchThrowable(commandTask::run);
        
        assertThat(exception)
            .isInstanceOf(CancellationException.class);
    }
    
    @Test
    public void onlyAwaitValueWhenGettingAlreadyCompleting()
    throws Exception
    {
        when(command.init(logger, IrrelevantTimeoutHandler.INSTANCE))
            .thenReturn(false);
        commandTask.run();
        
        commandTask.get();
        
        verify(command, never())
            .onSuccess();
        verify(command, never())
            .onFailure(any());
        verify(command, never())
            .onAbort();
    }
    
    @Test
    public void throwExecutionWhenItHasFailedWhileCompleting()
    throws Exception
    {
        when(command.init(logger, IrrelevantTimeoutHandler.INSTANCE))
            .thenThrow(SOME_EXCEPTION);
        
        Throwable exception = catchThrowable(commandTask::get);
        
        assertThat(exception)
            .isInstanceOf(ExecutionException.class)
            .hasCause(SOME_EXCEPTION);
    }
    
    @Test
    public void completeWithSuccessResult()
    throws Exception
    {
        when(executionBuilder.build(any(), any(), anyString(), anyList()))
            .thenReturn(execution);
        when(timeoutHandlerBuilder.build(SOME_TIMEOUT, SOME_TIME_UNIT))
            .thenReturn(timeoutHandler);
        when(timeoutHandler.getTimeout())
            .thenReturn(SOME_TIMEOUT);
        when(timeoutHandler.getUnit())
            .thenReturn(SOME_TIME_UNIT);
        when(timeoutHandler.checkIt())
            .thenReturn(timeoutHandler);
        when(command.getName())
            .thenReturn(ANY_COMMAND_NAME);
        when(command.getArguments())
            .thenReturn(ANY_ARGUMENTS);
        when(command.getWorkingDir())
            .thenReturn(Optional.empty());
        when(command.init(logger, timeoutHandler))
            .thenReturn(true);
        when(execution.errorCode(timeoutHandler))
            .thenReturn(SUCCESS_ERROR_CODE);
        
        boolean success = commandTask.get(SOME_TIMEOUT, SOME_TIME_UNIT)
            .success();
        
        assertThat(success).isTrue();
        verify(command).onSuccess();
    }
    
    @Test
    public void completeWithFailureCode()
    throws Exception
    {
        when(executionBuilder.build(any(), any(), anyString(), anyList()))
            .thenReturn(execution);
        when(timeoutHandlerBuilder.build(SOME_TIMEOUT, SOME_TIME_UNIT))
            .thenReturn(timeoutHandler);
        when(timeoutHandler.getTimeout())
            .thenReturn(SOME_TIMEOUT);
        when(timeoutHandler.getUnit())
            .thenReturn(SOME_TIME_UNIT);
        when(timeoutHandler.checkIt())
            .thenReturn(timeoutHandler);
        when(command.getName())
            .thenReturn(ANY_COMMAND_NAME);
        when(command.getArguments())
            .thenReturn(ANY_ARGUMENTS);
        when(command.getWorkingDir())
            .thenReturn(Optional.empty());
        when(command.init(logger, timeoutHandler))
            .thenReturn(true);
        when(command.mapFailureCode(SOME_FAILURE_ERROR_CODE))
            .thenReturn(SOME_FAILURE_CODE);
        when(execution.errorCode(timeoutHandler))
            .thenReturn(SOME_FAILURE_ERROR_CODE);
        
        PackerCommandFailureException exception =
            (PackerCommandFailureException) catchThrowable(
                () -> commandTask.get(SOME_TIMEOUT, SOME_TIME_UNIT).success());
        
        assertThat(exception.getFailureCode())
            .isEqualTo(SOME_FAILURE_CODE);
        verify(command).onFailure(SOME_FAILURE_CODE);
    }
    
    @Test
    public void completeWithError()
    throws Exception
    {
        when(timeoutHandlerBuilder.build(SOME_TIMEOUT, SOME_TIME_UNIT))
            .thenReturn(timeoutHandler);
        when(timeoutHandler.getTimeout())
            .thenReturn(SOME_TIMEOUT);
        when(timeoutHandler.getUnit())
            .thenReturn(SOME_TIME_UNIT);
        when(timeoutHandler.checkIt())
            .thenReturn(timeoutHandler);
        when(command.init(logger, timeoutHandler))
            .thenThrow(SOME_COMMAND_EXCEPTION);
        
        Throwable exception = catchThrowable(() -> commandTask.get(
            SOME_TIMEOUT,
            SOME_TIME_UNIT)
            .success());
        
        assertThat(exception).isEqualTo(SOME_COMMAND_EXCEPTION);
    }
}
