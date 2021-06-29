package sorald.processor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static sorald.Assertions.assertCompiles;
import static sorald.Assertions.assertNoRuleViolations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;
import sorald.Constants;
import sorald.FileUtils;
import sorald.TestHelper;
import sorald.event.StatsMetadataKeys;
import sorald.rule.Rule;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;

public class ProcessorTest {

    private static final Set<String> FILES_THAT_DONT_COMPILE_AFTER_REPAIR =
            Set.of("MathOnFloat.java", "CastArithmeticOperand.java");

    /**
     * Parameterized test that processes a single Java file at a time with a single processor.
     *
     * <p>To add a new test for a rule with existing tests, add a new file to an existing test
     * directory in {@link ProcessorTestHelper#TEST_FILES_ROOT}. To add a new test for a rule
     * without existing tests, add a new directory+Java file in {@link
     * ProcessorTestHelper#TEST_FILES_ROOT} as described in the docs for {@link
     * ProcessorTestHelper#toProcessorTestCase(File)}.
     */
    @ParameterizedTest
    @ArgumentsSource(NonCompliantJavaFileProvider.class)
    public void testProcessSingleFile(ProcessorTestHelper.ProcessorTestCase testCase)
            throws Exception {
        Path statsOutputFile = testCase.nonCompliantFile.toPath().resolveSibling("stats.json");

        ProcessorTestHelper.runSorald(
                testCase, Constants.ARG_STATS_OUTPUT_FILE, statsOutputFile.toString());

        String pathToRepairedFile = testCase.repairedFilePath().toString();
        TestHelper.removeComplianceComments(pathToRepairedFile);
        assertNoRuleViolations(testCase.repairedFilePath().toFile(), testCase.getRule());
        assertNoCrashReport(statsOutputFile);
    }

    /**
     * Provider class that provides test cases based on the buggy/non-compliant Java source files in
     * the test files directory.
     */
    private static class NonCompliantJavaFileProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext)
                throws IOException {
            return ProcessorTestHelper.getTestCasesInTemporaryDirectory().map(Arguments::of);
        }
    }

    /**
     * Parameterized test that processes a single Java file at a time with a single processor, and
     * compares the output to a reference. It executes on a subset of the test files acted upon by
     * {@link ProcessorTest#testProcessSingleFile(ProcessorTestHelper.ProcessorTestCase, File)}.
     *
     * <p>If a input test file A.java has a sibling A.java.expected, then this test is executed with
     * A.java.expected as the expected output from processing A.java.
     */
    @ParameterizedTest
    @ArgumentsSource(NonCompliantJavaFileWithExpectedProvider.class)
    public void testProcessSingleFile(
            ProcessorTestHelper.ProcessorTestCase testCase, @TempDir File tempdir)
            throws Exception {
        // arrange

        // Spoon does not like parsing files that don't end in .java, so we must copy the .expected
        // files to end with .java
        Path expectedOutput = tempdir.toPath().resolve(testCase.nonCompliantFile.getName());
        Files.copy(
                testCase.expectedOutfile().orElseThrow(IllegalStateException::new).toPath(),
                expectedOutput);
        assertNoRuleViolations(expectedOutput.toFile(), testCase.getRule());

        // act
        ProcessorTestHelper.runSorald(testCase);

        // assert
        Path pathToRepairedFile = testCase.repairedFilePath();
        CtModel repairedModel = parseNoComments(pathToRepairedFile);
        CtModel expectedModel = parseNoComments(expectedOutput);

        List<CtType<?>> repairedTypes = getSortedTypes(repairedModel);
        List<CtType<?>> expectedTypes = getSortedTypes(expectedModel);
        List<CtImport> repairedImports = getSortedImports(repairedModel);
        List<CtImport> expectedImports = getSortedImports(expectedModel);

        assertEquals(expectedTypes, repairedTypes);
        assertEquals(expectedImports, repairedImports);
    }

    @ParameterizedTest
    @MethodSource("getCompilableProcessorTestCases")
    void sorald_producesCompilableOutput_whenInputIsCompilable(
            ProcessorTestHelper.ProcessorTestCase compilableTestCase) throws Exception {
        assumeFalse(
                FILES_THAT_DONT_COMPILE_AFTER_REPAIR.contains(
                        compilableTestCase.nonCompliantFile.getName()),
                "See https://github.com/SpoonLabs/sorald/issues/570");

        ProcessorTestHelper.runSorald(compilableTestCase);
        assertCompiles(compilableTestCase.repairedFilePath().toFile());
    }

    private static Stream<Arguments> getCompilableProcessorTestCases() throws IOException {
        return ProcessorTestHelper.getTestCasesInTemporaryDirectory()
                .filter(
                        tc ->
                                ProcessorTestHelper.isStandaloneCompilableTestFile(
                                        tc.nonCompliantFile))
                .map(Arguments::of);
    }

    /**
     * Parameterized test that processes a single Java file at a time with a single processor, and
     * asserts that literal exact matches are contained in the output.
     */
    @ParameterizedTest
    @ArgumentsSource(NonCompliantJavaFileWithExactMatchesProvider.class)
    public void sorald_shouldProduceOutput_containingExactMatch(
            ProcessorTestHelper.ProcessorTestCase testCase) throws Exception {
        assertThat(testCase.getExpectedExactMatches(), is(not(empty())));

        // act
        ProcessorTestHelper.runSorald(testCase);

        // assert
        String output = Files.readString(testCase.repairedFilePath());
        assertThat(output, stringContainsInOrder(testCase.getExpectedExactMatches()));
    }

    /**
     * As described in https://github.com/SpoonLabs/sorald/issues/204, Sorald would crash in the
     * presence of directories ending in `.java`
     */
    @Test
    public void sorald_canProcessProject_whenDirectoryHasJavaFileExtension() throws Exception {
        // arrange
        Path workdir = TestHelper.createTemporaryProcessorTestFilesWorkspace();
        File origDir = workdir.resolve("S2116_ArrayHashCodeAndToString").toFile();
        File dirWithJavaExtension =
                origDir.toPath().resolveSibling(origDir.getName() + Constants.JAVA_EXT).toFile();
        org.apache.commons.io.FileUtils.moveDirectory(origDir, dirWithJavaExtension);
        Rule rule = Rule.of(new ArrayHashCodeAndToStringProcessor().getRuleKey());

        // act
        ProcessorTestHelper.runSorald(workdir.toFile(), rule);

        // assert
        assertNoRuleViolations(
                dirWithJavaExtension.toPath().resolve("ArrayHashCodeAndToString.java").toFile(),
                Rule.of(new ArrayHashCodeAndToStringProcessor().getRuleKey()));
    }

    @Test
    public void sorald_doesNotIndentNewElementsWithTabs_whenSourceCodeUsesSpaces()
            throws Exception {
        // arrange
        // rule 2755 always adds new elements, among other things a method
        ProcessorTestHelper.ProcessorTestCase testCase =
                ProcessorTestHelper.getTestCasesInTemporaryDirectory()
                        .filter(tc -> tc.ruleKey.equals(new XxeProcessingProcessor().getRuleKey()))
                        .findFirst()
                        .get();

        // act
        ProcessorTestHelper.runSorald(testCase);

        // assert
        String output = Files.readString(testCase.repairedFilePath());
        assertThat(output, not(containsString("\t")));
    }

    @Test
    public void sorald_canProcessProject_withModuleInfo() throws Exception {
        // arrange
        Path workdir = TestHelper.createTemporaryTestResourceWorkspace();
        Path scenarioRoot = workdir.resolve("scenario_test_files").resolve("project.with.module");
        Rule rule = Rule.of(new DeadStoreProcessor().getRuleKey());

        // act
        ProcessorTestHelper.runSorald(scenarioRoot.toFile(), rule);

        // assert
        Path sourceFile =
                scenarioRoot
                        .resolve("some")
                        .resolve("pkg")
                        .resolve("ClassInNamedModuleWithDeadStores.java");

        assertNoRuleViolations(sourceFile.toFile(), rule);
    }

    /**
     * Provider class that provides test cases based on the buggy/non-compliant Java source files in
     * the test files directory, that also have an expected outcome for the bugfix. The expected
     * files have the same name as their corresponding buggy files, but with the suffix ".expected".
     */
    private static class NonCompliantJavaFileWithExpectedProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext)
                throws IOException {
            return ProcessorTestHelper.getTestCasesInTemporaryDirectory()
                    .filter(testCase -> testCase.expectedOutfile().isPresent())
                    .map(Arguments::of);
        }
    }

    /** Provider class that provides test cases that with exact output matches. */
    private static class NonCompliantJavaFileWithExactMatchesProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context)
                throws Exception {
            return ProcessorTestHelper.getTestCasesInTemporaryDirectory()
                    .filter(testCase -> testCase.getExpectedExactMatches().size() > 0)
                    .map(Arguments::of);
        }
    }

    private static CtModel parseNoComments(Path javaFile) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(javaFile.toString());
        return launcher.buildModel();
    }

    private static List<CtImport> getSortedImports(CtModel model) {
        return model.getAllTypes().stream()
                .flatMap(
                        type ->
                                type
                                        .getFactory()
                                        .CompilationUnit()
                                        .getOrCreate(type)
                                        .getImports()
                                        .stream())
                .sorted(Comparator.comparing(CtImport::prettyprint))
                .collect(Collectors.toList());
    }

    private static List<CtType<?>> getSortedTypes(CtModel model) {
        return model.getAllTypes().stream()
                .sorted(Comparator.comparing(CtType::getQualifiedName))
                .collect(Collectors.toList());
    }

    /** Assert that the statistics output does not contain a crash report. */
    private static void assertNoCrashReport(Path statsOutputFile) throws IOException {
        JSONObject jo = FileUtils.readJSON(statsOutputFile);
        JSONArray ja = jo.getJSONArray(StatsMetadataKeys.CRASHES);
        assertThat(ja.toList(), is(empty()));
    }
}
