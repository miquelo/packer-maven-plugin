package io.github.miquelo.maven.plugin.packer;

import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.walk;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.Objects.requireNonNull;
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
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import io.github.miquelo.maven.plugin.packer.filehash.FileHash;

public class PackerBuildSourceFiles
{
    private static final String CHECKSUM_ALGORITHM = "SHA-256";
    private static final String CHECKSUM_FILE_NAME = ".checksum";
    
    private final Log log;
    private final File sourceDir;
    private final File workingDir;
    private final File checksumFile;
    private final List<FileHash> checksum;
    
    public PackerBuildSourceFiles(
        Log log,
        File sourceDir,
        File workingDir,
        Set<String> relativePathSet)
    throws MojoFailureException, MojoExecutionException
    {
        try
        {
            if (!sourceDir.isDirectory())
                throw new MojoFailureException(format(
                    "Source path %s is not a directory",
                    sourceDir.getAbsolutePath()));
            if (workingDir.exists() && !workingDir.isDirectory())
                throw new MojoFailureException(format(
                    "Working path %s is not a directory",
                    sourceDir.getAbsolutePath()));
            
            this.log = requireNonNull(log);
            this.sourceDir = sourceDir;
            this.workingDir = workingDir;
            checksumFile = new File(this.workingDir, CHECKSUM_FILE_NAME);
            
            checksum = relativePathSet.stream()
                .map(relativePath -> toFileHash(sourceDir, relativePath))
                .sorted(comparing(FileHash::getRelativePath))
                .collect(toList());
        }
        catch (ToFileHashException exception)
        {
            throw new MojoExecutionException(
                exception.getMessage(),
                exception.getCause());
        }
    }
    
    public File getWorkingDir()
    {
        return workingDir;
    }
    
    public boolean hasChanges()
    throws MojoExecutionException
    {
        return !checksum.equals(getPreviousFileHashList());
    }
    
    public void updateAll()
    throws MojoExecutionException
    {
        deleteWorkingFiles();
        prepareWorkingDir();
        prepareChecksumFile();
        updateWorkingFiles();
    }
    
    public void invalidateWorkingDir()
    {
        checksumFile.delete();
    }
    
    private List<FileHash> getPreviousFileHashList()
    throws MojoExecutionException
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
            return result;
        }
        catch (FileNotFoundException exception)
        {
            return emptyList();
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(format(
                "Unable to retrieve previous file hash list"),
                exception);
        }
    }
    
    private void deleteWorkingFiles()
    throws MojoExecutionException
    {
        try
        {
            log.debug(format(
                "Deleting files of working directory %s...",
                workingDir.getAbsolutePath()));
            if (workingDir.exists())
                walk(workingDir.toPath())
                    .sorted(reverseOrder())
                    .filter(path -> !path.startsWith(Paths.get(
                        workingDir.getAbsolutePath(),
                        ".")))
                    .filter(path -> !path.startsWith(Paths.get(
                        workingDir.getAbsolutePath(),
                        "packer_cache")))
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(format(
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
    throws MojoExecutionException
    {
        try
        {
            if (!checksumFile.exists())
                checksumFile.createNewFile();
            if (!checksumFile.isFile())
                throw new MojoExecutionException(format(
                    "Checksum %s is not a file",
                    checksumFile.getAbsolutePath()));
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(format(
                "Could not create chechsum file %s",
                checksumFile.getAbsolutePath()));
        }
    }
    
    private void updateWorkingFiles()
    throws MojoExecutionException
    {
        try (PrintWriter writer = new PrintWriter(checksumFile))
        {
            checksum.forEach(fileHash -> copyFile(
                writer,
                fileHash.toString(),
                Paths.get(
                    sourceDir.getAbsolutePath(),
                    fileHash.getRelativePath()),
                Paths.get(
                    workingDir.getAbsolutePath(),
                    fileHash.getRelativePath())));
        }
        catch (CopyFileException | IOException exception)
        {
            throw new MojoExecutionException(
                exception.getMessage(),
                exception.getCause());
        }
    }
    
    private static FileHash toFileHash(File baseDir, String relativePath)
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

        private ToFileHashException(Throwable cause, File file)
        {
            super(format(
                "Could not calculate hash of file %s",
                file.getAbsolutePath()),
                cause);
        }
    }
    
    private static class CopyFileException
    extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private CopyFileException(
            Throwable cause,
            Path sourcePath,
            Path targetPath)
        {
            super(format(
                "Could not copy file %s to file %s",
                sourcePath,
                targetPath),
                cause);
        }
    }
}
