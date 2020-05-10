package io.github.miquelo.tools.packer;

import static java.io.File.createTempFile;
import static java.lang.ProcessBuilder.Redirect.PIPE;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

interface ProcessLauncher
{
    boolean compatible();
    
    Process launch(File workingDir, String name, List<Object> args)
    throws IOException;
}

abstract class AbstractProcessLauncher
implements ProcessLauncher
{
    protected AbstractProcessLauncher()
    {
    }
    
    @Override
    public Process launch(File workingDir, String name, List<Object> args)
    throws IOException
    {
        return wrap(new ProcessBuilder(
            concat(
                command(),
                concat(
                    Stream.of(name),
                    args.stream()))
                        .map(Object::toString)
                        .collect(toList()))
            .directory(workingDir)
            .redirectOutput(PIPE)
            .start());
    }
    
    protected abstract Stream<String> command()
    throws IOException;
    
    protected abstract Process wrap(Process process)
    throws IOException;
}

class DefaultProcessLauncher
extends AbstractProcessLauncher
{
    private static final List<String> COMMAND = Stream.of(
        "packer",
        "-machine-readable")
        .collect(toList());
    
    private final List<String> command;
    
    DefaultProcessLauncher()
    {
        this(COMMAND);
    }
    
    DefaultProcessLauncher(List<String> command)
    {
        this.command = command;
    }
    
    @Override
    public boolean compatible()
    {
        return true;
    }

    @Override
    protected Stream<String> command()
    throws IOException
    {
        return command.stream();
    }

    @Override
    protected Process wrap(Process process)
    throws IOException
    {
        return process;
    }
    
}

class UnixProcessLauncher
extends AbstractProcessLauncher
{
    UnixProcessLauncher()
    {
    }
    
    @Override
    public boolean compatible()
    {
        // TODO Detect that is UNIX like system
        return true;
    }

    @Override
    public Stream<String> command()
    throws IOException
    {
        File launcherFile = createTempFile("launcher", ".sh");
        launcherFile.deleteOnExit();
        copy(
            getClass().getResourceAsStream("launcher-unix.sh"),
            launcherFile.toPath(),
            REPLACE_EXISTING);
        launcherFile.setExecutable(true);
        return Stream.of(launcherFile.getAbsolutePath());
    }

    @Override
    public Process wrap(Process process)
    throws IOException
    {
        return new UnixProcessWrapper(process);
    }
}

class UnixProcessWrapper
extends Process
{
    private final Process wrapped;
    private final int pid;
    private final Function<String, Stream<String>> executeFunction;
    
    public UnixProcessWrapper(Process wrapped)
    throws IOException
    {
        this(wrapped, UnixProcessWrapper::execute);
    }
    
    public UnixProcessWrapper(
        Process wrapped,
        Function<String, Stream<String>> executeFunction)
    throws IOException
    {
        this.wrapped = wrapped;
        pid = readPID(wrapped.getInputStream());
        this.executeFunction = executeFunction;
    }
    
    @Override
    public boolean isAlive()
    {
        return isAlive(executeFunction, pid);
    }
    
    @Override
    public InputStream getInputStream()
    {
        return wrapped.getInputStream();
    }
    
    @Override
    public InputStream getErrorStream()
    {
        return wrapped.getErrorStream();
    }

    @Override
    public OutputStream getOutputStream()
    {
        return wrapped.getOutputStream();
    }
    
    @Override
    public int exitValue()
    {
        return wrapped.exitValue();
    }

    @Override
    public int waitFor()
    throws InterruptedException
    {
        return wrapped.waitFor();
    }
    
    @Override
    public void destroy()
    {
        try
        {
            destroy(executeFunction, pid);
        }
        finally
        {
            wrapped.destroy();
        }
    }
    
    static Stream<String> execute(String command)
    {
        try
        {
            return new BufferedReader(new InputStreamReader(getRuntime()
                .exec(command)
                .getInputStream()))
                .lines();
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException(exception);
        }
    }
    
    private static int readPID(InputStream input)
    throws IOException
    {
        int pid = 0;
        int c = input.read();
        while (c != '\n')
        {
            pid *= 10;
            pid += c - '0';
            c = input.read();
        }
        return pid;
        
    }
    
    private static boolean isAlive(
        Function<String, Stream<String>> executeFunction,
        int pid)
    {
        return executeFunction.apply(format("ps -o stat= --pid %d", pid))
            .map(status -> status.charAt(0))
            .anyMatch(statusCode -> Stream.of('D', 'R', 'S')
                .filter(statusCode::equals)
                .count() > 0);
    }
    
    private static void destroy(
        Function<String, Stream<String>> executeFunction,
        int pid)
    {
        executeFunction.apply(format("ps -o pid= --ppid %d", pid))
            .map(String::trim)
            .map(Integer::parseInt)
            .forEach(subprocessPid -> destroy(executeFunction, subprocessPid));
        executeFunction.apply(format("kill %d", pid));
    }
}