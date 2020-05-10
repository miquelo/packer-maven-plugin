package io.github.miquelo.tools.packer.commands;

import static com.google.common.io.Files.write;
import static io.github.miquelo.tools.packer.PackerCommandFailureCode.FAILURE_ERROR;
import static io.github.miquelo.tools.packer.commands.PackerBuildCommand.CHECKSUM_ALGORITHM;
import static java.nio.file.Files.write;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.data.Index.atIndex;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.miquelo.tools.packer.PackerCommand;
import io.github.miquelo.tools.packer.PackerCommandException;
import io.github.miquelo.tools.packer.PackerCommandFailureCode;
import io.github.miquelo.tools.packer.PackerCommandLogger;
import io.github.miquelo.tools.packer.TimeoutHandler;
import io.github.miquelo.tools.packer.commands.BufferedReaderBuilder;
import io.github.miquelo.tools.packer.commands.MessageDigestCreator;

@ExtendWith(MockitoExtension.class)
public class PackerBuildCommandTest
{
    private static final MessageDigestCreator DEFAULT_DIGEST_CREATOR =
            MessageDigest::getInstance;
    
    private static final BufferedReaderBuilder FAILING_BUFFERED_READER_BUILDER =
        file -> {
            throw new IOException();
        };
        
    private static final String BAD_CHECKSUM_ALGORITHM =
        "BAD-CHECKSUM-ALGORITHM";
    
    private static final String CHECKSUM_FILE_NAME = ".checksum";
    
    private static final String BUILD_COMMAND_NAME = "build";
    
    private static final boolean FORCE = true;
    private static final boolean CHANGES_ARE_NEEDED = true;
    private static final boolean CHANGES_ARE_NOT_NEEDED = false;
    private static final boolean DO_INVALIDATE_ON_FAILURE = true;
    private static final boolean DO_NOT_INVALIDATE_ON_FAILURE = false;
    
    private static final Set<String> ANY_SOURCE_FILE_PATH_SET = emptySet();
    private static final boolean ANY_CHANGES_NEEDED = false;
    private static final boolean ANY_INVALIDATE_ON_FAILURE = false;
    private static final String ANY_TEMPLATE_PATH = "any-template-path";
    private static final boolean ANY_FORCE = false;
    private static final Set<String> ANY_ONLY = emptySet();
    private static final Set<String> ANY_EXCEPT = emptySet();
    private static final Map<String, Object> ANY_VARS = emptyMap();
    private static final Set<String> ANY_VAR_FILES = emptySet();
    private static final PackerCommandFailureCode ANY_FAILURE_CODE =
        FAILURE_ERROR;
    private static final int ANY_ERROR_CODE = 0;

    private static final String SOME_TEMPLATE_PATH = "some-template";
    
    private static final String FIRST_ONLY_ITEM = "first-only";
    private static final String SECOND_ONLY_ITEM = "second-only";
    private static final Object FIRST_SECOND_ONLY_STR =
        "first-only,second-only";
    private static final Object SECOND_FIRST_ONLY_STR =
        "second-only,first-only";
    private static final String FIRST_EXCEPT_ITEM = "first-except";
    private static final String SECOND_EXCEPT_ITEM = "second-except";
    private static final Object FIRST_SECOND_EXCEPT_STR =
        "first-except,second-except";
    private static final Object SECOND_FIRST_EXCEPT_STR =
        "second-except,first-except";
    private static final String FIRST_VAR_KEY = "first-var-key";
    private static final Object FIRST_VAR_VALUE = "first-var-value";
    private static final Object FIRST_VAR_STR = "first-var-key=first-var-value";
    private static final String SECOND_VAR_KEY = "second-var-key";
    private static final Object SECOND_VAR_VALUE = "second-var-value";
    private static final Object SECOND_VAR_STR =
        "second-var-key=second-var-value";
    private static final String FIRST_VAR_FILE = "first-var-file";
    private static final String SECOND_VAR_FILE = "second-var-file";

    private static final String SOME_INCLUDED_SOURCE_FILE_PATH =
        "some-included-file.txt";
    private static final byte[] SOME_INCLUDED_SOURCE_FILE_CONTENT = { 0x01 };
    private static final byte[] OLD_INCLUDED_SOURCE_FILE_CONTENT = { 0x02 };
    
    private static final Set<String> SOME_SOURCE_FILE_PATH_SET = singleton(
        SOME_INCLUDED_SOURCE_FILE_PATH);
    
    private static final String SOME_EXCLUDED_SOURCE_FILE_PATH =
        "some-excluded-file.txt";

    private static final String SOME_INCLUDED_SOURCE_FILE_CONTENT_HASH_ENTRY =
        "SHA-256 " +
        "4BF5122F344554C53BDE2EBB8CD2B7E3D1600AD631C385A5D7CCE23C7785459A " +
        "some-included-file.txt";

    private static final String
        ANTOHER_INCLUDED_SOURCE_FILE_CONTENT_HASH_ENTRY =
        "SHA-256 " +
        "4BF5122F344554C53BDE2EBB8CD2B7E3D1600AD631C385A5D7CCE23C7785459A " +
        "another-included-file.txt";
    
    private File tempDir;
    
    public PackerBuildCommandTest()
    {
        tempDir = null;
    }
    
    @BeforeEach
    public void setUp(@TempDir File tempDir)
    {
        this.tempDir = tempDir;
    }
    
    @Test
    public void workingDirMayNotExist(
        @Mock
        MessageDigestCreator anyDigestCreator)
    throws Exception
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File nonExistingWorkingDir = new File(
            tempDir,
            "non-existing-working-dir");
        
        new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            nonExistingWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
    }
    
    @Test
    public void getBuildName(
        @Mock
        MessageDigestCreator anyDigestCreator)
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File anyWorkingDir = new File(tempDir, "any-working-dir");
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            anyWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        String name = command.getName();
        
        assertThat(name).isEqualTo(BUILD_COMMAND_NAME);
    }
    
    @Test
    public void getCompleteInferredArguments(
        @Mock
        MessageDigestCreator anyDigestCreator)
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File anyWorkingDir = new File(tempDir, "any-working-dir");
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            anyWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            SOME_TEMPLATE_PATH,
            FORCE,
            Stream.of(FIRST_ONLY_ITEM, SECOND_ONLY_ITEM)
                .collect(toSet()),
            Stream.of(FIRST_EXCEPT_ITEM, SECOND_EXCEPT_ITEM)
                .collect(toSet()),
            Stream.of(
                new SimpleEntry<>(FIRST_VAR_KEY, FIRST_VAR_VALUE),
                new SimpleEntry<>(SECOND_VAR_KEY, SECOND_VAR_VALUE))
                .collect(toMap(Entry::getKey, Entry::getValue)),
            Stream.of(FIRST_VAR_FILE, SECOND_VAR_FILE)
                .collect(toSet()));
        
        List<Object> arguments = command.getArguments();
        
        assertThat(arguments)
            .hasSize(14)
            .satisfies(
                item -> assertThat(item)
                    .isEqualTo("-force"),
                atIndex(0))
            .satisfies(
                item -> assertThat(item)
                    .isEqualTo("-only"),
                atIndex(1))
            .satisfies(
                item -> assertThat(item)
                    .isIn(FIRST_SECOND_ONLY_STR, SECOND_FIRST_ONLY_STR),
                atIndex(2))
            .satisfies(item -> assertThat(item)
                .isEqualTo("-except"), atIndex(3))
            .satisfies(
                item -> assertThat(item)
                    .isIn(FIRST_SECOND_EXCEPT_STR, SECOND_FIRST_EXCEPT_STR),
                atIndex(4))
            .satisfies(
                item -> assertThat(item)
                    .isEqualTo("-var"),
                atIndex(5))
            .satisfies(
                item -> assertThat(item)
                    .isIn(FIRST_VAR_STR, SECOND_VAR_STR),
                atIndex(6))
            .satisfies(
                item -> assertThat(item)
                    .isEqualTo("-var"),
                atIndex(7))
            .satisfies(
                item -> assertThat(item)
                    .isIn(FIRST_VAR_STR, SECOND_VAR_STR),
                atIndex(8))
            .satisfies(
                item -> assertThat(item)
                    .isEqualTo("-var-file"),
                atIndex(9))
            .satisfies(
                item -> assertThat(item)
                    .isIn(FIRST_VAR_FILE, SECOND_VAR_FILE),
                atIndex(10))
            .satisfies(
                item -> assertThat(item)
                    .isEqualTo("-var-file"),
                atIndex(11))
            .satisfies(
                item -> assertThat(item)
                    .isIn(FIRST_VAR_FILE, SECOND_VAR_FILE),
                atIndex(12))
            .satisfies(
                item -> assertThat(item)
                    .isEqualTo(SOME_TEMPLATE_PATH),
                atIndex(13));
    }
    
    @Test
    public void getItsWorkingDir(
        @Mock
        MessageDigestCreator anyDigestCreator)
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            someWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
       Optional<File> workingDir = command.getWorkingDir();
       
       assertThat(workingDir).hasValue(someWorkingDir);
    }
    
    @Test
    public void updateAllWhereChecksumFileDoesNotExist(
        @Mock
        MessageDigestCreator anyDigestCreator,
        @Mock
        PackerCommandLogger anyLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File someSourceDir = new File(tempDir, "some-source-dir");
        someSourceDir.mkdir();
        File someIncludedSourceFile = new File(
            someSourceDir,
            SOME_INCLUDED_SOURCE_FILE_PATH);
        someIncludedSourceFile.createNewFile();
        write(SOME_INCLUDED_SOURCE_FILE_CONTENT, someIncludedSourceFile);
        File someExcludedSourceFile = new File(
            someSourceDir,
            SOME_EXCLUDED_SOURCE_FILE_PATH);
        someExcludedSourceFile.createNewFile();
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            someSourceDir,
            someWorkingDir,
            SOME_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        boolean mustContinue = command.init(anyLogger, anyTimeoutHandler);
        
        assertThat(mustContinue).isTrue();
        assertThat(new File(someWorkingDir, SOME_INCLUDED_SOURCE_FILE_PATH))
            .hasBinaryContent(SOME_INCLUDED_SOURCE_FILE_CONTENT);
        assertThat(new File(someWorkingDir, SOME_EXCLUDED_SOURCE_FILE_PATH))
            .doesNotExist();
    }
    
    @Test
    public void updateAllWhereChecksumFileHasChangedAndItIsNeeded(
        @Mock
        MessageDigestCreator anyDigestCreator,
        @Mock
        PackerCommandLogger anyLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File someSourceDir = new File(tempDir, "some-source-dir");
        someSourceDir.mkdir();
        File someIncludedSourceFile = new File(
            someSourceDir,
            SOME_INCLUDED_SOURCE_FILE_PATH);
        someIncludedSourceFile.createNewFile();
        write(SOME_INCLUDED_SOURCE_FILE_CONTENT, someIncludedSourceFile);
        File someExcludedSourceFile = new File(
            someSourceDir,
            SOME_EXCLUDED_SOURCE_FILE_PATH);
        someExcludedSourceFile.createNewFile();
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        File checksumFile = new File(someWorkingDir, CHECKSUM_FILE_NAME);
        write(
            checksumFile.toPath(),
            singletonList(ANTOHER_INCLUDED_SOURCE_FILE_CONTENT_HASH_ENTRY),
            CREATE);
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            someSourceDir,
            someWorkingDir,
            SOME_SOURCE_FILE_PATH_SET,
            CHANGES_ARE_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        boolean mustContinue = command.init(anyLogger, anyTimeoutHandler);
        
        assertThat(mustContinue).isTrue();
        assertThat(new File(someWorkingDir, SOME_INCLUDED_SOURCE_FILE_PATH))
            .hasBinaryContent(SOME_INCLUDED_SOURCE_FILE_CONTENT);
        assertThat(new File(someWorkingDir, SOME_EXCLUDED_SOURCE_FILE_PATH))
            .doesNotExist();
    }
    
    @Test
    public void ignoredWhenHasNoChangesAndTheyAreNeeded(
        @Mock
        PackerCommandLogger someLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File someSourceDir = new File(tempDir, "some-source-dir");
        someSourceDir.mkdir();
        File someIncludedSourceFile = new File(
            someSourceDir,
            SOME_INCLUDED_SOURCE_FILE_PATH);
        someIncludedSourceFile.createNewFile();
        write(SOME_INCLUDED_SOURCE_FILE_CONTENT, someIncludedSourceFile);
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        File checksumFile = new File(someWorkingDir, CHECKSUM_FILE_NAME);
        write(
            checksumFile.toPath(),
            singletonList(SOME_INCLUDED_SOURCE_FILE_CONTENT_HASH_ENTRY),
            CREATE);
        PackerCommand command = new PackerBuildCommand(
            DEFAULT_DIGEST_CREATOR,
            someSourceDir,
            someWorkingDir,
            SOME_SOURCE_FILE_PATH_SET,
            CHANGES_ARE_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        boolean mustContinue = command.init(someLogger, anyTimeoutHandler);
        
        assertThat(mustContinue).isFalse();
        verify(someLogger).info(anyString());
    }
    
    @Test
    public void mustContinueWhenHasNoChangesButTheyAreNotNeeded(
        @Mock
        PackerCommandLogger someLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File someSourceDir = new File(tempDir, "some-source-dir");
        someSourceDir.mkdir();
        File someIncludedSourceFile = new File(
            someSourceDir,
            SOME_INCLUDED_SOURCE_FILE_PATH);
        someIncludedSourceFile.createNewFile();
        write(SOME_INCLUDED_SOURCE_FILE_CONTENT, someIncludedSourceFile);
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        File checksumFile = new File(someWorkingDir, CHECKSUM_FILE_NAME);
        write(
            checksumFile.toPath(),
            singletonList(SOME_INCLUDED_SOURCE_FILE_CONTENT_HASH_ENTRY),
            CREATE);
        PackerCommand command = new PackerBuildCommand(
            DEFAULT_DIGEST_CREATOR,
            someSourceDir,
            someWorkingDir,
            SOME_SOURCE_FILE_PATH_SET,
            CHANGES_ARE_NOT_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        boolean mustContinue = command.init(someLogger, anyTimeoutHandler);
        
        assertThat(mustContinue).isTrue();
    }
    
    @Test
    public void createWorkingDirWhenItDoesNotExist(
        @Mock
        MessageDigestCreator anyDigestCreator,
        @Mock
        PackerCommandLogger anyLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File nonExistingWorkingDir = new File(
            tempDir,
            "non-existing-working-dir");
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            nonExistingWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        command.init(anyLogger, anyTimeoutHandler);
        
        assertThat(nonExistingWorkingDir).exists();
    }
    
    @Test
    public void replaceWorkingFilesWhenUpdated(
        @Mock
        MessageDigestCreator anyDigestCreator,
        @Mock
        PackerCommandLogger anyLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File someSourceDir = new File(tempDir, "some-source-dir");
        someSourceDir.mkdir();
        File someIncludedSourceFile = new File(
            someSourceDir,
            SOME_INCLUDED_SOURCE_FILE_PATH);
        someIncludedSourceFile.createNewFile();
        write(SOME_INCLUDED_SOURCE_FILE_CONTENT, someIncludedSourceFile);
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        File oldIncludedWorkingFile = new File(
            someWorkingDir,
            SOME_INCLUDED_SOURCE_FILE_PATH);
        oldIncludedWorkingFile.createNewFile();
        write(OLD_INCLUDED_SOURCE_FILE_CONTENT, oldIncludedWorkingFile);
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            someSourceDir,
            someWorkingDir,
            SOME_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        command.init(anyLogger, anyTimeoutHandler);
        
        assertThat(oldIncludedWorkingFile)
            .hasBinaryContent(SOME_INCLUDED_SOURCE_FILE_CONTENT);
    }
    
    @Test
    public void doNothingOnSuccess(
        @Mock
        MessageDigestCreator anyDigestCreator)
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File anyWorkingDir = new File(tempDir, "any-working-dir");
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            anyWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        command.onSuccess();
    }
    
    @Test
    public void invalidateOnFailureWhenNeeded(
        @Mock
        MessageDigestCreator anyDigestCreator)
    throws Exception
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        File checksumFile = new File(someWorkingDir, CHECKSUM_FILE_NAME);
        checksumFile.createNewFile();
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            someWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            DO_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        command.onFailure(ANY_FAILURE_CODE);
        
        assertThat(checksumFile).doesNotExist();
    }
    
    @Test
    public void doNotInvalidateOnFailureWhenNotWanted(
        @Mock
        MessageDigestCreator anyDigestCreator)
    throws Exception
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        File checksumFile = new File(someWorkingDir, CHECKSUM_FILE_NAME);
        checksumFile.createNewFile();
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            someWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            DO_NOT_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        command.onFailure(ANY_FAILURE_CODE);
        
        assertThat(checksumFile).exists();
    }
    
    @Test
    public void invalidateOnAbort(
        @Mock
        MessageDigestCreator anyDigestCreator)
    throws Exception
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        File checksumFile = new File(someWorkingDir, CHECKSUM_FILE_NAME);
        checksumFile.createNewFile();
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            someWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        command.onAbort();
        
        assertThat(checksumFile).doesNotExist();
    }
    
    @Test
    public void alwaysMapToFailureError(
        @Mock
        MessageDigestCreator anyDigestCreator)
    throws Exception
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File anyWorkingDir = new File(tempDir, "any-working-dir");
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            anyWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        PackerCommandFailureCode failureCode = command.mapFailureCode(
            ANY_ERROR_CODE);
        
        assertThat(failureCode).isEqualTo(FAILURE_ERROR);
    }
    
    @Test
    public void failWhenSourceDirIsNotDirectory(
        @Mock
        MessageDigestCreator anyDigestCreator)
    throws Exception
    {
        File badSourceDir = new File(tempDir, "bad-source-dir");
        badSourceDir.createNewFile();
        File anyWorkingDir = new File(tempDir, "any-working-dir");
        
        Throwable exception = catchThrowable(() -> new PackerBuildCommand(
            anyDigestCreator,
            badSourceDir,
            anyWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES));
        
        assertThat(exception)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Source path");
    }
    
    @Test
    public void failWhenWorkingDirIsNotDirectory(
        @Mock
        MessageDigestCreator anyDigestCreator)
    throws Exception
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File badWorkingDir = new File(tempDir, "bad-working-dir");
        badWorkingDir.createNewFile();
        
        Throwable exception = catchThrowable(() -> new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            badWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES));
        
        assertThat(exception)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Working path");
    }
    
    @Test
    public void failWhenUnableToRetrieveFileHashList(
        @Mock
        MessageDigestCreator anyDigestCreator,
        @Mock
        PackerCommandLogger anyLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File anyWorkingDir = new File(tempDir, "any-working-dir");
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            anyWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES,
            CHECKSUM_ALGORITHM,
            FAILING_BUFFERED_READER_BUILDER);
        
        Throwable exception = catchThrowable(() -> command.init(
            anyLogger,
            anyTimeoutHandler));
        
        assertThat(exception)
            .isInstanceOf(PackerCommandException.class)
            .hasMessage("Unable to retrieve file hash list");
    }
    
    @Test
    public void failWhenUnableToCreateChecksumFile(
        @Mock
        MessageDigestCreator anyDigestCreator,
        @Mock
        PackerCommandLogger anyLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File anySourceDir = new File(tempDir, "any-source-dir");
        anySourceDir.mkdir();
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        someWorkingDir.setWritable(false);
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            anySourceDir,
            someWorkingDir,
            ANY_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        Throwable exception = catchThrowable(() -> command.init(
            anyLogger,
            anyTimeoutHandler));
        
        assertThat(exception)
            .isInstanceOf(PackerCommandException.class)
            .hasMessageContaining("Could not create chechsum file");
    }
    
    @Test
    public void failWhenUnableToUpdateChecksumFile(
        @Mock
        MessageDigestCreator anyDigestCreator,
        @Mock
        PackerCommandLogger anyLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File someSourceDir = new File(tempDir, "some-source-dir");
        someSourceDir.mkdir();
        File someIncludedSourceFile = new File(
            someSourceDir,
            SOME_INCLUDED_SOURCE_FILE_PATH);
        someIncludedSourceFile.createNewFile();
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        File checksumFile = new File(someWorkingDir, CHECKSUM_FILE_NAME);
        checksumFile.createNewFile();
        someWorkingDir.setWritable(false);
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            someSourceDir,
            someWorkingDir,
            SOME_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        Throwable exception = catchThrowable(() -> command.init(
            anyLogger,
            anyTimeoutHandler));
        
        assertThat(exception)
            .isInstanceOf(PackerCommandException.class)
            .hasMessageContaining("Could not update file ");
    }
    
    @Test
    public void failWhenUnableToWriteToChecksumFile(
        @Mock
        MessageDigestCreator anyDigestCreator,
        @Mock
        PackerCommandLogger anyLogger,
        @Mock
        TimeoutHandler anyTimeoutHandler)
    throws Exception
    {
        File someSourceDir = new File(tempDir, "some-source-dir");
        someSourceDir.mkdir();
        File someIncludedSourceFile = new File(
            someSourceDir,
            SOME_INCLUDED_SOURCE_FILE_PATH);
        someIncludedSourceFile.createNewFile();
        File someWorkingDir = new File(tempDir, "some-working-dir");
        someWorkingDir.mkdir();
        File checksumFile = new File(someWorkingDir, CHECKSUM_FILE_NAME);
        checksumFile.createNewFile();
        checksumFile.setWritable(false);
        PackerCommand command = new PackerBuildCommand(
            anyDigestCreator,
            someSourceDir,
            someWorkingDir,
            SOME_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES);
        
        Throwable exception = catchThrowable(() -> command.init(
            anyLogger,
            anyTimeoutHandler));
        
        assertThat(exception)
            .isInstanceOf(PackerCommandException.class)
            .hasMessage("Could not updates files");
    }
    
    @Test
    public void failWhenBadChecksumAlgorithmIsUsed(
        @Mock
        MessageDigestCreator anyDigestCreator)
    throws Exception
    {
        File someSourceDir = new File(tempDir, "some-source-dir");
        someSourceDir.mkdir();
        File anyWorkingDir = new File(tempDir, "any-working-dir");
        
        Throwable exception = catchThrowable(() -> new PackerBuildCommand(
            anyDigestCreator,
            someSourceDir,
            anyWorkingDir,
            SOME_SOURCE_FILE_PATH_SET,
            ANY_CHANGES_NEEDED,
            ANY_INVALIDATE_ON_FAILURE,
            ANY_TEMPLATE_PATH,
            ANY_FORCE,
            ANY_ONLY,
            ANY_EXCEPT,
            ANY_VARS,
            ANY_VAR_FILES,
            BAD_CHECKSUM_ALGORITHM));
        
        assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not calculate hash for of file");
    }
}
