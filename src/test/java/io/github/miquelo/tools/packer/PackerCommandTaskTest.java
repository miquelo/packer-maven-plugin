package io.github.miquelo.tools.packer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PackerCommandTaskTest
{
    @Mock
    private PackerExecutionStarter executionStarter;

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
        executionStarter = null;
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
            executionStarter,
            logger,
            messageConsumer,
            command);
    }
    
    @Test
    public void isNotDoneByDefault()
    {
        assertThat(commandTask.isDone())
            .isFalse();
    }
    
    @Test
    public void isNotCancelledByDefault()
    {
        assertThat(commandTask.isCancelled())
            .isFalse();
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
    public void throwAlreadyRunningWhenRunMoteThanOnce()
    throws Exception
    {
        commandTask.run();
        
        Throwable exception = catchThrowable(() -> commandTask.run());
        
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Already started...");
    }
}
