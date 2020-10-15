package sorald.processor;

import org.sonar.check.Rule;
import org.sonar.plugins.java.api.JavaFileScanner;
import sorald.Constants;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper functions for {@link ProcessorTest}.
 */
public class ProcessorTestHelper {
    static final Path TEST_FILES_ROOT = Paths.get(Constants.PATH_TO_RESOURCES_FOLDER).resolve("processor_test_files");

    /**
     *  Create a {@link ProcessorTestCase} from a non-compliant Java source file.
     *
     *  For this to work out, the directory that the Java file file is located in must carry the same name as a sonar
     *  check class, minus the "Check" suffix. For example, if the test file is for the rule related to
     *  {@link org.sonar.java.checks.MathOnFloatCheck}, the directory must be called "MathOnFloat". The test
     *  file itself can be called anything. Here's an example of a compliant directory structure, where the Java
     *  files are test files for {@link org.sonar.java.checks.MathOnFloatCheck}.
     *
     *      MathOnFloat
     *             |
     *             ---- TestCaseFile.java
     *             |
     *             ---- OtherTestCaseFile.java
     */
    static ProcessorTestCase toProcessorTestCase(File nonCompliantFile) {
        File directory = nonCompliantFile.getParentFile();
        assert directory.isDirectory();
        String ruleName = directory.getName();
        Class<JavaFileScanner> checkClass = loadCheckClass(ruleName);
        String outfileDirRelpath = parseSourceFilePackage(nonCompliantFile.toPath()).replace(".", File.separator);
        Path outfileRelpath = Paths.get(outfileDirRelpath).resolve(nonCompliantFile.getName());
        return new ProcessorTestCase(ruleName, getRuleKey(checkClass), nonCompliantFile, checkClass, outfileRelpath);
    }

    /**
     * Parse the package for a single Java source file. If there is no package statement in the file, or the file
     * cannot be read for any reason, an empty string is returned instead.
     */
    private static String parseSourceFilePackage(Path sourceFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(sourceFile);
        } catch (IOException e) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\s*package\\s+?(\\S+)\\s*;");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private static Class<JavaFileScanner> loadCheckClass(String ruleName) {
        // FIXME This is a ridiculously insecure way to load the class. Should probably use a lookup table instead.
        String checkQualname = "org.sonar.java.checks." + ruleName + "Check";
        try {
            return (Class<JavaFileScanner>) Class.forName(checkQualname);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(checkQualname + " is not a valid class");
        }
    }

    /**
     * Retrieve the numeric identifier of the rule related to the given check class. Non-digits are stripped, so
     * e.g. S1234 becomes 1234.
     */
    private static String getRuleKey(Class<JavaFileScanner> checkClass) {
        return Arrays.stream(checkClass.getAnnotationsByType(Rule.class))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(checkClass.getName() + " does not have a key"))
                .key()
                .replaceAll("[^\\d]+", "");
    }

    /**
     * A wrapper class to hold the information required to execute a test case for a single file and rule with the
     * associated processor.
     */
    static class ProcessorTestCase {
        public final String ruleName;
        public final String ruleKey;
        public final File nonCompliantFile;
        public final Class<JavaFileScanner> checkClass;
        public final Path outfileRelpath;

        ProcessorTestCase(
                String ruleName,
                String ruleKey,
                File nonCompliantFile,
                Class<JavaFileScanner> checkClass,
                Path outfileRelpath
        ) {
            this.ruleName = ruleName;
            this.ruleKey = ruleKey;
            this.nonCompliantFile = nonCompliantFile;
            this.checkClass = checkClass;
            this.outfileRelpath = outfileRelpath;
        }

        @Override
        public String toString() {
            return "ruleKey=" + ruleKey +
                    " ruleName=" + ruleName +
                    " source=" + TEST_FILES_ROOT.relativize(nonCompliantFile.toPath());
        }

        public JavaFileScanner createCheckInstance() throws
                NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
            return checkClass.getConstructor().newInstance();
        }
    }
}