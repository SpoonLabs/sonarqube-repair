package sorald.sonar;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.sonar.java.checks.CompareStringsBoxedTypesWithEqualsCheck;
import org.sonar.java.checks.DeadStoreCheck;
import org.sonar.java.checks.InputStreamReadCheck;
import org.sonar.java.checks.NullShouldNotBeUsedWithOptionalCheck;
import org.sonar.java.checks.serialization.SerializableFieldInSerializableClassCheck;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonarsource.analyzer.commons.annotations.DeprecatedRuleKey;
import sorald.rule.RuleType;

class ChecksTest {

    @Test
    void test_getChecksByType_whenTypeIsBug_containsBugChecks() {
        final List<Class<? extends JavaFileScanner>> bugChecks =
                Checks.getChecksByType(RuleType.BUG);
        final List<Class<? extends JavaFileScanner>> expectedBugChecksSubset =
                Arrays.asList(
                        CompareStringsBoxedTypesWithEqualsCheck.class,
                        InputStreamReadCheck.class,
                        NullShouldNotBeUsedWithOptionalCheck.class);

        assertTrue(bugChecks.containsAll(expectedBugChecksSubset));
    }

    @Test
    void test_getChecksByType_whenTypeIsCodeSmell_containsCodeSmellChecks() {
        final List<Class<? extends JavaFileScanner>> codeSmellChecks =
                Checks.getChecksByType(RuleType.CODE_SMELL);
        final List<Class<? extends JavaFileScanner>> expectedCodeSmellChecksSubset =
                Arrays.asList(
                        DeadStoreCheck.class, SerializableFieldInSerializableClassCheck.class);

        assertTrue(codeSmellChecks.containsAll(expectedCodeSmellChecksSubset));
    }

    @Test
    void test_getCheck_whenKeyDoesNotExist_throws() {
        assertThrows(IllegalArgumentException.class, () -> Checks.getCheck("12345678"));
    }

    @Test
    @SuppressWarnings("UnstableApiUsage")
    void test_getRuleKey_whenCheckHasNoKey_throws() {
        JavaFileScanner scannerWithoutKey =
                j -> {
                    return;
                };

        assertThrows(
                IllegalArgumentException.class,
                () -> Checks.getRuleKey(scannerWithoutKey.getClass()));
    }

    @Test
    void getAllChecks_containsNoDeprecatedChecks() {
        List<Class<? extends JavaFileScanner>> deprecatedChecks =
                Checks.getAllChecks().stream()
                        .filter(
                                check ->
                                        check.getAnnotationsByType(DeprecatedRuleKey.class).length
                                                > 0)
                        .collect(Collectors.toList());

        assertThat(deprecatedChecks, empty());
    }
}
