package io.github.miquelo.tools.packer.commands;

import static io.github.miquelo.tools.packer.PackerCommandFailureCode
    .FAILURE_ERROR;
import static java.lang.String.format;
import static java.nio.file.Files.walk;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static javax.xml.bind.DatatypeConverter.parseHexBinary;
import static javax.xml.bind.DatatypeConverter.printHexBinary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
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
public class PackerBuildCommand
implements PackerCommand
{
    private static final String COMMAND_NAME = "build";

    private static final String CHECKSUM_FILE_NAME = ".checksum";
    private static final String CHECKSUM_ALGORITHM = "SHA-256";
    
    private static final int DIGEST_BUFFER_SIZE = 4 * 1024;
    
    private final MessageDigestCreator digestCreator;
    private final File inputDir;
    private final boolean changesNeeded;
    private final boolean invalidateOnFailure;
    private final List<Object> arguments;
    private final File checksumFile;
    
    /**
     * Packer {@code build} command complete constructor.
     * 
     * @param digestCreator
     *     Message digest used to obtain input files hash.
     * @param inputDir
     *     Directory where input files are located.
     * @param changesNeeded
     *     Whether changes on source files are needed for this command to don't
     *     be ignored.
     * @param invalidateOnFailure
     *     Whether files will be invalidated if command executions fails.
     * @param templatePath
     *     Input directory relative path of template used for this build.
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
    public PackerBuildCommand(
        MessageDigestCreator digestCreator,
        File inputDir,
        boolean changesNeeded,
        boolean invalidateOnFailure,
        String templatePath,
        boolean force,
        Set<String> only,
        Set<String> except,
        Map<String, Object> vars,
        Set<String> varFiles)
    {
        this.digestCreator = requireNonNull(digestCreator);
        this.inputDir = requireNonNull(inputDir);
        this.changesNeeded = changesNeeded;
        this.invalidateOnFailure = invalidateOnFailure;
        
        arguments = Stream.of(
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
        
        checksumFile = new File(this.inputDir, CHECKSUM_FILE_NAME);
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
        return Optional.of(inputDir);
    }
    
    @Override
    public boolean init(
        PackerCommandLogger logger,
        TimeoutHandler timeoutHandler)
    throws PackerCommandException, TimeoutException
    {
        logger.debug(format("Using %s as input directory", inputDir));
        
        Set<ChecksumEntry> currentChecksum = currentChecksumGet();
        if (!currentChecksum.equals(previousChecksumGet()))
        {
            checksumUpdate(currentChecksum);
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
            checksumFile.delete();
    }
    
    @Override
    public void onAbort()
    {
        checksumFile.delete();
    }
    
    @Override
    public PackerCommandFailureCode mapFailureCode(int errorCode)
    {
        return FAILURE_ERROR;
    }
    
    private Set<ChecksumEntry> currentChecksumGet()
    throws PackerCommandException
    {
        try
        {
            return walk(inputDir.toPath())
                .filter(this::isRegularFile)
                .filter(this::isNotChecksumFile)
                .map(this::toChecksumEntry)
                .collect(toSet());
        }
        catch (IOException exception)
        {
            throw new PackerCommandException(exception);
        }
        catch (
            UncheckedIOException |
            UncheckedNoSuchAlgorithmException exception)
        {
            throw new PackerCommandException(exception.getCause());
        }
    }
    
    private Set<ChecksumEntry> previousChecksumGet()
    throws PackerCommandException
    {
        try (BufferedReader reader = newBufferedReader(checksumFile))
        {
            return reader.lines()
                .map(ChecksumEntry::parse)
                .collect(toSet());
        }
        catch (FileNotFoundException exception)
        {
            return emptySet();
        }
        catch (IOException exception)
        {
            throw new PackerCommandException(exception);
        }
        catch (UncheckedIOException exception)
        {
            throw new PackerCommandException(exception.getCause());
        }
    }
    
    private void checksumUpdate(Set<ChecksumEntry> checksum)
    throws PackerCommandException
    {
        try (PrintWriter writer = new PrintWriter(checksumFile))
        {
            checksum.forEach(entry -> writer.println(entry));
        }
        catch (FileNotFoundException exception)
        {
            createNewFile(checksumFile);
        }
        catch (UncheckedIOException exception)
        {
            throw new PackerCommandException(exception.getCause());
        }
    }
    
    private boolean isRegularFile(Path path)
    {
        return path.toFile().isFile();
    }
    
    private boolean isNotChecksumFile(Path path)
    {
        return !path.toAbsolutePath()
            .equals(checksumFile.getAbsoluteFile().toPath());
    }
    
    private ChecksumEntry toChecksumEntry(Path path)
    {
        try (InputStream input = new FileInputStream(path.toFile()))
        {
            MessageDigest digest = digestCreator.create(CHECKSUM_ALGORITHM);
            byte[] buf = new byte[DIGEST_BUFFER_SIZE];
            int len = input.read(buf, 0, DIGEST_BUFFER_SIZE);
            while (len > 0)
            {
                digest.update(buf, 0, len);
                len = input.read(buf, 0, DIGEST_BUFFER_SIZE);
            }
            return new ChecksumEntry(buf, path.toString());
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException(exception);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new UncheckedNoSuchAlgorithmException(exception);
        }
    }
    
    private static BufferedReader newBufferedReader(File file)
    throws IOException
    {
        return new BufferedReader(new FileReader(file));
    }
    
    private static void createNewFile(File file)
    throws PackerCommandException
    {
        try
        {
            file.createNewFile();
        }
        catch (IOException exception)
        {
            throw new PackerCommandException(exception);
        }
    }
}

class ChecksumEntry
{
    private final byte[] hash;
    private final String path;
    
    ChecksumEntry(byte[] hash, String path)
    {
        this.hash = requireNonNull(hash);
        this.path = requireNonNull(path);
    }
    
    @Override
    public int hashCode()
    {
        return Arrays.hashCode(hash);
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
            return true;
        if (getClass().equals(obj.getClass()))
        {
            ChecksumEntry entry = (ChecksumEntry) obj;
            return Arrays.equals(hash, entry.hash) && path.equals(entry.path);
        }
        return false;
    }
    
    @Override
    public String toString()
    {
        return format("%s %s", toString(hash), path);
    }
    
    static ChecksumEntry parse(String str)
    {
        String[] parts = str.split(" ");
        return new ChecksumEntry(toBytes(parts[0]), parts[1]);
    }
    
    private static byte[] toBytes(String str)
    {
        return parseHexBinary(str);
    }
    
    private static String toString(byte[] bytes)
    {
        return printHexBinary(bytes);
    }
}

class UncheckedNoSuchAlgorithmException
extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    UncheckedNoSuchAlgorithmException(NoSuchAlgorithmException cause)
    {
        super(cause);
    }
    
    @Override
    public NoSuchAlgorithmException getCause()
    {
        return (NoSuchAlgorithmException) super.getCause();
    }
}

