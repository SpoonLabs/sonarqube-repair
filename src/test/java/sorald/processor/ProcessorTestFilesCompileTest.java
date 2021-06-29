package sorald.processor;

import static sorald.Assertions.assertCompiles;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Meta tests for verifying that the processor test files compile as expected. */
class ProcessorTestFilesCompileTest {

    @ParameterizedTest
    @MethodSource("provideCompilableProcessorTestInputFile")
    void processorTestCaseInputFile_notMarkedNOCOMPILE_shouldCompile(File testCaseJavaFile) {
        assertCompiles(testCaseJavaFile);
    }

    private static Stream<Arguments> provideCompilableProcessorTestInputFile() throws IOException {
        return getCompilableProcessorTestCases().map(tc -> tc.nonCompliantFile).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("provideCompilableProcessorTestExpectedFiles")
    void processorTestCaseExpectedFile_notMarkedNOCOMPILE_shouldCompile(
            File testCaseExpectedJavaFile, @TempDir File tempDir) throws IOException {
        File javaFile = copyFileToDirWithoutExpectedExtension(testCaseExpectedJavaFile, tempDir);
        assertCompiles(javaFile);
    }

    private static Stream<Arguments> provideCompilableProcessorTestExpectedFiles()
            throws IOException {
        return getCompilableProcessorTestCases()
                .flatMap(tc -> tc.expectedOutfile().stream())
                .map(Arguments::of);
    }

    private static File copyFileToDirWithoutExpectedExtension(File expectedJavaFile, File dir)
            throws IOException {
        String fileNameWithExpectedExtension = expectedJavaFile.getName();
        String validJavaFileName =
                fileNameWithExpectedExtension.substring(
                        0, fileNameWithExpectedExtension.lastIndexOf("."));
        File validJavaFile = dir.toPath().resolve(validJavaFileName).toFile();
        FileUtils.copyFile(expectedJavaFile, validJavaFile);
        return validJavaFile;
    }

    private static Stream<ProcessorTestHelper.ProcessorTestCase> getCompilableProcessorTestCases()
            throws IOException {
        return ProcessorTestHelper.getTestCasesInTemporaryDirectory()
                .filter(
                        tc ->
                                ProcessorTestHelper.isStandaloneCompilableTestFile(
                                        tc.nonCompliantFile));
    }
}
