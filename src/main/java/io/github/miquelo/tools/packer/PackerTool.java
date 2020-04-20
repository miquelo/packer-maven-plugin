package io.github.miquelo.tools.packer;

import static java.lang.Long.parseLong;
import static java.time.Instant.ofEpochMilli;
import static java.util.Arrays.copyOfRange;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class PackerTool
{
    private final PackerCommandLogger commandLogger;
    private final Consumer<PackerOutputMessage> messageConsumer;
    private final ExecutorService messageConsumerService;
    
    private PackerTool(
        PackerCommandLogger commandLogger,
        Consumer<PackerOutputMessage> messageConsumer)
    {
        this.commandLogger = requireNonNull(commandLogger);
        this.messageConsumer = requireNonNull(messageConsumer);
        messageConsumerService = Executors.newCachedThreadPool();
    }
    
    public PackerCommand build(
        MessageDigestCreator digestCreator,
        File sourceDir,
        File workingDir,
        Set<String> sourceFilePathSet,
        boolean changesNeeded,
        boolean invalidateOnFailure,
        boolean keepCache,
        String templatePath,
        boolean force,
        Map<String, Object> vars)
    throws PackerCommandException
    {
        return new PackerBuildCommand(
            this::launch,
            commandLogger,
            messageConsumer,
            digestCreator,
            sourceDir,
            workingDir,
            sourceFilePathSet,
            changesNeeded,
            invalidateOnFailure,
            keepCache,
            templatePath,
            force,
            vars);
    }
    
    public static PackerTool newInstance(
        PackerCommandLogger commandLogger,
        Consumer<PackerOutputMessage> messageConsumer)
    {
        return new PackerTool(commandLogger, messageConsumer);
    }
    
    private int launch(
        Consumer<PackerOutputMessage> messageConsumer,
        File workingDir,
        String name,
        List<String> args)
    throws IOException, InterruptedException, ExecutionException
    {
        Process process = new ProcessBuilder(concat(
            Stream.of("packer", "-machine-readable", name),
            args.stream())
                .collect(toList()))
            .directory(workingDir)
            .start();
        Future<?> future = messageConsumerService.submit(
            () -> messageConsumerReader(process.getInputStream()));
        process.waitFor();
        future.get();
        return process.exitValue();
    }
    
    private void messageConsumerReader(InputStream input)
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
