package sorald;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import sorald.event.StatsMetadataKeys;
import sorald.processor.ProcessorTestHelper;
import sorald.processor.XxeProcessingProcessor;
import sorald.rule.Rule;
import sorald.rule.RuleViolation;
import sorald.sonar.ProjectScanner;
import sorald.sonar.SonarRule;

public class GatherStatsTest {

    @ParameterizedTest
    @EnumSource(value = RepairStrategy.class)
    public void statisticsFile_containsExpectedStats(
            RepairStrategy repairStrategy, @TempDir File tempDir) throws Exception {
        ProcessorTestHelper.ProcessorTestCase testCase =
                ProcessorTestHelper.getTestCasesInTemporaryDirectory()
                        .filter(tc -> tc.ruleKey.equals(new XxeProcessingProcessor().getRuleKey()))
                        .findFirst()
                        .get();

        org.apache.commons.io.FileUtils.copyFile(
                testCase.nonCompliantFile,
                tempDir.toPath().resolve(testCase.nonCompliantFile.getName()).toFile());

        if (repairStrategy == RepairStrategy.MAVEN) {
            MavenHelper.convertToMavenProject(tempDir);
        }

        File statsFile = tempDir.toPath().resolve("stats.json").toFile();

        ProcessorTestHelper.runSorald(
                tempDir,
                testCase.getRule(),
                Constants.ARG_STATS_OUTPUT_FILE,
                statsFile.getAbsolutePath(),
                Constants.ARG_REPAIR_STRATEGY,
                repairStrategy.name());

        JSONObject jo = FileUtils.readJSON(statsFile.toPath());
        JSONArray repairs = jo.getJSONArray(StatsMetadataKeys.REPAIRS);
        assertThat(repairs.length(), greaterThan(0));

        for (int i = 0; i < repairs.length(); i++) {
            assertThat(
                    repairs.getJSONObject(i).keySet(),
                    containsInAnyOrder(
                            StatsMetadataKeys.REPAIR_RULE_KEY,
                            StatsMetadataKeys.REPAIR_RULE_NAME,
                            StatsMetadataKeys.REPAIR_CRASHED_LOCATIONS,
                            StatsMetadataKeys.REPAIR_PERFORMED_LOCATIONS,
                            StatsMetadataKeys.REPAIR_NB_PERFORMED,
                            StatsMetadataKeys.REPAIR_NB_FAILURES,
                            StatsMetadataKeys.REPAIR_NB_VIOLATIONS_BEFORE,
                            StatsMetadataKeys.REPAIR_NB_VIOLATIONS_AFTER));
        }

        assertThat(
                jo.getJSONObject(StatsMetadataKeys.EXECUTION_INFO)
                        .getJSONArray(StatsMetadataKeys.ORIGINAL_ARGS)
                        .length(),
                greaterThan(0));

        assertThat(jo.getLong(StatsMetadataKeys.PARSE_TIME_MS), greaterThan(0L));
        assertThat(jo.getLong(StatsMetadataKeys.REPAIR_TIME_MS), greaterThan(0L));
        assertThat(jo.getLong(StatsMetadataKeys.START_TIME_MS), greaterThan(0L));
        assertThat(jo.getLong(StatsMetadataKeys.END_TIME_MS), greaterThan(0L));
        assertThat(jo.getLong(StatsMetadataKeys.TOTAL_TIME_MS), greaterThan(0L));
    }

    /** Check that the amount of violations is correct when using targeted repair. */
    @ParameterizedTest
    @EnumSource(value = RepairStrategy.class)
    public void statisticsFile_containsCorrectNbViolationsBeforeAndAfter_whenUsingTargetedRepair(
            RepairStrategy repairStrategy, @TempDir File tmpDir) throws IOException {
        // arrange/act
        TargetedRepairInfo targetedRepairInfo = performTargetedRepair(tmpDir, repairStrategy);

        // assert
        assertThat(
                "Targeted repair did not do its job...",
                targetedRepairInfo.violationsAfter.size(),
                equalTo(targetedRepairInfo.violationsBefore.size() - 1));

        JSONObject jo = FileUtils.readJSON(targetedRepairInfo.statsFile);

        JSONArray repairStats = jo.getJSONArray(StatsMetadataKeys.REPAIRS);
        assertThat("unexpected amount of repair stats", repairStats.length(), equalTo(1));

        JSONObject xxeRepairStats = repairStats.getJSONObject(0);
        assertThat(
                xxeRepairStats.getInt(StatsMetadataKeys.REPAIR_NB_VIOLATIONS_BEFORE),
                equalTo(targetedRepairInfo.violationsBefore.size()));
        assertThat(
                xxeRepairStats.getInt(StatsMetadataKeys.REPAIR_NB_VIOLATIONS_AFTER),
                equalTo(targetedRepairInfo.violationsAfter.size()));
    }

    @ParameterizedTest
    @EnumSource(value = RepairStrategy.class)
    public void statisticsFile_containsCorrectSpecifierForPerformedRepair_whenUsingTargetedRepair(
            RepairStrategy repairStrategy, @TempDir File tmpDir) throws IOException {
        // arrange/act
        TargetedRepairInfo targetedRepairInfo = performTargetedRepair(tmpDir, repairStrategy);

        // assert
        String specifier =
                targetedRepairInfo.targetViolation.relativeSpecifier(
                        targetedRepairInfo.projectPath);
        JSONObject xxeRepairStats =
                FileUtils.readJSON(targetedRepairInfo.statsFile)
                        .getJSONArray(StatsMetadataKeys.REPAIRS)
                        .getJSONObject(0);

        assertThat(
                xxeRepairStats
                        .getJSONArray(StatsMetadataKeys.REPAIR_PERFORMED_LOCATIONS)
                        .getJSONObject(0)
                        .getString(StatsMetadataKeys.VIOLATION_SPECIFIER),
                equalTo(specifier));
    }

    private static TargetedRepairInfo performTargetedRepair(
            File tmpDir, RepairStrategy repairStrategy) throws IOException {
        File statsFile = tmpDir.toPath().resolve("stats.json").toFile();
        File processorTestFiles = ProcessorTestHelper.TEST_FILES_ROOT.toFile();
        File project = tmpDir.toPath().resolve("project").toFile();
        org.apache.commons.io.FileUtils.copyDirectory(processorTestFiles, project);

        if (repairStrategy == RepairStrategy.MAVEN) {
            MavenHelper.convertToMavenProject(project);
        }

        Rule targetRule = new SonarRule(new XxeProcessingProcessor().getRuleKey());

        Set<RuleViolation> violationsBefore =
                ProjectScanner.scanProject(project, project, targetRule);
        RuleViolation targetViolation =
                new ArrayList<>(violationsBefore).get(violationsBefore.size() / 2);
        String specifier = targetViolation.relativeSpecifier(project.toPath());

        // act
        Main.main(
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    project.getAbsolutePath(),
                    Constants.ARG_STATS_OUTPUT_FILE,
                    statsFile.getAbsolutePath(),
                    Constants.ARG_RULE_VIOLATION_SPECIFIERS,
                    specifier,
                    Constants.ARG_REPAIR_STRATEGY,
                    repairStrategy.name()
                });

        Set<RuleViolation> violationsAfter =
                ProjectScanner.scanProject(project, project, targetRule);

        return new TargetedRepairInfo(
                project.toPath(),
                statsFile.toPath(),
                violationsBefore,
                violationsAfter,
                targetViolation,
                targetRule);
    }

    /** A simple container for test statistics of a targeted repair. */
    private static class TargetedRepairInfo {
        final Path projectPath;
        final Path statsFile;
        final Set<RuleViolation> violationsBefore;
        final Set<RuleViolation> violationsAfter;
        final RuleViolation targetViolation;
        final Rule targetRule;

        private TargetedRepairInfo(
                Path projectPath,
                Path statsFile,
                Set<RuleViolation> violationsBefore,
                Set<RuleViolation> violationsAfter,
                RuleViolation targetViolation,
                Rule targetRule) {
            this.projectPath = projectPath;
            this.statsFile = statsFile;
            this.violationsBefore = violationsBefore;
            this.violationsAfter = violationsAfter;
            this.targetViolation = targetViolation;
            this.targetRule = targetRule;
        }
    }
}
