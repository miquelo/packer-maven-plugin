package io.github.miquelo.tools.packer;

import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.walk;
import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class PackerBuildCommand
extends PackerCommand
{
    private static final String COMMAND_NAME = "build";
    
    private static final String CHECKSUM_ALGORITHM = "SHA-256";
    private static final String CHECKSUM_FILE_NAME = ".checksum";

    private static final String CACHE_PATH = "packer_cache";
    
    private final File sourceDir;
    private final File workingDir;
    private final File checksumFile;
    private final List<FileHash> sourceFileHashList;
    private final boolean changesNeeded;
    private final boolean invalidateOnFailure;
    private final boolean keepCache;
    
    public PackerBuildCommand(
        PackerCommandLauncher launcher,
        PackerCommandLogger commandLogger,
        Consumer<PackerOutputMessage> messageConsumer,
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
        super(
            launcher,
            commandLogger,
            messageConsumer,
            COMMAND_NAME,
            Stream.of(
                force ? Stream.of("-force") : Stream.<String>empty(),
                vars.entrySet().stream()
                    .flatMap(var -> Stream.of(
                        "-var",
                        String.format(
                            "%s=%s",
                            var.getKey(),
                            var.getValue().toString()))),
                Stream.of(templatePath))
                .flatMap(identity())
                .collect(toList()));
        
        if (!sourceDir.isDirectory())
            throw new PackerCommandException(format(
                "Source path %s is not a directory",
                sourceDir.getAbsolutePath()));
        if (workingDir.exists() && !workingDir.isDirectory())
            throw new PackerCommandException(format(
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
        this.keepCache = keepCache;
    }
    
    @Override
    protected Optional<File> getDesiredWorkingDir()
    {
        return Optional.of(workingDir);
    }
    
    @Override
    protected boolean init()
    throws PackerCommandException
    {
        if (hasChanges())
        {
            deleteWorkingFiles();
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
    
    @Override
    protected void onFailure(FailureCode failureCode)
    {
        if (invalidateOnFailure)
            invalidateWorkingFiles();
    }
    
    @Override
    protected void onAbort()
    {
        invalidateWorkingFiles();
    }
    
    @Override
    protected FailureCode mapFailureCode(int errorCode)
    {
        return FailureCode.FAILURE_ERROR;
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
    
    private boolean isNotWorkingDir(Path path)
    {
        return !path.equals(Paths.get(workingDir.getAbsolutePath()));
    }
    
    private boolean isNotKeepCacheOrNotCacheDir(Path path)
    {
        return !keepCache || !path.startsWith(Paths.get(
            workingDir.getAbsolutePath(),
            CACHE_PATH));
    }
    
    private void deleteWorkingFiles()
    throws PackerCommandException
    {
        try
        {
            if (workingDir.exists())
                walk(workingDir.toPath())
                    .sorted(reverseOrder())
                    .filter(this::isNotWorkingDir)
                    .filter(this::isNotKeepCacheOrNotCacheDir)
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        catch (IOException exception)
        {
            throw new PackerCommandException(format(
                "Could not delete files of working directory %s",
                workingDir.getAbsolutePath()));
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
            sourceFileHashList.forEach(fileHash -> copyFile(
                writer,
                fileHash.toString(),
                Paths.get(
                    sourceDir.getAbsolutePath(),
                    fileHash.getRelativePath()),
                Paths.get(
                    workingDir.getAbsolutePath(),
                    fileHash.getRelativePath())));
        }
        catch (CopyFileException exception)
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
    throws PackerCommandException
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
            throw new PackerCommandException(format(
                "Could not calculate hash for of file %s",
                exception.file.getAbsolutePath()),
                exception.getCause());
        }
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
    
    private static void copyFile(
        PrintWriter checksumWriter,
        String hashStr,
        Path sourcePath,
        Path targetPath)
    {
        try
        {
            createDirectories(targetPath.getParent());
            copy(sourcePath, targetPath);
            checksumWriter.println(hashStr);
        }
        catch (IOException exception)
        {
            throw new CopyFileException(exception, sourcePath, targetPath);
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
    
    private static class CopyFileException
    extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private final Path sourcePath;
        private final Path targetPath;
        
        private CopyFileException(
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
