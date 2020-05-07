package io.github.miquelo.tools.packer;

import static io.github.miquelo.tools.packer.PackerCommandFailureCode
    .FAILURE_ERROR;
import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.delete;
import static java.util.Comparator.comparing;
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

class PackerBuildCommand
implements PackerCommand
{
    private static final String COMMAND_NAME = "build";
    
    private static final String CHECKSUM_ALGORITHM = "SHA-256";
    private static final String CHECKSUM_FILE_NAME = ".checksum";
    
    private final File sourceDir;
    private final File workingDir;
    private final File checksumFile;
    private final List<FileHash> sourceFileHashList;
    private final boolean changesNeeded;
    private final boolean invalidateOnFailure;
    private final List<Object> arguments;
    
    public PackerBuildCommand(
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
        try (BufferedReader reader = new BufferedReader(new FileReader(
            checksumFile)))
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
                "Unable to retrieve previous file hash list"),
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
            if (!checksumFile.isFile())
                throw new PackerCommandException(format(
                    "Checksum %s is not a file",
                    checksumFile.getAbsolutePath()));
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
        File sourceDir,
        Set<String> sourceFilePathSet)
    {
        try
        {
            return sourceFilePathSet.stream()
                .map(sourceFilePath -> toFileHash(
                    digestCreator,
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
        File baseDir,
        String relativePath)
    {
        try
        {
            return FileHash.digest(
                MessageDigest::getInstance,
                baseDir,
                CHECKSUM_ALGORITHM,
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
