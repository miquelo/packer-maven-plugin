package io.github.miquelo.maven.plugin.packer;

import static java.lang.String.format;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

/**
 * Run a Packer build command.
 */
@Mojo(
    name="build"
)
public class PackerBuildMojo
extends AbstractMojo
{
    private static final Map<String, BiConsumer<Log, String>>
        LOG_BI_CONSUMER_MAP = Stream.of(
            new SimpleEntry<String, BiConsumer<Log, String>>(
                "info",
                Log::info),
            new SimpleEntry<String, BiConsumer<Log, String>>(
                "say",
                Log::info),
            new SimpleEntry<String, BiConsumer<Log, String>>(
                "message",
                Log::info),
            new SimpleEntry<String, BiConsumer<Log, String>>(
                "warn",
                Log::warn),
            new SimpleEntry<String, BiConsumer<Log, String>>(
                "error",
                Log::error))
            .collect(toMap(Entry::getKey, Entry::getValue));
    
    private final FileSetManager fileSetManager;
    
    @Parameter(
        required=true,
        readonly=true,
        defaultValue="${project}"
    )
    private MavenProject project;
    
    /**
     * File set used on this build.
     */
    @Parameter(
        required=true
    )
    private FileSet fileSet;
    
    /**
     * Template file name located on root of file set.
     */
    @Parameter(
        defaultValue="template.json"
    )
    private String templateFileName;
    
    /**
     * Variables for template.
     */
    @Parameter
    private Properties vars;
    
    /**
     * Whether build must be executed even output directory is created.
     */
    @Parameter(
        defaultValue="false"
    )
    private boolean force;
    
    /**
     * Whether this execution must be skipped.
     */
    @Parameter(
        defaultValue="false"
    )
    private boolean skip;
    
    /**
     * Whether must be some change for this build in order to be executed.
     */
    @Parameter(
        defaultValue="true"
    )
    private boolean changesNeeded;
    
    /**
     * Whether working files must be invalidated when build fails.
     */
    @Parameter(
        defaultValue="true"
    )
    private boolean invalidateOnFailure;
    
    public PackerBuildMojo()
    {
        fileSetManager = new FileSetManager(getLog());
        project = null;
        fileSet = null;
        templateFileName = null;
        vars = null;
        force = false;
        skip = false;
        changesNeeded = false;
        invalidateOnFailure = false;
    }
    
    @Override
    public void execute()
    throws MojoFailureException, MojoExecutionException
    {
        if (skip)
            getLog().info("Execution skipped...");
        else
            execute(new PackerBuildSourceFiles(
                getLog(),
                new File(fileSet.getDirectory()),
                new File(fileSet.getOutputDirectory()),
                Stream.of(fileSetManager.getIncludedFiles(fileSet))
                    .collect(toSet())));
    }
    
    private void execute(PackerBuildSourceFiles sourceFiles)
    throws MojoFailureException, MojoExecutionException
    {
        if (sourceFiles.hasChanges())
        {
            sourceFiles.updateAll();
            executeBuild(sourceFiles);
        }
        else if (changesNeeded)
            getLog().info("There is not any change. Ignoring...");
        else
            executeBuild(sourceFiles);
    }
    
    private void executeBuild(PackerBuildSourceFiles sourceFiles)
    throws MojoFailureException, MojoExecutionException
    {
        if (!toolBuild(
            sourceFiles.getWorkingDir(),
            templateFileName,
            Optional.ofNullable(vars)
                .orElseGet(Properties::new)))
        {
            if (invalidateOnFailure)
                sourceFiles.invalidateWorkingDir();
            throw new MojoFailureException("Packer build has failed");
        }
    }
    
    private boolean toolBuild(
        File workingDir,
        String templateFileName,
        Properties vars)
    throws MojoFailureException, MojoExecutionException
    {
        return toolBuild(
            workingDir,
            Stream.of(
                Stream.of("packer"),
                Stream.of("build"),
                Stream.of("-machine-readable"),
                vars.entrySet().stream()
                    .flatMap(entry -> Stream.of(
                        "-var",
                        format("'%s=%s'", entry.getKey(), entry.getValue()))),
                Stream.of("-force")
                    .filter(arg -> force),
                Stream.of(templateFileName))
                .flatMap(identity())
                .collect(toList()));
    }
    
    private boolean toolBuild(File workingDir, List<String> args)
    throws MojoFailureException, MojoExecutionException 
    {
        try
        {
            getLog().debug(format(
                "Going to run [%s] inside %s",
                args.stream()
                    .collect(joining(" ")),
                workingDir.getAbsolutePath()));
            
            Process process = new ProcessBuilder(args)
                .directory(workingDir)
                .start();
            
            ExecutorService logStreamService = Executors.newFixedThreadPool(2);
            logStreamService.submit(() -> logStream(process.getInputStream()));
            
            process.waitFor(1, TimeUnit.HOURS);
            return process.exitValue() == 0;
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Could not execute packer",
                exception);
        }
        catch (InterruptedException exception)
        {
            throw new MojoExecutionException(
                "Packer execution was interrupted",
                exception);
        }
    }
    
    private void logStream(InputStream input)
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            input)))
        {
            String line = reader.readLine();
            while (line != null)
            {
                String[] parts = line.split(",");
                Stream.of(parts.length < 5 ? "" : parts[4])
                    .flatMap(part -> Stream.of(part.split("\\\\n")))
                    .forEach(msg -> Optional.ofNullable(
                        LOG_BI_CONSUMER_MAP.get(parts[3]))
                        .orElse(Log::debug)
                        .accept(getLog(), msg));
                line = reader.readLine();
            }
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException(exception);
        }
    }
}
