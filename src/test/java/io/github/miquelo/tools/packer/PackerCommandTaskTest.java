package io.github.miquelo.tools.packer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PackerCommandTaskTest
{
    private static final boolean ANT_MAY_INTERRUPT_IF_RUNNING = false;

    @Mock
    private PackerExecutionBuilder executionBuilder;

    @Mock
    private PackerCommandLogger logger;

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
    public void isNotCancelledByDefault()
    {
        boolean cancelled = commandTask.isCancelled();
        
        assertThat(cancelled).isFalse();
    }
    
    @Test
    public void skipWhenInitReturnsFalse()
    throws Exception
    {
        when(command.init(any(), any()))
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
        
        Throwable exception = catchThrowable(() -> commandTask.run());
        
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Already started...");
    }
    
    @Test
    public void throwCancelledWhenRunAlreadyCancelled()
    {
        commandTask.cancel(ANT_MAY_INTERRUPT_IF_RUNNING);
        
        Throwable exception = catchThrowable(() -> commandTask.run());
        
        assertThat(exception)
            .isInstanceOf(CancellationException.class);
    }
    
    @Test
    public void onlyAwaitValueWhenGettingAlreadyCompleting()
    throws Exception
    {
        when(command.init(any(), any()))
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
}
