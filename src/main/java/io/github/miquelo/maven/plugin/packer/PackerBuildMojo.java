package io.github.miquelo.maven.plugin.packer;

import static io.github.miquelo.tools.packer.PackerOutputMessage.DATA_UI_ERROR;
import static io.github.miquelo.tools.packer.PackerOutputMessage.DATA_UI_MESSAGE;
import static io.github.miquelo.tools.packer.PackerOutputMessage.DATA_UI_SAY;
import static io.github.miquelo.tools.packer.PackerOutputMessage.TYPE_UI;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import io.github.miquelo.tools.packer.PackerCommandException;
import io.github.miquelo.tools.packer.PackerCommandFailureException;
import io.github.miquelo.tools.packer.PackerOutputMessage;
import io.github.miquelo.tools.packer.PackerTool;

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
     * Template path relative to source directory.
     */
    @Parameter(
        defaultValue="template.json"
    )
    private String templatePath;
    
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
        templatePath = null;
        vars = null;
        force = false;
        changesNeeded = false;
        invalidateOnFailure = false;
    }
    
    @Override
    public void execute(PackerTool packerTool)
    throws MojoFailureException, MojoExecutionException
    {
        try
        {
            packerTool.build(
                MessageDigest::getInstance,
                new File(fileSet.getDirectory()),
                new File(fileSet.getOutputDirectory()),
                Stream.of(fileSetManager.getIncludedFiles(fileSet))
                    .collect(toSet()),
                changesNeeded,
                invalidateOnFailure,
                templatePath,
                force,
                Optional.ofNullable(vars)
                    .map(Properties::entrySet)
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .collect(toMap(
                        e -> e.getKey().toString(),
                        Entry::getValue)))
                .execute();
        }
        catch (PackerCommandException exception)
        {
            throw new MojoExecutionException("Packer error", exception);
        }
        catch (PackerCommandFailureException exception)
        {
            throw new MojoFailureException("Packer failure", exception);
        }
        catch (InterruptedException exception)
        {
            // Nothing to be done...
        }
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
                forEachLine(data[1], line -> getLog().info(line));
                break;
                case DATA_UI_ERROR:
                forEachLine(data[1], line -> getLog().error(line));
                break;
                default:
                forEachLine(data[1], line -> getLog().debug(line));
            }
        }
    }
    
    private static void forEachLine(
        String str,
        Consumer<String> lineConsumer)
    {
        Stream.of(str.split("\\\\n"))
            .forEach(line -> lineConsumer.accept(line));
    }
}
