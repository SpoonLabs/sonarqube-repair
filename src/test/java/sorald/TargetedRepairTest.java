package sorald;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static sorald.Assertions.assertNoRuleViolations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sorald.processor.BigDecimalDoubleConstructorProcessor;
import sorald.processor.ProcessorTestHelper;
import sorald.rule.Rule;
import sorald.rule.RuleViolation;
import sorald.sonar.ProjectScanner;
import sorald.sonar.SonarRule;

/** Tests for the targeted repair functionality of Sorald. */
public class TargetedRepairTest {

    @Test
    void targetedRepair_correctlyRepairsSingleViolation() throws Exception {
        // arrange
        TargetedRepairWorkdirInfo workdirInfo = setupWorkdir();

        // act
        Main.main(
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    workdirInfo.workdir.getAbsolutePath(),
                    Constants.ARG_RULE_VIOLATION_SPECIFIERS,
                    workdirInfo.targetViolation.relativeSpecifier(workdirInfo.workdir.toPath())
                });

        // assert
        Set<RuleViolation> violationsAfter =
                ProjectScanner.scanProject(
                        workdirInfo.targetFile, workdirInfo.targetFile, workdirInfo.rule);

        assertThat(violationsAfter.size(), equalTo(workdirInfo.numViolationsBefore - 1));
        assertFalse(violationsAfter.contains(workdirInfo.targetViolation));
    }

    /** It should not be possible to specify both rule keys and specific rule violations. */
    @Test
    public void targetedRepair_cannotBeUsedWithRuleKeys(@TempDir File workdir) throws Exception {
        // arrange
        TargetedRepairWorkdirInfo workdirInfo = setupWorkdir();
        var args =
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    workdir.getAbsolutePath(),
                    Constants.ARG_RULE_VIOLATION_SPECIFIERS,
                    workdirInfo.targetViolation.relativeSpecifier(workdir.toPath()),
                    Constants.ARG_RULE_KEY,
                    "2755"
                };

        // act/assert
        assertThrows(SystemExitHandler.NonZeroExit.class, () -> Main.main(args));
    }

    @Test
    public void targetedRepair_requiresViolationPath_existsInOriginalFilesPath(
            @TempDir File workdir) throws Exception {
        // arrange
        TargetedRepairWorkdirInfo workdirInfo = setupWorkdir();

        // make the violation ID relative to some other directory
        String badViolationId =
                workdirInfo.targetViolation.relativeSpecifier(workdir.getParentFile().toPath());
        var args =
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    workdir.getAbsolutePath(),
                    Constants.ARG_RULE_VIOLATION_SPECIFIERS,
                    badViolationId
                };

        // act/assert
        assertThrows(SystemExitHandler.NonZeroExit.class, () -> Main.main(args));
    }

    @Test
    public void targetedRepair_acceptsAbsoluteViolationPath() throws Exception {
        // arrange
        TargetedRepairWorkdirInfo workdirInfo = setupWorkdir();

        // act
        Path rootDir = workdirInfo.workdir.toPath().getRoot();
        String absoluteViolationId =
                workdirInfo
                        .targetViolation
                        .relativeSpecifier(rootDir)
                        .replaceFirst(
                                Constants.VIOLATION_SPECIFIER_SEP, File.pathSeparator + rootDir);
        Main.main(
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    workdirInfo.workdir.toString(),
                    Constants.ARG_RULE_VIOLATION_SPECIFIERS,
                    absoluteViolationId
                });

        // assert
        Set<RuleViolation> violationsAfter =
                ProjectScanner.scanProject(
                        workdirInfo.workdir, workdirInfo.workdir, workdirInfo.rule);
        assertThat(violationsAfter.size(), equalTo(workdirInfo.numViolationsBefore - 1));
    }

    @Test
    void targetedRepair_requiresViolationSpecs_pointToExistingViolations() throws Exception {
        // arrange
        TargetedRepairWorkdirInfo workdirInfo = setupWorkdir();
        // run Sorald to remove the violation
        ProcessorTestHelper.runSorald(workdirInfo.workdir, workdirInfo.rule);
        assertNoRuleViolations(
                workdirInfo.targetViolation.getAbsolutePath().toFile(), workdirInfo.rule);
        String violationSpec =
                workdirInfo.targetViolation.relativeSpecifier(workdirInfo.workdir.toPath());

        String[] args = {
            Constants.REPAIR_COMMAND_NAME,
            Constants.ARG_SOURCE,
            workdirInfo.workdir.toString(),
            Constants.ARG_RULE_VIOLATION_SPECIFIERS,
            violationSpec
        };

        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));

        // act/assert
        assertThrows(SystemExitHandler.NonZeroExit.class, () -> Main.main(args));
        assertThat(
                err.toString(),
                allOf(
                        containsString("No actual violation matching violation spec:"),
                        containsString(violationSpec)));
    }

    /** Test that targeted repair works when --source points to a file rather than a directory. */
    @Test
    void targetedRepair_canTargetSpecificFile() throws Exception {
        // arrange
        TargetedRepairWorkdirInfo workdirInfo = setupWorkdir();
        Path target = workdirInfo.targetViolation.getAbsolutePath();
        String violationSpec = workdirInfo.targetViolation.relativeSpecifier(target);

        // act
        Main.main(
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    target.toString(),
                    Constants.ARG_RULE_VIOLATION_SPECIFIERS,
                    violationSpec
                });

        // assert
        Set<RuleViolation> violationsAfter =
                ProjectScanner.scanProject(
                        workdirInfo.workdir, workdirInfo.workdir, workdirInfo.rule);
        assertThat(violationsAfter.size(), equalTo(workdirInfo.numViolationsBefore - 1));
    }

    /**
     * Test that targeted repair works with legacy identifiers lacking the S prefix of Sonar rules
     */
    @Test
    void targetedRepair_canUseSpecifiers_withRuleKeysLackingSPrefix() throws IOException {
        // arrange
        TargetedRepairWorkdirInfo workdirInfo = setupWorkdir();
        String legacyViolationSpec =
                workdirInfo
                        .targetViolation
                        .relativeSpecifier(workdirInfo.workdir.toPath())
                        .substring(1);

        // act
        Main.main(
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    workdirInfo.workdir.getAbsolutePath(),
                    Constants.ARG_RULE_VIOLATION_SPECIFIERS,
                    legacyViolationSpec
                });

        // assert
        Set<RuleViolation> violationsAfter =
                ProjectScanner.scanProject(
                        workdirInfo.workdir, workdirInfo.workdir, workdirInfo.rule);
        assertThat(violationsAfter.size(), equalTo(workdirInfo.numViolationsBefore - 1));
    }

    /** Setup the workdir with a specific target violation. */
    private static TargetedRepairWorkdirInfo setupWorkdir() throws IOException {
        Path workdir = TestHelper.createTemporaryProcessorTestFilesWorkspace();

        Rule rule = new SonarRule(new BigDecimalDoubleConstructorProcessor().getRuleKey());
        File targetFile =
                workdir.resolve("S2111_BigDecimalDoubleConstructor")
                        .resolve("BigDecimalDoubleConstructor.java")
                        .toFile();

        List<RuleViolation> violationsBefore =
                new ArrayList<>(ProjectScanner.scanProject(targetFile, workdir.toFile(), rule));
        assertThat(
                "there must be more than 1 violation in the test file for an adequate test",
                violationsBefore.size(),
                greaterThan(1));
        violationsBefore.sort(RuleViolation::compareTo);
        RuleViolation violation = violationsBefore.get(violationsBefore.size() / 2);

        return new TargetedRepairWorkdirInfo(
                workdir.toFile(), rule, violationsBefore.size(), targetFile, violation);
    }

    /** Simple container for info about the targeted repair working directory. */
    private static class TargetedRepairWorkdirInfo {
        final File workdir;
        final Rule rule;
        final int numViolationsBefore;
        final File targetFile;
        final RuleViolation targetViolation;

        private TargetedRepairWorkdirInfo(
                File workdirPath,
                Rule rule,
                int numViolationsBefore,
                File targetFile,
                RuleViolation targetViolation) {
            this.workdir = workdirPath;
            this.rule = rule;
            this.numViolationsBefore = numViolationsBefore;
            this.targetFile = targetFile;
            this.targetViolation = targetViolation;
        }
    }
}
