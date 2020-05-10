package io.github.miquelo.tools.packer;

import static io.github.miquelo.tools.packer.UnixProcessWrapper.execute;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class UnixProcessLauncherTest
{
    private static final int SUCCESS_EXIT_CODE = 0;
    
    private static final List<Object> ANY_ARGS = emptyList();
    
    private static final String SUCCESSFULLY_LAUNCH_NAME =
        "successfully-launch";
    private static final String INFINITE_SLEEP_NAME = "infinite-sleep";
    private static final String FALSE_NAME = "false";
    private static final String STOP_NAME = "stop";
    
    private static final List<Object> SOME_ARGS = Stream.of(
        "first-arg",
        "second-arg")
        .collect(toList());

    private static final String SUCCESS_OUTPUT = "success";

    private static final String NOT_FOUND_COMMAND = "---not-found";
    
    public UnixProcessLauncherTest()
    {
    }
    
    @Test
    public void isAlwaysCompatible()
    {
        ProcessLauncher launcher = new UnixProcessLauncher();
        
        boolean compatible = launcher.compatible();
        
        assertThat(compatible).isTrue();
    }
    
    @Test
    public void successfullyLaunch(
        @TempDir
        File anyWorkingDir)
    throws Exception
    {
        ProcessLauncher launcher = new UnixProcessLauncher();
        
        Process process = launcher.launch(
            anyWorkingDir,
            SUCCESSFULLY_LAUNCH_NAME,
            SOME_ARGS);
        int exitCode = process.waitFor();
        String output = outputRead(process);
        
        assertThat(exitCode).isEqualTo(SUCCESS_EXIT_CODE);
        assertThat(output).isEqualTo(SUCCESS_OUTPUT);
    }
    
    @Test
    public void isAliveBySystemProcess(
        @TempDir
        File anyWorkingDir)
    throws Exception
    {
        ProcessLauncher launcher = new UnixProcessLauncher();
        Process process = launcher.launch(
            anyWorkingDir,
            INFINITE_SLEEP_NAME,
            ANY_ARGS);
        
        boolean alive = process.isAlive();
        process.destroy();
        
        assertThat(alive).isTrue();
    }
    
    @Test
    public void isNotAliveBySystemProcess(
        @TempDir
        File anyWorkingDir)
    throws Exception
    {
        ProcessLauncher launcher = new UnixProcessLauncher();
        Process process = launcher.launch(
            anyWorkingDir,
            INFINITE_SLEEP_NAME,
            ANY_ARGS);
        process.destroy();
        
        boolean alive = process.isAlive();
        
        assertThat(alive).isFalse();
    }
    
    @Test
    public void isNotAliveWhenIsStoppedProcess(
        @TempDir
        File anyWorkingDir)
    throws Exception
    {
        ProcessLauncher launcher = new UnixProcessLauncher();
        Process process = launcher.launch(
            anyWorkingDir,
            STOP_NAME,
            ANY_ARGS);
        
        boolean alive = process.isAlive();
        process.destroy();
        
        assertThat(alive).isFalse();
    }
    
    @Test
    public void hasAnErrorStream(
        @TempDir
        File anyWorkingDir)
    throws Exception
    {
        ProcessLauncher launcher = new UnixProcessLauncher();
        Process process = launcher.launch(
            anyWorkingDir,
            FALSE_NAME,
            ANY_ARGS);
        process.waitFor();
        
        InputStream errorStream = process.getErrorStream();
        
        assertThat(errorStream).isNotNull();
    }
    
    @Test
    public void hasAnOutputStream(
        @TempDir
        File anyWorkingDir)
    throws Exception
    {
        ProcessLauncher launcher = new UnixProcessLauncher();
        Process process = launcher.launch(
            anyWorkingDir,
            FALSE_NAME,
            ANY_ARGS);
        process.waitFor();
        
        OutputStream outputStream = process.getOutputStream();
        
        assertThat(outputStream).isNotNull();
    }
    
    @Test
    public void hasAccessToItsExitValue(
        @TempDir
        File anyWorkingDir)
    throws Exception
    {
        ProcessLauncher launcher = new UnixProcessLauncher();
        Process process = launcher.launch(
            anyWorkingDir,
            FALSE_NAME,
            ANY_ARGS);
        process.waitFor();
        
        int exitValue = process.exitValue();
        
        assertThat(exitValue).isZero();
    }
    
    @Test
    public void executeCommandNotFound()
    {
        Throwable exception = catchThrowable(() -> execute(NOT_FOUND_COMMAND));
        
        assertThat(exception).isInstanceOf(UncheckedIOException.class);
    }
    
    private static String outputRead(Process process)
    throws IOException
    {
        return new BufferedReader(new InputStreamReader(
            process.getInputStream()))
                .readLine();
    }
}
