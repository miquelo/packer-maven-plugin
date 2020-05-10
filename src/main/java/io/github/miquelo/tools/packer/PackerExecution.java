package io.github.miquelo.tools.packer;

import static java.lang.Long.parseLong;
import static java.time.Instant.ofEpochMilli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

class PackerExecution
{
    public static final ProcessLauncher[] SUPPORTED_LAUNCHERS = {
        new UnixProcessLauncher(),
        new DefaultProcessLauncher()
    };
    
    private final Process process;
    private final PackerOutputReaderTask outputReaderTask;
    
    PackerExecution(
        Consumer<PackerOutputMessage> messageConsumer,
        File workingDir,
        String name,
        List<Object> args,
        ProcessLauncher[] launchers,
        Executor messageConsumerExecutor)
    throws IOException, InterruptedException
    {
        process = Stream.of(launchers)
            .filter(ProcessLauncher::compatible)
            .findAny()
            .orElseThrow(IllegalArgumentException::new)
            .launch(workingDir, name, args);
        outputReaderTask = new PackerOutputReaderTask(
            messageConsumer,
            process.getInputStream());
        messageConsumerExecutor.execute(outputReaderTask);
    }
    
    public int errorCode(TimeoutHandler timeoutHandler)
    throws InterruptedException, TimeoutException
    {
        if (timeoutHandler.isRelevant())
        {
            if (process.waitFor(
                timeoutHandler.getTimeout(),
                timeoutHandler.getUnit()))
            {
                outputReaderTask.awaitTermination();
                return process.exitValue();
            }
            process.destroy();
            outputReaderTask.awaitTermination();
            throw new TimeoutException();
        }
        int code = process.waitFor();
        outputReaderTask.awaitTermination();
        return code;
    }
    
    public boolean interrupt()
    {
        process.destroy();
        outputReaderTask.awaitTermination();
        return !process.isAlive();
    }
}

class PackerOutputReaderTask
implements Runnable
{
    private final Consumer<PackerOutputMessage> messageConsumer;
    private final BufferedReader reader;
    private final Lock terminationLock;
    
    PackerOutputReaderTask(
        Consumer<PackerOutputMessage> messageConsumer,
        InputStream input)
    {
        this.messageConsumer = messageConsumer;
        reader = new BufferedReader(new InputStreamReader(input));
        terminationLock = new ReentrantLock();
    }

    @Override
    public void run()
    {
        try
        {
            terminationLock.lock();
            
            reader.lines()
                .map(PackerOutputReaderTask::messageParts)
                .forEach(this::messageAccept);
        }
        finally
        {
            terminationLock.unlock();
        }
    }
    
    void awaitTermination()
    {
        try
        {
            terminationLock.lock();
        }
        finally
        {
            terminationLock.unlock();
        }
    }
    
    private void messageAccept(String[] parts)
    {
        try
        {
            messageConsumer.accept(new PackerOutputMessageImpl(
                ofEpochMilli(parseLong(parts[0]) * 1000L),
                parts[1],
                parts[2],
                formatData(parts, 3)));
        }
        catch (RuntimeException exception)
        {
            // Ignore malformed output...
        }
    }
    
    private static String[] messageParts(String message)
    {
        return message.split(",", -1);
    }
    
    private static String[] formatData(String[] parts, int from)
    {
        String[] data = new String[parts.length - from];
        for (int i = from; i < parts.length; ++i)
            data[i - from] = formatDataPart(parts[i]);
        return data;
    }
    
    private static String formatDataPart(String part)
    {
        return part.replace("%!(PACKER_COMMA)", ",")
            .replace("\\n", "\n")
            .replace("\\r", "\r");
    }
}

interface PackerOutputReaderTaskFactory
{
    PackerOutputReaderTask getTask(
        Consumer<PackerOutputMessage> messageConsumer,
        InputStream input);
}
