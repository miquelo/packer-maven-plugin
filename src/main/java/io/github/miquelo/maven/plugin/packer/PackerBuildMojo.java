package io.github.miquelo.maven.plugin.packer;

import static io.github.miquelo.tools.packer.PackerOutputMessage.DATA_UI_ERROR;
import static io.github.miquelo.tools.packer.PackerOutputMessage
    .DATA_UI_MESSAGE;
import static io.github.miquelo.tools.packer.PackerOutputMessage.DATA_UI_SAY;
import static io.github.miquelo.tools.packer.PackerOutputMessage.TYPE_UI;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import io.github.miquelo.tools.packer.PackerCommand;
import io.github.miquelo.tools.packer.PackerOutputMessage;
import io.github.miquelo.tools.packer.commands.PackerBuildCommand;

/**
 * Run a Packer build command.
 */
@Mojo(
    name="build"
)
public final class PackerBuildMojo
extends AbstractPackerMojo
{
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
    
    /**
     * Template path relative to source directory.
     */
    @Parameter(
        defaultValue="template.json"
    )
    private String templatePath;
    
    /**
     * Whether build must be executed even output directory is created.
     */
    @Parameter(
        defaultValue="false"
    )
    private boolean force;
    
    /**
     * Comma-separated builder names that must be taken into account. Empty for
     * all.
     */
    @Parameter
    private String only;
    
    /**
     * Comma-separated builder names that must be ignored.
     */
    @Parameter
    private String except;
    
    /**
     * Variables for template.
     */
    @Parameter
    private Properties vars;
    
    /**
     * Variable files for template. Relative to source directory.
     */
    @Parameter
    private Set<String> varFiles;
    
    public PackerBuildMojo()
    {
        fileSetManager = new FileSetManager(getLog());
        project = null;
        fileSet = null;
        changesNeeded = false;
        invalidateOnFailure = false;
        templatePath = null;
        force = false;
        only = null;
        except = null;
        vars = null;
        varFiles = null;
    }
    
    @Override
    protected PackerCommand command()
    {
        return new PackerBuildCommand(
            MessageDigest::getInstance,
            new File(fileSet.getDirectory()),
            new File(fileSet.getOutputDirectory()),
            Stream.of(fileSetManager.getIncludedFiles(fileSet))
                .collect(toSet()),
            changesNeeded,
            invalidateOnFailure,
            templatePath,
            force,
            Optional.ofNullable(only)
                .map(str -> str.split(","))
                .map(Stream::of)
                .orElseGet(Stream::empty)
                .map(String::trim)
                .filter(str -> !str.isEmpty())
                .collect(toSet()),
            Optional.ofNullable(except)
                .map(str -> str.split(","))
                .map(Stream::of)
                .orElseGet(Stream::empty)
                .map(String::trim)
                .filter(str -> !str.isEmpty())
                .collect(toSet()),
            Optional.ofNullable(vars)
                .map(Properties::entrySet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .collect(toMap(
                    e -> e.getKey().toString(),
                    Entry::getValue)),
            Optional.ofNullable(varFiles)
                .orElseGet(Collections::emptySet));
    }

    @Override
    protected void acceptOutputMessage(PackerOutputMessage message)
    {
        if (TYPE_UI.equals(message.getType()))
        {
            String[] data = message.getData();
            switch (data[0])
            {
                case DATA_UI_MESSAGE:
                case DATA_UI_SAY:
                forEachLine(data[1], this::logInfo);
                break;
                case DATA_UI_ERROR:
                forEachLine(data[1], this::logError);
                break;
                default:
                forEachLine(data[1], this::logDebug);
            }
        }
    }
    
    private void logInfo(String msg)
    {
        getLog().info(msg);
    }
    
    private void logError(String msg)
    {
        getLog().error(msg);
    }
    
    private void logDebug(String msg)
    {
        getLog().debug(msg);
    }
    
    private static void forEachLine(
        String str,
        Consumer<String> lineConsumer)
    {
        Stream.of(str.split("\n"))
            .forEach(line -> lineConsumer.accept(line));
    }
}
