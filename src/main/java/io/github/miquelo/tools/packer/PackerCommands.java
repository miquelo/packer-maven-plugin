package io.github.miquelo.tools.packer;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * Built-in Packer commands.
 * 
 * @see PackerCommand
 */
public final class PackerCommands
{
    private PackerCommands()
    {
    }
    
    /**
     * Create a Packer {@code build} command.
     * 
     * @param digestCreator
     *     Message digest used to obtain source files hash.
     * @param sourceDir
     *     Directory where source files are located.
     * @param workingDir
     *     Directory where build command will work at.
     * @param sourceFilePathSet
     *     Selected source files.
     * @param changesNeeded
     *     Whether changes on source files are needed for this command to don't
     *     be ignored.
     * @param invalidateOnFailure
     *     Whether files will be invalidated if command executions fails.
     * @param templatePath
     *     Source directory relative path of template used for this build.
     * @param only
     *     Set of builder names that must be taken into account. Empty for all.
     * @param except
     *     Set of builder names that must be ignored.
     * @param force
     *     Whether execution should overwrite Packer output directory.
     * @param vars
     *     Variables used for this build.
     * @param varFiles
     *     Variable files used for this build.
     *     
     * @return
     *     The created Packer build command.
     */
    public static PackerCommand buildCommand(
        MessageDigestCreator digestCreator,
        File sourceDir,
        File workingDir,
        Set<String> sourceFilePathSet,
        boolean changesNeeded,
        boolean invalidateOnFailure,
        String templatePath,
        boolean force,
        Set<String> only,
        Set<String> except,
        Map<String, Object> vars,
        Set<String> varFiles)
    {
        return new PackerBuildCommand(
            digestCreator,
            sourceDir, workingDir,
            sourceFilePathSet,
            changesNeeded,
            invalidateOnFailure,
            templatePath,
            force,
            only,
            except,
            vars,
            varFiles);
    }
}
