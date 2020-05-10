package io.github.miquelo.tools.packer;

import static java.time.Instant.ofEpochMilli;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PackerExecutionTest
{
    private static final boolean IS_COMPATIBLE = true;
    private static final boolean IS_NOT_COMPATIBLE = false;
    
    private static final boolean IS_RELEVANT = true;
    private static final boolean IS_NOT_RELEVANT = false;
    
    private static final boolean IS_ALIVE = true;
    private static final boolean IS_NOT_ALIVE = false;
    
    private static final boolean WITH_TIMEOUT = false;
    private static final boolean WITHOUT_TIMEOUT = true;
    
    private static final String ANY_NAME = "any-name";
    private static final List<Object> ANY_ARGS = emptyList();
    
    private static final String SOME_NAME = "some-name";
    private static final List<Object> SOME_ARGS = emptyList();
    
    private static final byte[] WELL_FORMED_MESSAGE_INPUT_STREAM = new byte[] {
        0x32, 0x2c, 0x77, 0x65, 0x6c, 0x6c, 0x2d, 0x66,
        0x6f, 0x72, 0x6d, 0x65, 0x64, 0x2d, 0x74, 0x61,
        0x72, 0x67, 0x65, 0x74, 0x2c, 0x77, 0x65, 0x6c,
        0x6c, 0x2d, 0x66, 0x6f, 0x72, 0x6d, 0x65, 0x64,
        0x2d, 0x74, 0x79, 0x70, 0x65, 0x2c, 0x77, 0x65,
        0x6c, 0x6c, 0x2d, 0x66, 0x6f, 0x72, 0x6d, 0x65,
        0x64, 0x2d, 0x64, 0x61, 0x74, 0x61, 0x2d, 0x25,
        0x21, 0x28, 0x50, 0x41, 0x43, 0x4b, 0x45, 0x52,
        0x5f, 0x43, 0x4f, 0x4d, 0x4d, 0x41, 0x29, 0x5c,
        0x6e, 0x5c, 0x72, 0x2c
    };
    
    private static final byte[] MALFORMED_MESSAGE_INPUT_STREAM = {
        0x0d
    };
    
    private static final Long SOME_TIMEOUT = 1000L;
    private static final TimeUnit SOME_UNIT = SECONDS;
    
    private static final int SOME_EXIT_VALUE = 10;
    
    private static final Instant WELL_FORMED_TIMESTAMP = ofEpochMilli(2000);
    private static final String WELL_FORMED_TARGET = "well-formed-target";
    private static final String WELL_FORMED_TYPE = "well-formed-type";
    private static final String WELL_FORMED_DATA_PART =
        "well-formed-data-,\n\r";
    private static final String EMPTY_DATA_PART = "";

    private Executor messageConsumerExecutor;
    
    public PackerExecutionTest()
    {
    }
    
    @BeforeEach
    public void setUp()
    {
        messageConsumerExecutor = Runnable::run;
    }
    
    @Test
    public void waitForExitValueWithoutTimeout(
        @Mock
        Consumer<PackerOutputMessage> messageConsumer,
        @Mock
        ProcessLauncher processLauncher,
        @Mock
        Process process,
        @Mock
        TimeoutHandler timeoutHandler,
        @TempDir
        File workingDir)
    throws Exception
    {
        when(processLauncher.compatible())
            .thenReturn(IS_COMPATIBLE);
        when(processLauncher.launch(workingDir, SOME_NAME, SOME_ARGS))
            .thenReturn(process);
        when(process.getInputStream())
            .thenReturn(new ByteArrayInputStream(
                WELL_FORMED_MESSAGE_INPUT_STREAM));
        when(process.exitValue())
            .thenReturn(SOME_EXIT_VALUE);
        when(process.waitFor(SOME_TIMEOUT, SOME_UNIT))
            .thenReturn(WITHOUT_TIMEOUT);
        when(timeoutHandler.isRelevant())
            .thenReturn(IS_RELEVANT);
        when(timeoutHandler.getTimeout())
            .thenReturn(SOME_TIMEOUT);
        when(timeoutHandler.getUnit())
            .thenReturn(SOME_UNIT);
        PackerExecution execution = new PackerExecution(
            messageConsumer,
            workingDir,
            SOME_NAME,
            SOME_ARGS,
            new ProcessLauncher[] {
                processLauncher
            },
            messageConsumerExecutor);
        
        int errorCode = execution.errorCode(timeoutHandler);
        
        assertThat(errorCode).isEqualTo(SOME_EXIT_VALUE);
        verify(messageConsumer).accept(argThat(message ->
            message.getTimestamp().equals(WELL_FORMED_TIMESTAMP) &&
            message.getTarget().equals(Optional.of(WELL_FORMED_TARGET)) &&
            message.getType().equals(WELL_FORMED_TYPE) &&
            message.getData()[0].equals(WELL_FORMED_DATA_PART) &&
            message.getData()[1].equals(EMPTY_DATA_PART)));
    }
    
    @Test
    public void waitForExitValueWithTimeout(
        @Mock
        Consumer<PackerOutputMessage> messageConsumer,
        @Mock
        ProcessLauncher processLauncher,
        @Mock
        Process process,
        @Mock
        TimeoutHandler timeoutHandler,
        @TempDir
        File workingDir)
    throws Exception
    {
        when(processLauncher.compatible())
            .thenReturn(IS_COMPATIBLE);
        when(processLauncher.launch(workingDir, SOME_NAME, SOME_ARGS))
            .thenReturn(process);
        when(process.getInputStream())
            .thenReturn(new ByteArrayInputStream(
                WELL_FORMED_MESSAGE_INPUT_STREAM));
        when(process.waitFor(SOME_TIMEOUT, SOME_UNIT))
            .thenReturn(WITH_TIMEOUT);
        when(timeoutHandler.isRelevant())
            .thenReturn(IS_RELEVANT);
        when(timeoutHandler.getTimeout())
            .thenReturn(SOME_TIMEOUT);
        when(timeoutHandler.getUnit())
            .thenReturn(SOME_UNIT);
        PackerExecution execution = new PackerExecution(
            messageConsumer,
            workingDir,
            SOME_NAME,
            SOME_ARGS,
            new ProcessLauncher[] {
                processLauncher
            },
            messageConsumerExecutor);
        
        Throwable exception = catchThrowable(() -> execution.errorCode(
            timeoutHandler));
        
        assertThat(exception).isInstanceOf(TimeoutException.class);
        verify(process).destroy();
        verify(messageConsumer).accept(argThat(message ->
            message.getTimestamp().equals(WELL_FORMED_TIMESTAMP) &&
            message.getTarget().equals(Optional.of(WELL_FORMED_TARGET)) &&
            message.getType().equals(WELL_FORMED_TYPE) &&
            message.getData()[0].equals(WELL_FORMED_DATA_PART) &&
            message.getData()[1].equals(EMPTY_DATA_PART)));
    }
    
    @Test
    public void waitForExitValue(
        @Mock
        Consumer<PackerOutputMessage> messageConsumer,
        @Mock
        ProcessLauncher processLauncher,
        @Mock
        Process process,
        @Mock
        TimeoutHandler timeoutHandler,
        @TempDir
        File workingDir)
    throws Exception
    {
        when(processLauncher.compatible())
            .thenReturn(IS_COMPATIBLE);
        when(processLauncher.launch(workingDir, SOME_NAME, SOME_ARGS))
            .thenReturn(process);
        when(process.getInputStream())
            .thenReturn(new ByteArrayInputStream(
                WELL_FORMED_MESSAGE_INPUT_STREAM));
        when(process.waitFor())
            .thenReturn(SOME_EXIT_VALUE);
        when(timeoutHandler.isRelevant())
            .thenReturn(IS_NOT_RELEVANT);
        PackerExecution execution = new PackerExecution(
            messageConsumer,
            workingDir,
            SOME_NAME,
            SOME_ARGS,
            new ProcessLauncher[] {
                processLauncher
            },
            messageConsumerExecutor);
        
        int errorCode = execution.errorCode(timeoutHandler);
        
        assertThat(errorCode).isEqualTo(SOME_EXIT_VALUE);
        verify(process, never()).destroy();
        verify(messageConsumer).accept(argThat(message ->
            message.getTimestamp().equals(WELL_FORMED_TIMESTAMP) &&
            message.getTarget().equals(Optional.of(WELL_FORMED_TARGET)) &&
            message.getType().equals(WELL_FORMED_TYPE) &&
            message.getData()[0].equals(WELL_FORMED_DATA_PART) &&
            message.getData()[1].equals(EMPTY_DATA_PART)));
    }
    
    @Test
    public void successfulInterrupt(
        @Mock
        Consumer<PackerOutputMessage> messageConsumer,
        @Mock
        ProcessLauncher processLauncher,
        @Mock
        Process process,
        @Mock
        TimeoutHandler anyTimeoutHandler,
        @TempDir
        File workingDir)
    throws Exception
    {
        when(processLauncher.compatible())
            .thenReturn(IS_COMPATIBLE);
        when(processLauncher.launch(workingDir, SOME_NAME, SOME_ARGS))
            .thenReturn(process);
        when(process.getInputStream())
            .thenReturn(new ByteArrayInputStream(
                WELL_FORMED_MESSAGE_INPUT_STREAM));
        when(process.isAlive())
            .thenReturn(IS_NOT_ALIVE);
        PackerExecution execution = new PackerExecution(
            messageConsumer,
            workingDir,
            SOME_NAME,
            SOME_ARGS,
            new ProcessLauncher[] {
                processLauncher
            },
            messageConsumerExecutor);
        
        boolean successful = execution.interrupt();
        
        assertThat(successful).isTrue();
        verify(process).destroy();
    }
    
    @Test
    public void unsuccessfulInterrupt(
        @Mock
        Consumer<PackerOutputMessage> messageConsumer,
        @Mock
        ProcessLauncher processLauncher,
        @Mock
        Process process,
        @Mock
        TimeoutHandler anyTimeoutHandler,
        @TempDir
        File workingDir)
    throws Exception
    {
        when(processLauncher.compatible())
            .thenReturn(IS_COMPATIBLE);
        when(processLauncher.launch(workingDir, SOME_NAME, SOME_ARGS))
            .thenReturn(process);
        when(process.getInputStream())
            .thenReturn(new ByteArrayInputStream(
                WELL_FORMED_MESSAGE_INPUT_STREAM));
        when(process.isAlive())
            .thenReturn(IS_ALIVE);
        PackerExecution execution = new PackerExecution(
            messageConsumer,
            workingDir,
            SOME_NAME,
            SOME_ARGS,
            new ProcessLauncher[] {
                processLauncher
            },
            messageConsumerExecutor);
        
        boolean successful = execution.interrupt();
        
        assertThat(successful).isFalse();
        verify(process).destroy();
    }
    
    @Test
    public void ignoreMalformedOutput(
        @Mock
        Consumer<PackerOutputMessage> messageConsumer,
        @Mock
        ProcessLauncher processLauncher,
        @Mock
        Process process,
        @Mock
        TimeoutHandler anyTimeoutHandler,
        @TempDir
        File workingDir)
    throws Exception
    {
        when(processLauncher.compatible())
            .thenReturn(IS_COMPATIBLE);
        when(processLauncher.launch(workingDir, SOME_NAME, SOME_ARGS))
            .thenReturn(process);
        when(process.getInputStream())
            .thenReturn(new ByteArrayInputStream(
                MALFORMED_MESSAGE_INPUT_STREAM));
        PackerExecution execution = new PackerExecution(
            messageConsumer,
            workingDir,
            SOME_NAME,
            SOME_ARGS,
            new ProcessLauncher[] {
                processLauncher
            },
            messageConsumerExecutor);
        
        execution.errorCode(anyTimeoutHandler);
        
        verify(messageConsumer, never()).accept(any());
    }
    
    @Test
    public void failWhenThereAreNoCompatibleLaunchers(
        @Mock
        Consumer<PackerOutputMessage> anyMessageConsumer,
        @Mock
        ProcessLauncher incompatibleProcessLauncher,
        @TempDir
        File workingDir)
    throws Exception
    {
        when(incompatibleProcessLauncher.compatible())
            .thenReturn(IS_NOT_COMPATIBLE);
        
        Throwable exception = catchThrowable(() -> new PackerExecution(
            anyMessageConsumer,
            workingDir,
            ANY_NAME,
            ANY_ARGS,
            new ProcessLauncher[] {
                incompatibleProcessLauncher
            },
            messageConsumerExecutor));
        
        assertThat(exception).isInstanceOf(IllegalArgumentException.class);
    }
}
