package io.github.miquelo.tools.packer;

import static java.lang.Long.parseLong;
import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.time.Instant.ofEpochMilli;
import static java.util.Arrays.copyOfRange;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;

class PackerExecution
{
    private static final long MESSAGE_CONSUMER_SERVICE_WAIT_TIMEOUT = 2;
    private static final TimeUnit MESSAGE_CONSUMER_SERVICE_WAIT_UNIT = SECONDS;
    
    private final ExecutorService messageConsumerService;
    private final Process process;
    
    PackerExecution(
        Consumer<PackerOutputMessage> messageConsumer,
        File workingDir,
        String name,
        List<Object> args,
        long waitTimeout,
        TimeUnit waitUnit)
    throws IOException, InterruptedException
    {
        messageConsumerService = newFixedThreadPool(1);
        process = new ProcessBuilder(concat(
            Stream.of("packer", "-machine-readable", name),
            args.stream())
                .map(Object::toString)
                .collect(toList()))
            .directory(workingDir)
            .redirectOutput(INHERIT)
            .start();
        messageConsumerService.submit(() -> messageConsumerReader(
            messageConsumer,
            process.getInputStream()));
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
                awaitMessageConsumerServiceTermination();
                return process.exitValue();
            }
            process.destroy();
            awaitMessageConsumerServiceTermination();
            throw new TimeoutException();
        }
        int code = process.waitFor();
        awaitMessageConsumerServiceTermination();
        return code;
    }
    
    public boolean interrupt()
    {
        process.destroy();
        return !process.isAlive();
    }
    
    private void awaitMessageConsumerServiceTermination()
    throws InterruptedException
    {
        messageConsumerService.awaitTermination(
            MESSAGE_CONSUMER_SERVICE_WAIT_TIMEOUT,
            MESSAGE_CONSUMER_SERVICE_WAIT_UNIT);
    }
    
    private static void messageConsumerReader(
        Consumer<PackerOutputMessage> messageConsumer,
        InputStream input)
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            input)))
        {
            String line = reader.readLine();
            while (line != null)
            {
                messageConsumer.accept(parseMessage(line));
                line = reader.readLine();
            }
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException(exception);
        }
    }
    
    private static PackerOutputMessage parseMessage(String line)
    {
        String[] parts = line.split(",", -1);
        return new PackerOutputMessage(
            ofEpochMilli(parseLong(parts[0]) * 1000L),
            parts[1],
            parts[2],
            copyOfRange(parts, 3, parts.length));
  }
}
