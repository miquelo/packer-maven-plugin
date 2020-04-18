package io.github.miquelo.tools.packer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

@FunctionalInterface
public interface PackerCommandLauncher
{
    int launch(
        Consumer<PackerOutputMessage> messageConsumer,
        File workingDir,
        String name,
        List<String> args)
    throws IOException, InterruptedException, ExecutionException;
}
