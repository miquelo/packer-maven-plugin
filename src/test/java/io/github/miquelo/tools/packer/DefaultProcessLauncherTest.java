package io.github.miquelo.tools.packer;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultProcessLauncherTest
{
    private static final List<String> ANY_COMMAND = emptyList();
    
    private static final List<String> ECHO_COMMAND = singletonList("echo");
    
    private static final String SOME_NAME = "some-name";
    private static final List<Object> SOME_ARGS = Stream.of(
        "first-arg",
        "second-arg")
        .collect(toList());

    private static final String SOME_ECHO_OUTPUT = "some-name "
        + "first-arg "
        + "second-arg";


    public DefaultProcessLauncherTest()
    {
    }
    
    @Test
    public void isAlwaysCompatible()
    {
        ProcessLauncher launcher = new DefaultProcessLauncher(ANY_COMMAND);
        
        boolean compatible = launcher.compatible();
        
        assertThat(compatible).isTrue();
    }
    
    @Test
    public void launchCommand(
        @TempDir
        File anyWorkingDir)
    throws Exception
    {
        ProcessLauncher launcher = new DefaultProcessLauncher(ECHO_COMMAND);
        
        String output = outputRead(launcher.launch(
            anyWorkingDir,
            SOME_NAME,
            SOME_ARGS));
        
        assertThat(output).isEqualTo(SOME_ECHO_OUTPUT);
    }

    private static String outputRead(Process process)
    throws IOException
    {
        return new BufferedReader(new InputStreamReader(
            process.getInputStream()))
                .readLine();
    }
}
