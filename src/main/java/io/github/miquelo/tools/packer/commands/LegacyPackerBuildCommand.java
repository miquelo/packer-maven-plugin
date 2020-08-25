package io.github.miquelo.tools.packer.commands;

import static io.github.miquelo.tools.packer.PackerCommandFailureCode
    .FAILURE_ERROR;
import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.delete;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import io.github.miquelo.tools.packer.PackerCommand;
import io.github.miquelo.tools.packer.PackerCommandException;
import io.github.miquelo.tools.packer.PackerCommandFailureCode;
import io.github.miquelo.tools.packer.PackerCommandLogger;
import io.github.miquelo.tools.packer.TimeoutHandler;

/**
 * Packer {@code build} command.
 */
@Deprecated
public class LegacyPackerBuildCommand
implements PackerCommand
{
    private static final String COMMAND_NAME = "build";
    
    private static final String CHECKSUM_FILE_NAME = ".checksum";
    
    static final String CHECKSUM_ALGORITHM = "SHA-256";
    
    private final File sourceDir;
    private final File workingDir;
    private final File checksumFile;
    private final List<FileHash> sourceFileHashList;
    private final boolean changesNeeded;
    private final boolean invalidateOnFailure;
    private final List<Object> arguments;
    private final BufferedReaderBuilder bufferedReaderBuilder;
    
    /**
     * Packer {@code build} command complete constructor.
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
     */
    public LegacyPackerBuildCommand(
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
        this(
            digestCreator,
            sourceDir,
            workingDir,
            sourceFilePathSet,
            changesNeeded,
            invalidateOnFailure,
            templatePath,
            force,
            only,
            except,
            vars,
            varFiles,
            CHECKSUM_ALGORITHM);
    }
    
    LegacyPackerBuildCommand(
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
        Set<String> varFiles,
        String checksumAlgorithm)
    {
        this(
            digestCreator,
            sourceDir,
            workingDir,
            sourceFilePathSet,
            changesNeeded,
            invalidateOnFailure,
            templatePath,
            force,
            only,
            except,
            vars,
            varFiles,
            checksumAlgorithm,
            LegacyPackerBuildCommand::bufferedReaderBuild);
    }
    
    LegacyPackerBuildCommand(
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
        Set<String> varFiles,
        String checksumAlgorithm,
        BufferedReaderBuilder bufferedReaderBuilder)
    {
        if (!sourceDir.isDirectory())
            throw new IllegalArgumentException(format(
                "Source path %s is not a directory",
                sourceDir.getAbsolutePath()));
        if (workingDir.exists() && !workingDir.isDirectory())
            throw new IllegalArgumentException(format(
                "Working path %s is not a directory",
                sourceDir.getAbsolutePath()));
        
        this.sourceDir = sourceDir;
        this.workingDir = workingDir;
        checksumFile = new File(this.workingDir, CHECKSUM_FILE_NAME);
        sourceFileHashList = toSourceFileHashList(
            digestCreator,
            checksumAlgorithm,
            this.sourceDir,
            sourceFilePathSet);
        this.changesNeeded = changesNeeded;
        this.invalidateOnFailure = invalidateOnFailure;
        arguments = toArguments(
            templatePath,
            force,
            only,
            except,
            vars,
            varFiles);
        this.bufferedReaderBuilder = requireNonNull(bufferedReaderBuilder);
    }
    
    @Override
    public String getName()
    {
        return COMMAND_NAME;
    }

    @Override
    public List<Object> getArguments()
    {
        return arguments;
    }
    
    @Override
    public Optional<File> getWorkingDir()
    {
        return Optional.of(workingDir);
    }
    
    @Override
    public boolean init(
        PackerCommandLogger logger,
        TimeoutHandler timeoutHandler)
    throws PackerCommandException, TimeoutException
    {
        if (hasChanges())
        {
            prepareWorkingDir();
            prepareChecksumFile();
            updateWorkingFiles();
            return true;
        }
        if (changesNeeded)
        {
            logger.info("There is not any change. Ignoring...");
            return false;
        }
        return true;
    }
    
    public void onSuccess()
    {
        // Nothing to be done...
    }
    
    @Override
    public void onFailure(PackerCommandFailureCode failureCode)
    {
        if (invalidateOnFailure)
            invalidateWorkingFiles();
    }
    
    @Override
    public void onAbort()
    {
        invalidateWorkingFiles();
    }
    
    @Override
    public PackerCommandFailureCode mapFailureCode(int errorCode)
    {
        return FAILURE_ERROR;
    }
    
    private boolean hasChanges()
    throws PackerCommandException
    {
        try (BufferedReader reader = bufferedReaderBuilder.build(checksumFile))
        {
            List<FileHash> result = new ArrayList<>();
            String line = reader.readLine();
            while (line != null)
            {
                result.add(FileHash.parse(line));
                line = reader.readLine();
            }
            return !sourceFileHashList.equals(result);
        }
        catch (FileNotFoundException exception)
        {
            return true;
        }
        catch (IOException exception)
        {
            throw new PackerCommandException(format(
                "Unable to retrieve file hash list"),
                exception);
        }
    }
    
    private void prepareWorkingDir()
    {
        if (!workingDir.exists())
            workingDir.mkdirs();
    }
    
    private void prepareChecksumFile()
    throws PackerCommandException
    {
        try
        {
            if (!checksumFile.exists())
                checksumFile.createNewFile();
        }
        catch (IOException exception)
        {
            throw new PackerCommandException(format(
                "Could not create chechsum file %s",
                checksumFile.getAbsolutePath()),
                exception);
        }
    }
    
    private void updateWorkingFiles()
    throws PackerCommandException
    {
        try (PrintWriter writer = new PrintWriter(checksumFile))
        {
            sourceFileHashList.forEach(fileHash -> updateFile(
                writer,
                fileHash.toString(),
                new File(sourceDir, fileHash.getRelativePath()),
                new File(workingDir, fileHash.getRelativePath())));
        }
        catch (UpdateFileException exception)
        {
            throw new PackerCommandException(format(
                "Could not update file %s from file %s",
                exception.sourcePath,
                exception.targetPath),
                exception.getCause());
        }
        catch (IOException exception)
        {
            throw new PackerCommandException(
                "Could not updates files",
                exception.getCause());
        }
    }
    
    private void invalidateWorkingFiles()
    {
        checksumFile.delete();
    }
    
    private static List<FileHash> toSourceFileHashList(
        MessageDigestCreator digestCreator,
        String checksumAlgorith,
        File sourceDir,
        Set<String> sourceFilePathSet)
    {
        try
        {
            return sourceFilePathSet.stream()
                .map(sourceFilePath -> toFileHash(
                    digestCreator,
                    checksumAlgorith,
                    sourceDir,
                    sourceFilePath))
                .sorted(comparing(FileHash::getRelativePath))
                .collect(toList());
        }
        catch (ToFileHashException exception)
        {
            throw new IllegalStateException(format(
                "Could not calculate hash for of file %s",
                exception.file.getAbsolutePath()),
                exception.getCause());
        }
    }
    
    private static List<Object> toArguments(
        String templatePath,
        boolean force,
        Set<String> only,
        Set<String> except,
        Map<String, Object> vars,
        Set<String> varFiles)
    {
        return Stream.of(
            force ? Stream.of("-force") : Stream.empty(),
            only.isEmpty() ? Stream.empty() : Stream.of(
                "-only",
                only.stream()
                    .collect(joining(","))),
            except.isEmpty() ? Stream.empty() : Stream.of(
                "-except",
                except.stream()
                        .collect(joining(","))),
            vars.entrySet().stream()
                .flatMap(var -> Stream.of(
                    "-var",
                    String.format(
                        "%s=%s",
                        var.getKey(),
                        var.getValue().toString()))),
            varFiles.stream()
                .flatMap(varFile -> Stream.of(
                    "-var-file",
                    varFile)),
            Stream.of(templatePath))
            .flatMap(identity())
            .collect(toList());
    }
    
    private static FileHash toFileHash(
        MessageDigestCreator digestCreator,
        String checksumAlgorithm,
        File baseDir,
        String relativePath)
    {
        try
        {
            return FileHash.digest(
                MessageDigest::getInstance,
                baseDir,
                checksumAlgorithm,
                relativePath);
        }
        catch (NoSuchAlgorithmException | IOException exception)
        {
            throw new ToFileHashException(
                exception,
                new File(baseDir, relativePath));
        }
    }
    
    private static void updateFile(
        PrintWriter checksumWriter,
        String hashStr,
        File sourceFile,
        File targetFile)
    {
        try
        {
            createDirectories(targetFile.toPath().getParent());
            if (targetFile.exists())
                delete(targetFile.toPath());
            copy(sourceFile.toPath(), targetFile.toPath());
            checksumWriter.println(hashStr);
        }
        catch (IOException exception)
        {
            throw new UpdateFileException(
                exception,
                sourceFile.toPath(),
                targetFile.toPath());
        }
    }
    
    private static BufferedReader bufferedReaderBuild(File file)
    throws IOException
    {
        return new BufferedReader(new FileReader(file));
    }
    
    private static class ToFileHashException
    extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        
        private final File file;

        private ToFileHashException(Throwable cause, File file)
        {
            super(cause);
            this.file = file;
        }
    }
    
    private static class UpdateFileException
    extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private final Path sourcePath;
        private final Path targetPath;
        
        private UpdateFileException(
            Throwable cause,
            Path sourcePath,
            Path targetPath)
        {
            super(cause);
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
        }
    }
}

@FunctionalInterface
interface BufferedReaderBuilder
{
    BufferedReader build(File file)
    throws IOException;
}
